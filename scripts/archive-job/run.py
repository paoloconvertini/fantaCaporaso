#!/usr/bin/env python3
"""Run the approved Drive check in an isolated preview, never in the main checkout."""
import argparse
import base64
import fcntl
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
from urllib.parse import urlparse
from datetime import datetime
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / '.local' / 'archive-job'
PAGES = ('classifiche', 'risultati', 'coppa-italia', 'statistiche')


def atomic_json(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    temporary.replace(path)


def validate_manifest(value, folder_ids):
    if not isinstance(value, dict) or value.get('version') != 1 or not isinstance(value.get('files'), list):
        raise ValueError('Manifest non valido')
    seen = set()
    for entry in value['files']:
        if not isinstance(entry, dict):
            raise ValueError('Voce manifest non valida')
        for key in ('id', 'title', 'mimeType', 'modifiedTime', 'folderId', 'url'):
            if not isinstance(entry.get(key), str) or not entry[key]:
                raise ValueError('Campo manifest mancante: ' + key)
        if entry['folderId'] not in folder_ids or entry['id'] in seen:
            raise ValueError('Cartella non autorizzata o ID duplicato')
        seen.add(entry['id'])
        modified = datetime.fromisoformat(entry['modifiedTime'].replace('Z', '+00:00'))
        if modified.tzinfo is None:
            raise ValueError('Data di modifica senza fuso orario')
        if not entry['url'].startswith(('https://drive.google.com/', 'https://docs.google.com/')):
            raise ValueError('URL manifest non appartenente a Google Drive')
    return value


def differences(previous, current):
    old = {item['id']: item for item in previous['files']}
    new = {item['id']: item for item in current['files']}
    keys = ('title', 'mimeType', 'modifiedTime', 'folderId')
    return {
        'new': sorted(new.keys() - old.keys()),
        'modified': sorted(key for key in old.keys() & new.keys()
                           if any(old[key][field] != new[key][field] for field in keys)),
        'removed': sorted(old.keys() - new.keys()),
    }


def materialize_legacy(event, workspace, maximum):
    """Bounded compatibility for connector artifacts unavailable over signed URLs."""
    item = event.get('item', {})
    if event.get('type') != 'item.completed' or item.get('type') != 'mcp_tool_call' or item.get('tool') != 'google_drive.fetch':
        return
    arguments = item.get('arguments', {})
    if arguments.get('include_base64') is not True or not arguments.get('download_raw_file'):
        return
    parts = urlparse(arguments.get('url', '')).path.split('/')
    if 'd' not in parts or parts.index('d') + 1 >= len(parts):
        return
    file_id = parts[parts.index('d') + 1]
    manifest = json.loads((workspace / 'candidate-manifest.json').read_text())
    file = next((f for f in manifest['files'] if f['id'] == file_id), None)
    if file is None:
        raise ValueError('Download non appartenente al manifest verificato')
    extensions = {'image/jpeg': '.jpg', 'image/png': '.png',
                  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': '.xlsx',
                  'application/vnd.ms-excel': '.xls'}
    extension = extensions.get(file['mimeType'])
    if extension is None or not all(c.isalnum() or c in '_-' for c in file_id):
        raise ValueError('Formato o ID non supportato per compatibilità limitata')

    def find_blob(value):
        if isinstance(value, dict):
            if isinstance(value.get('b64_string'), str) and value['b64_string']:
                return value['b64_string']
            for key, child in value.items():
                if key == 'text' and isinstance(child, str) and child.lstrip().startswith('{'):
                    try:
                        child = json.loads(child)
                    except ValueError:
                        continue
                result = find_blob(child)
                if result:
                    return result
        elif isinstance(value, list):
            for child in value:
                result = find_blob(child)
                if result:
                    return result
        return None

    blob = find_blob(item.get('result', {}))
    if not blob:
        return
    if len(blob) > ((maximum + 2) // 3) * 4:
        raise ValueError('File oltre il limite della compatibilità inline')
    content = base64.b64decode(blob, validate=True)
    if len(content) > maximum:
        raise ValueError('File oltre il limite della compatibilità inline')
    target = workspace / 'sources' / (file_id + extension)
    temporary = target.with_suffix(extension + '.tmp')
    temporary.write_bytes(content)
    temporary.replace(target)


def watch_events(path, workspace, maximum, stop, errors):
    with path.open() as stream:
        buffered = ''
        while True:
            buffered += stream.read()
            while '\n' in buffered:
                line, buffered = buffered.split('\n', 1)
                if not line.strip():
                    continue
                try:
                    materialize_legacy(json.loads(line), workspace, maximum)
                except Exception as error:
                    errors.append(str(error))
            if stop.wait(0.2):
                buffered += stream.read()
                for line in buffered.splitlines():
                    if line.strip():
                        try:
                            materialize_legacy(json.loads(line), workspace, maximum)
                        except Exception as error:
                            errors.append(str(error))
                return


def validate_preview(workspace):
    landing = workspace / 'landing-page'
    for path in workspace.rglob('*'):
        if path.is_symlink():
            raise ValueError('Link simbolico inatteso nell’anteprima: ' + str(path))
    for path in landing.rglob('*'):
        if path.suffix.lower() in ('.xlsx', '.xls', '.xlsm'):
            raise ValueError('Workbook sorgente inatteso tra i file pubblici')
        if path.is_file() and path.suffix.lower() in ('.html', '.js', '.json', '.css'):
            text = path.read_text().lower()
            if 'oaiusercontent.com' in text or 'skoid=' in text:
                raise ValueError('Riferimento temporaneo di download nei file pubblici')
    for page in PAGES:
        path = landing / page / 'index.html'
        if not path.is_file() or not path.stat().st_size:
            raise ValueError('Pagina mancante: ' + page)
    home = (landing / 'index.html').read_text()
    for page in PAGES:
        if '/' + page + '/' not in home:
            raise ValueError('Link home mancante: ' + page)


def save_review(workspace, record):
    report = workspace / 'report.md'
    if report.is_file():
        record['report'] = str(report)
    try:
        validate_preview(workspace)
    except (ValueError, OSError) as error:
        record['previewValidation'] = str(error)
        return
    pointer = STATE / 'review-preview.tmp'
    if pointer.is_symlink():
        pointer.unlink()
    pointer.symlink_to(workspace / 'landing-page', target_is_directory=True)
    pointer.replace(STATE / 'review-preview')
    record['reviewPreview'] = str(STATE / 'review-preview')


def run(check_only):
    os.umask(0o077)
    STATE.mkdir(parents=True, exist_ok=True)
    lock = (STATE / 'run.lock').open('a')
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        lock.close()
        print('Un controllo archivio è già in esecuzione; nessuna esecuzione sovrapposta.')
        return 0
    config = json.loads((ROOT / 'config/archive-job.json').read_text())
    zone = ZoneInfo(config['timezone'])
    now = datetime.now(zone)
    run_id = now.strftime('%Y%m%d-%H%M%S-%f')
    run_dir = STATE / 'runs' / run_id
    run_dir.mkdir(parents=True)
    mode = 'check' if check_only else 'prepare'
    record = {'id': run_id, 'startedAt': now.isoformat(), 'mode': mode, 'status': 'running',
              'runDirectory': str(run_dir)}
    atomic_json(STATE / 'last-run.json', record)
    atomic_json(run_dir / 'status.json', record)
    workspace = Path(tempfile.mkdtemp(prefix='fantacaporaso-archive-'))
    process = None
    try:
        codex = os.environ.get('ARCHIVE_CODEX_BIN') or shutil.which('codex')
        if not codex:
            raise RuntimeError('Codex CLI non trovato nel PATH')
        previous_path = STATE / 'manifest.json'
        previous = json.loads(previous_path.read_text()) if previous_path.exists() else {'version': 1, 'files': []}
        folder_ids = {item['id'] for item in config['folders']}
        validate_manifest(previous, folder_ids)
        atomic_json(workspace / 'previous-manifest.json', previous)
        latest = STATE / 'latest-preview'
        prior_workspace = latest.resolve().parent if latest.exists() else None
        if prior_workspace is not None:
            shutil.copytree(prior_workspace / 'landing-page', workspace / 'landing-page')
            if (prior_workspace / 'sources').exists():
                shutil.copytree(prior_workspace / 'sources', workspace / 'sources')
        else:
            shutil.copytree(ROOT / 'landing-page', workspace / 'landing-page')
        (workspace / 'sources').mkdir(exist_ok=True)
        atomic_json(workspace / 'job-input.json', {**config, 'mode': mode})
        prompt = (ROOT / 'scripts/archive-job/prompt.md').read_text()
        schema = ROOT / 'scripts/archive-job/result.schema.json'
        command = [codex, 'exec', '--ephemeral', '--skip-git-repo-check', '--sandbox', 'workspace-write',
                   '-c', 'approval_policy="never"',
                   '-c', 'sandbox_workspace_write.network_access=true',
                   '-c', 'sandbox_workspace_write.writable_roots=[]',
                   '-c', 'sandbox_workspace_write.exclude_slash_tmp=true',
                   '-c', 'sandbox_workspace_write.exclude_tmpdir_env_var=true',
                   '--json', '--output-schema', str(schema),
                   '--output-last-message', str(workspace / 'result.json'), '-']
        print('Controllo archivio iniziato:', run_id, '| modalità:', mode, flush=True)
        with (run_dir / 'events.jsonl').open('w') as events, (run_dir / 'stderr.log').open('w') as errors:
            watcher_stop = threading.Event()
            materialization_errors = []
            watcher = threading.Thread(target=watch_events,
                                       args=(run_dir / 'events.jsonl', workspace,
                                             config.get('legacyInlineMaxBytes', 1048576), watcher_stop, materialization_errors),
                                       daemon=True)
            watcher.start()
            process = subprocess.Popen(command, cwd=workspace, stdin=subprocess.PIPE, stdout=events,
                                       stderr=errors, text=True, start_new_session=True)
            try:
                process.communicate(input=prompt, timeout=config['timeoutSeconds'])
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                raise RuntimeError('Tempo massimo del controllo superato')
            finally:
                watcher_stop.set()
                watcher.join(timeout=5)
            if materialization_errors:
                raise RuntimeError('Materializzazione sorgenti fallita: ' + '; '.join(materialization_errors))
        if process.returncode:
            raise RuntimeError('Codex terminato con errore; consultare stderr.log e events.jsonl')
        result = json.loads((workspace / 'result.json').read_text())
        if result.get('status') not in ('prepared', 'unchanged', 'checked', 'needs_attention') or not isinstance(result.get('summary'), str):
            raise ValueError('Esito Codex non valido')
        if result['status'] == 'needs_attention':
            raise RuntimeError(result['summary'])
        current = validate_manifest(json.loads((workspace / 'candidate-manifest.json').read_text()), folder_ids)
        diff = differences(previous, current)
        if diff['removed']:
            raise ValueError('File rimossi dall’archivio: revisione manuale richiesta')
        if not (workspace / 'report.md').is_file():
            raise ValueError('Rapporto del controllo mancante')
        if check_only and result['status'] != 'checked':
            raise ValueError('La modalità check richiede esito checked')
        if not check_only:
            if result['status'] not in ('prepared', 'unchanged'):
                raise ValueError('Anteprima non pronta')
            if result['status'] == 'unchanged' and (diff['new'] or diff['modified']):
                raise ValueError('Il job dichiara invariato un archivio modificato')
            validate_preview(workspace)
        saved_workspace = run_dir / 'workspace'
        shutil.copytree(workspace, saved_workspace)
        if not check_only:
            pointer = STATE / 'latest-preview.tmp'
            if pointer.is_symlink():
                pointer.unlink()
            pointer.symlink_to(saved_workspace / 'landing-page', target_is_directory=True)
            pointer.replace(latest)
            atomic_json(STATE / 'manifest.json', current)
        record.update(status=result['status'], summary=result['summary'], filesCount=len(current['files']),
                      differences=diff, report=str(saved_workspace / 'report.md'))
        if not check_only:
            record['preview'] = str(latest)
        print(result['summary'], flush=True)
    except Exception as error:
        record.update(status='needs_attention', error=str(error))
        if workspace.exists() and not (run_dir / 'workspace').exists():
            shutil.copytree(workspace, run_dir / 'workspace', symlinks=True)
        save_review(run_dir / 'workspace', record)
        print('Controllo non completato:', error, file=sys.stderr, flush=True)
    finally:
        record['finishedAt'] = datetime.now(zone).isoformat()
        atomic_json(run_dir / 'status.json', record)
        atomic_json(STATE / 'last-run.json', record)
        shutil.rmtree(workspace)
        lock.close()
    return 1 if record['status'] == 'needs_attention' else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check-only', action='store_true', help='Verifica accesso e modifiche senza scaricare o preparare pagine')
    args = parser.parse_args()
    sys.exit(run(args.check_only))

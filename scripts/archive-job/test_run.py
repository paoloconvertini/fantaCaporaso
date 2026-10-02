import fcntl
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('archive_run', Path(__file__).with_name('run.py'))
job = importlib.util.module_from_spec(spec)
spec.loader.exec_module(job)

class ArchiveJobTest(unittest.TestCase):
    def entry(self, **changes):
        return {'id': 'sample', 'title': 'Classifica.xlsx', 'mimeType': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                'modifiedTime': '2026-10-01T08:00:00Z', 'folderId': 'allowed',
                'url': 'https://drive.google.com/file/d/sample/view', **changes}

    def test_rejects_other_folder_duplicate_ids_and_timeless_dates(self):
        for entries in ([self.entry(folderId='excluded')], [self.entry(), self.entry()], [self.entry(modifiedTime='2026-10-01')]):
            with self.assertRaises(ValueError):
                job.validate_manifest({'version': 1, 'files': entries}, {'allowed'})

    def test_detects_changes_and_deletions_by_id_and_metadata(self):
        previous = {'version': 1, 'files': [self.entry(), self.entry(id='removed')]}
        current = {'version': 1, 'files': [self.entry(modifiedTime='2026-10-02T08:00:00Z'), self.entry(id='new')]}
        self.assertEqual({'new': ['new'], 'modified': ['sample'], 'removed': ['removed']}, job.differences(previous, current))

    def test_check_does_not_advance_baseline_and_failure_preserves_good_preview(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ('config', 'scripts/archive-job', 'landing-page'):
                (root / name).mkdir(parents=True)
            (root / 'config/archive-job.json').write_text(json.dumps({'timezone': 'Europe/Rome', 'timeoutSeconds': 30, 'folders': [{'id': 'allowed'}]}))
            (root / 'scripts/archive-job/prompt.md').write_text('Approved test task')
            (root / 'scripts/archive-job/result.schema.json').write_text('{}')
            (root / 'landing-page/index.html').write_text('Original home')
            fake = root / 'fake-codex'
            fake.write_text('''#!/usr/bin/env python3
import json,sys,os
from pathlib import Path
sys.stdin.read()
mode=json.loads(Path('job-input.json').read_text())['mode']
status='checked' if mode=='check' else 'prepared'
if os.environ.get('FAKE_JOB_FAIL'):status='needs_attention'
manifest={"version":1,"files":[{"id":"sample","title":"Classifica.xlsx","mimeType":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","modifiedTime":"2026-10-01T08:00:00Z","folderId":"allowed","url":"https://drive.google.com/file/d/sample/view"}]}
Path('candidate-manifest.json').write_text(json.dumps(manifest))
Path('report.md').write_text('Verified test report')
if mode!='check':
 pages=['classifiche','risultati','coppa-italia','statistiche']
 Path('landing-page/index.html').write_text(' '.join('/'+p+'/' for p in pages))
 for p in pages:
  d=Path('landing-page')/p;d.mkdir(exist_ok=True);(d/'index.html').write_text('Test page')
out=Path(sys.argv[sys.argv.index('--output-last-message')+1]);out.write_text(json.dumps({'status':status,'summary':'Fixture result'}))
''')
            fake.chmod(0o700)
            state = root / 'state'
            with patch.object(job, 'ROOT', root), patch.object(job, 'STATE', state), patch.dict(os.environ, {'ARCHIVE_CODEX_BIN': str(fake)}):
                self.assertEqual(0, job.run(True))
                self.assertFalse((state / 'manifest.json').exists())
                self.assertFalse((state / 'latest-preview').exists())
                self.assertEqual(0, job.run(False))
                self.assertEqual('Original home', (root / 'landing-page/index.html').read_text())
                baseline = (state / 'manifest.json').read_bytes()
                preview = (state / 'latest-preview').resolve()
                with patch.dict(os.environ, {'FAKE_JOB_FAIL': '1'}):
                    self.assertEqual(1, job.run(False))
                self.assertEqual(baseline, (state / 'manifest.json').read_bytes())
                self.assertEqual(preview, (state / 'latest-preview').resolve())

    def test_bounded_artifact_materialization_rejects_oversized_and_unknown_files(self):
        import base64
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory); (workspace / 'sources').mkdir()
            (workspace / 'candidate-manifest.json').write_text(json.dumps({'version': 1, 'files': [self.entry(mimeType='image/jpeg')]}))
            event = {'type': 'item.completed', 'item': {'type': 'mcp_tool_call', 'tool': 'google_drive.fetch',
                'arguments': {'url': 'https://drive.google.com/file/d/sample/view', 'download_raw_file': True, 'include_base64': True},
                'result': {'content': [{'type': 'text', 'text': json.dumps({'b64_string': base64.b64encode(b'image fixture').decode()})}]}}}
            job.materialize_legacy(event, workspace, 1024)
            self.assertEqual(b'image fixture', (workspace / 'sources/sample.jpg').read_bytes())
            with self.assertRaises(ValueError):job.materialize_legacy(event, workspace, 2)
            event['item']['arguments']['url'] = 'https://drive.google.com/file/d/not-allowed/view'
            with self.assertRaises(ValueError):job.materialize_legacy(event, workspace, 1024)

    def test_preview_rejects_source_workbooks_and_temporary_download_links(self):
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory); landing = workspace / 'landing-page';landing.mkdir()
            for page in job.PAGES:
                (landing / page).mkdir(); (landing / page / 'index.html').write_text('Sports page')
            (landing / 'index.html').write_text(' '.join('/' + p + '/' for p in job.PAGES))
            job.validate_preview(workspace)
            workbook = landing / 'private.xlsx';workbook.write_bytes(b'Workbook')
            with self.assertRaises(ValueError):job.validate_preview(workspace)
            workbook.unlink()
            (landing / 'statistiche/index.html').write_text('https://files.oaiusercontent.com/temporary')
            with self.assertRaises(ValueError):job.validate_preview(workspace)

    def test_overlapping_run_does_not_start_codex(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory)
            with (state / 'run.lock').open('a') as lock, patch.object(job, 'STATE', state):
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                self.assertEqual(0, job.run(False))
                self.assertFalse((state / 'last-run.json').exists())

if __name__ == '__main__':
    unittest.main()

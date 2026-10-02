#!/usr/bin/env python3
"""Install the weekly archive preview as a user LaunchAgent."""
import argparse
import json
import os
from pathlib import Path
import plistlib
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
LABEL = 'it.fantacaporaso.archive-preview'
STATE = ROOT / '.local/archive-job'


def definition():
    config = json.loads((ROOT / 'config/archive-job.json').read_text())
    return {
        'Label': LABEL,
        'ProgramArguments': ['/bin/bash', str(ROOT / 'scripts/run-archive-job.sh')],
        'WorkingDirectory': str(ROOT),
        'EnvironmentVariables': {'PATH': str(Path.home() / '.volta/bin') + ':/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin',
                                 'TZ': config['timezone']},
        'StartCalendarInterval': {'Weekday': config['weekday'], 'Hour': config['hour'], 'Minute': config['minute']},
        'RunAtLoad': False,
        'ProcessType': 'Background',
        'ThrottleInterval': 60,
        'StandardOutPath': str(STATE / 'launchd.stdout.log'),
        'StandardErrorPath': str(STATE / 'launchd.stderr.log'),
    }


def install():
    os.umask(0o077)
    if sys.platform != 'darwin':
        raise RuntimeError('Il job launchd richiede macOS')
    config = json.loads((ROOT / 'config/archive-job.json').read_text())
    local_zone = Path('/etc/localtime').resolve()
    if not str(local_zone).endswith('/' + config['timezone']):
        raise RuntimeError('Il fuso del Mac deve essere ' + config['timezone'] + '; non viene modificato automaticamente')
    STATE.mkdir(parents=True, exist_ok=True)
    directory = Path.home() / 'Library/LaunchAgents'
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / (LABEL + '.plist')
    temporary = path.with_suffix('.plist.tmp')
    temporary.write_bytes(plistlib.dumps(definition()))
    subprocess.run(['plutil', '-lint', str(temporary)], check=True)
    service = 'gui/' + str(os.getuid()) + '/' + LABEL
    loaded = subprocess.run(['launchctl', 'print', service], capture_output=True).returncode == 0
    if loaded:
        subprocess.run(['launchctl', 'bootout', service], check=True)
    temporary.replace(path)
    subprocess.run(['launchctl', 'bootstrap', 'gui/' + str(os.getuid()), str(path)], check=True)
    subprocess.run(['launchctl', 'enable', service], check=True)
    print('Job installato:', path)
    print('Martedì alle 09:00 Europe/Rome; nessuna esecuzione immediata o pubblicazione automatica.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--render', action='store_true', help='Stampa il plist senza installarlo')
    args = parser.parse_args()
    if args.render:
        sys.stdout.buffer.write(plistlib.dumps(definition()))
    else:
        install()

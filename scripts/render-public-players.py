#!/usr/bin/env python3
"""Publish only public player identity and roster ownership, from one DB snapshot."""
import datetime
import json
from pathlib import Path
import sys
from zoneinfo import ZoneInfo

ROLES = {'PORTIERE', 'DIFENSORE', 'CENTROCAMPISTA', 'ATTACCANTE'}


def catalog(rows):
    if not isinstance(rows, list) or not rows:
        raise ValueError('Catalogo calciatori vuoto')
    players, seen = [], set()
    for row in rows:
        if any(not isinstance(row.get(key), str) or not row[key].strip() for key in ['name', 'club', 'role']):
            raise ValueError('Identità del calciatore incompleta')
        if row['role'] not in ROLES:
            raise ValueError('Ruolo sconosciuto')
        owner = row.get('owner')
        if owner is not None and (not isinstance(owner, str) or not owner.strip()):
            raise ValueError('Proprietario non valido')
        key = (row['name'].casefold(), row['club'].casefold(), row['role'])
        if key in seen:
            raise ValueError('Calciatore duplicato nel catalogo')
        seen.add(key)
        # Whitelist: account, crediti e costi non vengono esportati.
        players.append({key: row[key] for key in ['name', 'club', 'role']} | {'owner': owner})
    owners = {row['owner'] for row in players if row['owner'] is not None}
    owned = sum(row['owner'] is not None for row in players)
    if not owned:
        raise ValueError('Nessuna rosa nel catalogo: esportazione rifiutata')
    return {
        'updatedAt': datetime.datetime.now(ZoneInfo('Europe/Rome')).isoformat(timespec='seconds'),
        'teamCount': len(owners), 'rosteredCount': owned, 'freeCount': len(players) - owned,
        'players': sorted(players, key=lambda row: (row['name'].casefold(), row['club'].casefold()))
    }


if __name__ == '__main__':
    data = catalog(json.load(sys.stdin))
    output = Path(sys.argv[1])
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(output)
    print(f'Catalogo pubblico: {data["rosteredCount"]} nelle rose, {data["freeCount"]} svincolati.')

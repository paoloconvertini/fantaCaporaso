#!/usr/bin/env python3
"""Prepare a read-only public snapshot for the verified October 2026 market."""
import json
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
CONTAINER = 'fantasta-auction-postgres-1'


def main():
    if len(sys.argv) != 2:
        raise SystemExit('Uso: prepare-public-history.py <cartella-output>')
    stage = Path(sys.argv[1]).resolve()
    if not (stage / 'index.html').is_file() or not (stage / 'storico/index.html').is_file():
        raise SystemExit('Preparare prima una copia completa del sito pubblico nella cartella output')
    sql = """BEGIN READ ONLY;
SELECT jsonb_build_object('count', count(*), 'rounds', coalesce(jsonb_agg(round_data ORDER BY closed_at DESC), '[]'::jsonb))
FROM (SELECT h.closed_at, jsonb_build_object(
'player', h.player_name, 'team', h.player_team, 'role', h.player_role,
'value', h.player_value, 'winner', h.winner_name, 'price', h.winning_amount,
'bids', (SELECT coalesce(jsonb_agg(jsonb_build_object('participant', b.participant_name, 'amount', b.amount) ORDER BY b.amount DESC), '[]'::jsonb)
 FROM auction_history_bid b WHERE b.history_id=h.id)) AS round_data
FROM auction_history h WHERE h.closed_at >= timestamp '2026-10-01 00:00:00'
AND h.closed_at < timestamp '2026-10-02 00:00:00') rows;
COMMIT;"""
    result = subprocess.run(['docker', 'exec', '-i', CONTAINER, 'sh', '-c',
        'psql -v ON_ERROR_STOP=1 -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=sql, text=True, capture_output=True, check=True)
    data = json.loads(result.stdout)
    if data['count'] != 63:
        raise SystemExit(f"Storico cambiato: attese 63 aste, trovate {data['count']}. Riesaminare prima di pubblicare.")
    if any(not row['bids'] or not row['winner'] for row in data['rounds']):
        raise SystemExit('Offerte o vincitore mancanti: riesaminare i dati')
    path = stage / 'storico/data.json'
    existing = json.loads(path.read_text())
    code = '5c04ce0b-0e61-4410-940e-a07d0ba1a547'
    snapshot = {'code': code, 'label': 'Mercato di riparazione 1 — 1 ottobre 2026',
        'closedAt': datetime.now(timezone.utc).isoformat(), 'snapshot': True, 'rounds': data['rounds']}
    existing['sessions'] = [snapshot] + [s for s in existing.get('sessions', []) if s['code'] != code]
    path.write_text(json.dumps(existing, ensure_ascii=False, separators=(',', ':')))
    html = (ROOT / 'landing-page/storico/index.html').read_text()
    html = html.replace('Conclusa il ${new Date(session.closedAt)', "${session.snapshot ? 'Aggiornato il' : 'Conclusa il'} ${new Date(session.closedAt)")
    (stage / 'storico/index.html').write_text(html)
    print(f"Anteprima pronta: {data['count']} aste, {sum(len(r['bids']) for r in data['rounds'])} offerte. Nessuna modifica al database.")


if __name__ == '__main__':
    main()

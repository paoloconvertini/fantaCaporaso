#!/usr/bin/env python3
"""Render a public roster snapshot, without costs, budgets or account data."""
import collections
import datetime
import html
import json
from pathlib import Path
import sys
from zoneinfo import ZoneInfo

ROLES = [('PORTIERE', 'Portieri'), ('DIFENSORE', 'Difensori'),
         ('CENTROCAMPISTA', 'Centrocampisti'), ('ATTACCANTE', 'Attaccanti')]

def render(rows):
    if not isinstance(rows, list) or not rows:
        raise ValueError('Nessuna rosa da pubblicare')
    teams = collections.defaultdict(lambda: collections.defaultdict(list))
    known_roles = dict(ROLES)
    seen = set()
    for row in rows:
        if any(not isinstance(row.get(k), str) or not row[k].strip() for k in ['team', 'player', 'role', 'club']):
            raise ValueError('Nome, ruolo o squadra mancanti')
        if row['role'] not in known_roles:
            raise ValueError('Ruolo sconosciuto')
        key = (row['player'], row['club'])
        if key in seen:
            raise ValueError('Calciatore duplicato nelle rose')
        seen.add(key)
        teams[row['team']][row['role']].append(row)
    escape = html.escape
    cards = []
    for team, roles in sorted(teams.items(), key=lambda item: item[0].casefold()):
        sections = []
        for role, label in ROLES:
            players = sorted(roles[role], key=lambda row: row['player'].casefold())
            listing = ''.join(f'<li><strong>{escape(row["player"])}</strong><span>{escape(row["club"])}</span></li>' for row in players)
            sections.append(f'<section class="role"><h3>{label}<span>{len(players)}</span></h3><ul>{listing}</ul></section>')
        count = sum(len(players) for players in roles.values())
        cards.append(f'<details class="team-card"><summary><h2>{escape(team)}</h2><span>{count} calciatori</span></summary><div class="roles">{"".join(sections)}</div></details>')
    now = datetime.datetime.now(ZoneInfo('Europe/Rome'))
    return f'''<!doctype html>
<html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="theme-color" content="#07120f"><meta name="description" content="Le rose consolidate delle squadre FantaCaporaso, divise per ruolo."><title>Le rose | FantaCaporaso</title><link rel="icon" href="/favicon.svg" type="image/svg+xml"><link rel="stylesheet" href="/styles.css"><link rel="stylesheet" href="/rose/rosters.css"></head>
<body><main class="page rosters-page"><div class="ambient ambient-one" aria-hidden="true"></div><div class="ambient ambient-two" aria-hidden="true"></div><section class="shell" aria-labelledby="page-title"><header class="header"><a class="brand" href="/" aria-label="FantaCaporaso, pagina iniziale"><span class="brand-mark" aria-hidden="true">⚽</span><span><strong>Fanta</strong>Caporaso</span></a><span class="season">STAGIONE 2026/27</span></header><div class="rosters-content"><a class="back-link" href="/">← Torna alla home</a><p class="eyebrow">LE ROSE DELLA LEGA</p><h1 id="page-title">Le squadre.<br><em>La nostra lega.</em></h1><p class="intro">Le rose consolidate dopo il mercato di ottobre. Apri una squadra per consultare i calciatori, divisi per ruolo.</p><p class="update">{len(teams)} squadre · {len(rows)} calciatori · Aggiornato al <time datetime="{now.isoformat(timespec='seconds')}">{now.strftime('%d/%m/%Y alle %H:%M')}</time></p><div class="team-grid">{''.join(cards)}</div></div><footer><span>Portale ufficiale FantaCaporaso</span><span class="footer-separator" aria-hidden="true"></span><span>Rose consolidate</span></footer></section></main></body></html>'''

if __name__ == '__main__':
    rows = json.load(sys.stdin)
    document = render(rows)
    output = Path(sys.argv[1])
    output.mkdir(parents=True, exist_ok=True)
    output.joinpath('index.html').write_text(document, encoding='utf-8')
    print(f'Rose pubbliche generate: {len(rows)} calciatori.')

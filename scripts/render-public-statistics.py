#!/usr/bin/env python3
"""Extract bounded sports summaries from the readable Drive CSV of Formazioni."""
import argparse
import csv
import json
import math
import re
from pathlib import Path


def extract(path, source, expected_teams):
    rows = list(csv.reader(Path(path).open(encoding='utf-8')))
    header = rows.pop(0)
    if not header[1].endswith('ª'):
        raise ValueError('Giornata non riconosciuta')
    day = int(header[1][:-1])
    if not re.search(r'2026-2027 Formazioni 0?'+str(day)+r'ª Giornata\.xlsx$', source):
        raise ValueError('Stagione o giornata della fonte incoerente')
    if not 1 <= day <= 38:
        raise ValueError('Giornata fuori intervallo')
    def block(label, name_col, offset=2):
        indexes = [i for i, row in enumerate(rows) if label in row and row[name_col] == "Squadra"]
        if len(indexes) != 1:
            raise ValueError('Sezione mancante o ambigua: '+label)
        selected = rows[indexes[0]+offset:indexes[0]+offset+16]
        names = [r[name_col] for r in selected]
        if len(names) != 16 or len(set(names)) != 16 or set(names) != expected_teams:
            raise ValueError('Squadre non valide: '+label)
        return selected
    def n(row, col):
        value = float(row[col]) if row[col] else 0.0
        if not math.isfinite(value):
            raise ValueError('Valore non finito')
        return int(value) if value.is_integer() else value
    sections = []
    def add(key, title, description, heads, values):
        sections.append(dict(key=key, title=title, description=description, heads=heads, rows=values))
    values=[]
    for r in block("Riepilogo Partite in Inferiorita'",5):
        counts=[n(r,c) for c in range(9,15)]
        if any(v<0 or int(v)!=v for v in counts) or sum(counts)>day or sum((i+1)*v for i,v in enumerate(counts))!=n(r,15):
            raise ValueError('Conteggi inferiorità incoerenti: '+r[5])
        values.append([r[5],n(r,8),*counts,sum(counts),n(r,15)])
    add('inferiorita','Partite in inferiorità','Conteggi cumulativi. Il totale delle partite conta ogni gara una volta; i giocatori mancanti pesano una gara in 10 come 1 e una gara in 9 come 2.', ['Squadra','Giocatori a voto · giornata','In 10','In 9','In 8','In 7','In 6','In 5','Partite in inferiorità','Giocatori mancanti'], values)
    values=[]
    for r in block('Riepilogo Bonus Modificatore Difesa',5):
        counts=[n(r,c) for c in range(9,15)]
        if sum((i+1)*v for i,v in enumerate(counts))!=n(r,15):
            raise ValueError('Modificatore incoerente: '+r[5])
        values.append([r[5],n(r,8),*counts,n(r,15)])
    add('difesa','Modificatore difesa','Occorrenze cumulative per fascia e punti complessivi di bonus.', ['Squadra','Bonus giornata','+1','+2','+3','+4','+5','+6','Bonus totale'],values)
    values=[]
    for r in block('Riepilogo Bonus Capitano',5):
        counts=[n(r,c) for c in range(9,21)]
        if sum(counts)!=day or sum((-2.5+i*.5)*v for i,v in enumerate(counts))!=n(r,22):
            raise ValueError('Bonus capitano incoerente: '+r[5])
        values.append([r[5],n(r,8),*counts,n(r,22)])
    add('capitano','Bonus capitano','Bonus e malus di giornata; occorrenze cumulative per valore e saldo totale.', ['Squadra','Bonus giornata',*[str(-2.5+i*.5).replace('.',',') for i in range(12)],'Saldo totale'],values)
    for key,title,label in [('gol','Gol dei calciatori','Reti'),('assist','Assist','Assist')]:
        starters=block(label+' Titolari',28);squad=block(label+' Squadra',28)
        if [r[28] for r in starters]!=[r[28] for r in squad]:
            raise ValueError('Ordine squadre incoerente')
        values=[]
        for a,b in zip(starters,squad):
            for r in (a,b):
                if n(r,31)+n(r,32)!=n(r,33):
                    raise ValueError('Totale incoerente: '+r[28])
            values.append([a[28],n(a,31),n(a,33),n(b,31),n(b,33),n(b,33)-n(a,33)])
        add(key,title,'Valori dei riepiloghi “Titolari” e “Squadra” del file. La differenza mostra i bonus non utilizzati; i gol dei calciatori sono distinti dai gol degli incontri.', ['Squadra','Titolari · giornata','Titolari · totale','Squadra · giornata','Squadra · totale','Non utilizzati · totale'],values)
    for key,title,label in [('gialli','Ammonizioni','Gialli'),('rossi','Espulsioni','Rossi')]:
        values=[]
        for r in block(label,17,offset=2):
            if n(r,19)+n(r,21)!=n(r,22):
                raise ValueError('Cartellini incoerenti: '+r[17])
            values.append([r[17],n(r,19),n(r,21),n(r,22)])
        add(key,title,'Conteggi riportati nel riepilogo del file Formazioni.', ['Squadra','Giornata','Precedenti','Totale'],values)
    values=[[r[5],*[n(r,c) for c in range(8,18)],n(r,18)] for r in block("Riepilogo Voti d'Ufficio",5)]
    if any(sum(r[1:-1])!=r[-1] for r in values):
        raise ValueError('Voti ufficio incoerenti')
    add('ufficio',"Voti d’ufficio",'Conteggi del riepilogo originale; le colonne ordinali mantengono le etichette del file.', ['Squadra',*[f'{i}°' for i in range(1,11)],'Totale'],values)
    return dict(day=day,season='2026-2027',source=source,sections=sections)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('csv');parser.add_argument('--source',required=True)
    parser.add_argument('--archive',default='landing-page/archivio/data.json')
    args=parser.parse_args()
    path=Path(args.archive);data=json.loads(path.read_text())
    teams={r[1] for r in max(data['days'],key=lambda d:d['day'])['standings']}
    data['statistics']=extract(args.csv,args.source,teams)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    print(f"Statistiche verificate: {data['statistics']['day']}ª giornata, 8 tabelle, 16 squadre.")

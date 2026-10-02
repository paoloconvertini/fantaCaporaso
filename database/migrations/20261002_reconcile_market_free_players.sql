-- Riconciliazione puntuale del mercato 1 con il listone finale post cessioni.
-- Prima di applicare: salvare un backup e consultare docs/reviews/2026-10-02-svincolati.md.
-- Idempotente: nessuna modifica a rose, crediti, offerte o movimenti pregressi.
BEGIN;
DO $$ BEGIN
IF NOT EXISTS (SELECT 1 FROM mercato_config WHERE sessioncode='5c04ce0b-0e61-4410-940e-a07d0ba1a547' AND numeromercato=1)
THEN RAISE EXCEPTION 'Mercato diverso: riesaminare prima di applicare'; END IF;
IF (SELECT count(*) FROM player WHERE lower(name) IN ('patric','milik'))<>2
OR EXISTS(SELECT 1 FROM rosters r JOIN player p ON p.id=r.player_id WHERE lower(p.name) IN ('patric','milik'))
THEN RAISE EXCEPTION 'Patric/Milik mancanti, ambigui o in rosa: riesaminare'; END IF;
END $$;
LOCK TABLE rosters, participant IN SHARE MODE;
CREATE TEMP TABLE roster_before ON COMMIT DROP AS SELECT * FROM rosters;
CREATE TEMP TABLE participant_before ON COMMIT DROP AS SELECT * FROM participant;
CREATE TEMP TABLE excluded_free_names(name text primary key) ON COMMIT DROP;
INSERT INTO excluded_free_names VALUES ('albarracin'),('asllani'),('audero'),('barcella'),('berenbruch'),('bohinen'),('brorsson'),('cajuste'),('camara a'),('casas'),('cichero'),('colley m'),('comi'),('corrado'),('de marzi'),('di gregorio'),('farji'),('galazzi'),('gelli j'),('gigot'),('grabara'),('grosso f'),('iannoni'),('junior ligue'),('kamate'),('karlsson'),('kospo'),('kouassi'),('kouda'),('koutsoupias'),('kumer celik'),('lella'),('lindstrom'),('loubao'),('lysionok'),('mannini'),('martin'),('mazzitelli'),('milik'),('mlacic'),('moro l'),('nuamah'),('obaretin'),('odogu'),('okoro'),('oyono j'),('paleari'),('patric'),('perin'),('piana'),('pittarella'),('pizzignacco'),('pompei'),('pozzi a'),('prati'),('renzetti d'),('rossi f'),('rui modesto'),('siviero'),('suzuki'),('topalovic'),('zeroli');
UPDATE player p SET active=false,deletedat=coalesce(p.deletedat,now())
WHERE lower(trim(p.name)) IN (SELECT name FROM excluded_free_names)
AND NOT EXISTS(SELECT 1 FROM rosters r WHERE r.player_id=p.id);
UPDATE player SET active=false,deletedat=coalesce(deletedat,now()),
 departuresessioncode='5c04ce0b-0e61-4410-940e-a07d0ba1a547'
WHERE lower(name) IN ('patric','milik');
DO $$ BEGIN
IF EXISTS((SELECT * FROM rosters EXCEPT SELECT * FROM roster_before) UNION ALL (SELECT * FROM roster_before EXCEPT SELECT * FROM rosters))
OR EXISTS((SELECT * FROM participant EXCEPT SELECT * FROM participant_before) UNION ALL (SELECT * FROM participant_before EXCEPT SELECT * FROM participant))
THEN RAISE EXCEPTION 'Rose o crediti modificati'; END IF;
END $$;
COMMIT;

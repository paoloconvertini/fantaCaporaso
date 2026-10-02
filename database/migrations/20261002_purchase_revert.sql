-- Registra gli acquisti verificati senza modificare rose o crediti.
BEGIN;
ALTER TABLE market_movement ADD COLUMN IF NOT EXISTS auctionroundid varchar(255);
ALTER TABLE roster_acquisition ADD COLUMN IF NOT EXISTS ownerhistorycreated boolean NOT NULL DEFAULT false;
UPDATE roster_acquisition SET ownerhistorycreated=true
 WHERE purchasegroupcode LIKE 'verified-20261001-%';
ALTER TABLE market_movement DROP CONSTRAINT IF EXISTS market_movement_type_check;
ALTER TABLE market_movement ADD CONSTRAINT market_movement_type_check
 CHECK (type IN ('RELEASE','DEPARTED','EXCHANGE','MINI_PURCHASE','PURCHASE'));
INSERT INTO market_movement (id,participant_id,player_id,type,currentvalue,previousrosteramount,
 resultingrosteramount,sessioncode,operationcode,playernamesnapshot,playerteamsnapshot,
 destinationparticipantsnapshot,countedrelease,createdat)
 SELECT nextval('market_movement_seq'), a.participant_id,a.player_id,'PURCHASE',a.paidamount,0,
 a.paidamount,a.sessioncode,'PURCHASE:' || a.purchasegroupcode,p.name,p.team,owner.name,false,a.acquiredat
 FROM roster_acquisition a JOIN rosters r ON r.id=a.rosterentryid AND r.player_id=a.player_id AND r.participant_id=a.participant_id
 JOIN player p ON p.id=a.player_id JOIN participant owner ON owner.id=a.participant_id
 WHERE NOT EXISTS (SELECT 1 FROM market_movement m WHERE m.player_id=a.player_id AND m.operationcode='PURCHASE:' || a.purchasegroupcode);
-- Collega lo storico precedente solo in presenza di una corrispondenza univoca.
UPDATE market_movement m SET auctionroundid=(
 SELECT min(h.round_id) FROM auction_history h
 WHERE h.player_id=m.player_id AND h.winner_participant_id=m.participant_id
 AND h.closed_at BETWEEN m.createdat - interval '1 second' AND m.createdat + interval '5 seconds'
 HAVING count(*)=1)
 WHERE m.type='PURCHASE' AND m.auctionroundid IS NULL;
COMMIT;

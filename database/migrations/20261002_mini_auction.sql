-- Mini aste: applicare dopo backup, prima del deploy. Nessuna modifica a rose o crediti.
BEGIN;
CREATE SEQUENCE IF NOT EXISTS mini_auction_session_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS mini_auction_slot_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE IF NOT EXISTS mini_auction_session (
 id bigint PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
 code varchar(255) NOT NULL UNIQUE, label varchar(255) NOT NULL,
 sourcesessioncode varchar(255) NOT NULL, sourcedate date NOT NULL,
 status varchar(255) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','CLOSED','CANCELLED')),
 createdat timestamp, activatedat timestamp, closedat timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS mini_auction_one_open
 ON mini_auction_session ((1)) WHERE status IN ('DRAFT','ACTIVE');
CREATE TABLE IF NOT EXISTS mini_auction_slot (
 id bigint PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
 session_id bigint NOT NULL REFERENCES mini_auction_session(id),
 participant_id bigint NOT NULL REFERENCES participant(id),
 role varchar(255) NOT NULL, releasednames varchar(255) NOT NULL,
 refund double precision NOT NULL, minimumbid double precision NOT NULL,
 purchasesize integer NOT NULL, filled boolean NOT NULL DEFAULT false,
 acquiredplayerid bigint, paidamount double precision, filledat timestamp
);
CREATE TABLE IF NOT EXISTS mini_auction_slot_roster (
 slot_id bigint NOT NULL REFERENCES mini_auction_slot(id), position integer NOT NULL,
 roster_id bigint, PRIMARY KEY(slot_id,position)
);
CREATE TABLE IF NOT EXISTS mini_auction_slot_player (
 slot_id bigint NOT NULL REFERENCES mini_auction_slot(id), position integer NOT NULL,
 player_id bigint, PRIMARY KEY(slot_id,position)
);
-- Hibernate aggiorna il catalogo enum, ma non necessariamente il CHECK già esistente.
ALTER TABLE market_movement DROP CONSTRAINT IF EXISTS market_movement_type_check;
ALTER TABLE market_movement ADD CONSTRAINT market_movement_type_check
 CHECK (type IN ('RELEASE','DEPARTED','EXCHANGE','MINI_PURCHASE'));
COMMIT;

-- Registra ogni acquisto, inclusi manuali e con un solo offerente.
-- Applicare con backup prima del deploy. Non modifica rose, crediti o account.
BEGIN;
CREATE SEQUENCE IF NOT EXISTS roster_acquisition_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE IF NOT EXISTS roster_acquisition (
 id bigint PRIMARY KEY,
 rosterentryid bigint NOT NULL UNIQUE,
 player_id bigint NOT NULL REFERENCES player(id) ON DELETE CASCADE,
 participant_id bigint NOT NULL REFERENCES participant(id) ON DELETE CASCADE,
 sessioncode varchar(255) NOT NULL,
 purchasegroupcode varchar(255) NOT NULL,
 acquiredat timestamp NOT NULL,
 paidamount double precision NOT NULL,
 repairmarket boolean NOT NULL
);
COMMIT;

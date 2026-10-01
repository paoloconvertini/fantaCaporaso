-- Observer remains read-only even when associated with an existing participant.
BEGIN;
UPDATE app_user SET role = 'observer' WHERE role = 'user' AND participant_id IS NULL;
CREATE SEQUENCE IF NOT EXISTS player_target_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE IF NOT EXISTS player_target (
 id BIGINT PRIMARY KEY,
 account_id BIGINT NOT NULL REFERENCES app_user(id),
 player_id BIGINT NOT NULL REFERENCES player(id),
 CONSTRAINT player_target_account_player_unique UNIQUE (account_id, player_id)
);
ALTER TABLE player_target DROP CONSTRAINT IF EXISTS player_target_account_id_fkey;
ALTER TABLE player_target ADD CONSTRAINT player_target_account_id_fkey
 FOREIGN KEY (account_id) REFERENCES app_user(id) ON DELETE CASCADE;
ALTER TABLE player_target DROP CONSTRAINT IF EXISTS player_target_player_id_fkey;
ALTER TABLE player_target ADD CONSTRAINT player_target_player_id_fkey
 FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE;
COMMIT;

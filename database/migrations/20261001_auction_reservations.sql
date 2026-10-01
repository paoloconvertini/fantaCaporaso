BEGIN;
ALTER TABLE mercato_config ADD COLUMN IF NOT EXISTS prenotazioneabilitata boolean NOT NULL DEFAULT false;
ALTER TABLE mercato_config ADD COLUMN IF NOT EXISTS durataprenotazionesecondi integer NOT NULL DEFAULT 15;
COMMIT;

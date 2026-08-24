CREATE SEQUENCE IF NOT EXISTS auction_history_session_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS auction_history_session (
    id BIGINT PRIMARY KEY,
    session_code VARCHAR(64) NOT NULL,
    label VARCHAR(120) NOT NULL,
    closed_at TIMESTAMP NOT NULL,
    roster_snapshot_id BIGINT NOT NULL,
    publish_status VARCHAR(24) NOT NULL,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMP,
    publish_error VARCHAR(1000),
    CONSTRAINT uk_auction_history_session_code UNIQUE (session_code)
);

CREATE INDEX IF NOT EXISTS idx_auction_history_session_closed
    ON auction_history_session(closed_at);

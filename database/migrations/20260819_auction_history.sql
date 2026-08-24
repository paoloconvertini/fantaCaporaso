CREATE SEQUENCE IF NOT EXISTS auction_history_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS auction_history_bid_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS auction_history (
    id BIGINT PRIMARY KEY,
    bidder_count INTEGER NOT NULL,
    closed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    origin_round_id VARCHAR(64),
    player_id BIGINT,
    player_name VARCHAR(255) NOT NULL,
    player_role VARCHAR(32),
    player_team VARCHAR(255),
    player_value INTEGER,
    round_id VARCHAR(64) NOT NULL,
    session_code VARCHAR(64) NOT NULL,
    winner_name VARCHAR(255) NOT NULL,
    winner_participant_id BIGINT,
    winning_amount DOUBLE PRECISION NOT NULL,
    CONSTRAINT uk_auction_history_round UNIQUE (round_id)
);

CREATE TABLE IF NOT EXISTS auction_history_bid (
    id BIGINT PRIMARY KEY,
    amount DOUBLE PRECISION NOT NULL,
    participant_id BIGINT,
    participant_key VARCHAR(64) NOT NULL,
    participant_name VARCHAR(255) NOT NULL,
    history_id BIGINT NOT NULL REFERENCES auction_history(id),
    CONSTRAINT uk_auction_history_bid_participant UNIQUE (history_id, participant_key)
);

CREATE INDEX IF NOT EXISTS idx_auction_history_session ON auction_history(session_code, closed_at);
CREATE INDEX IF NOT EXISTS idx_auction_history_player ON auction_history(player_name);
CREATE INDEX IF NOT EXISTS idx_auction_history_value ON auction_history(player_value);
CREATE INDEX IF NOT EXISTS idx_auction_history_bid_history ON auction_history_bid(history_id);

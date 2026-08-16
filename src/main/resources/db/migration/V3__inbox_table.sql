
CREATE TABLE inbox_messages (
    message_id VARCHAR(255) NOT NULL PRIMARY KEY,
    payload JSONB NOT NULL,
    headers JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    last_error TEXT,
    source VARCHAR(50) NOT NULL
);

CREATE INDEX idx_inbox_status_received_at
    ON inbox_messages (status, received_at);

CREATE INDEX idx_inbox_processed_at
    ON inbox_messages (processed_at) 
    WHERE processed_at IS NOT NULL;
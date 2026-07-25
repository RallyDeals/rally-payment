-- V2__outbox_table.sql
-- Transactional Outbox table for reliable event publishing

CREATE TABLE outbox_messages (
    id UUID NOT NULL PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    last_error TEXT
);

-- Index for outbox relay polling (ordered by created_at for ordering guarantees)
CREATE INDEX idx_outbox_status_created_at 
    ON outbox_messages (status, created_at);

-- Index for finding messages by aggregate
CREATE INDEX idx_outbox_aggregate_id 
    ON outbox_messages (aggregate_id);

-- Index for cleanup queries
CREATE INDEX idx_outbox_published_at 
    ON outbox_messages (published_at) 
    WHERE published_at IS NOT NULL;
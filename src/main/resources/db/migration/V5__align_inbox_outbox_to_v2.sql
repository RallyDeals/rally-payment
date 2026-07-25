-- Align inbox/outbox tables with payment-service-details-v2 contract

ALTER TABLE outbox_messages
    RENAME COLUMN id TO message_id;

ALTER TABLE outbox_messages
    RENAME COLUMN event_type TO message_type;

ALTER TABLE outbox_messages
    ADD COLUMN IF NOT EXISTS topic VARCHAR(100),
    ADD COLUMN IF NOT EXISTS message_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS correlation_id UUID,
    ADD COLUMN IF NOT EXISTS causation_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

ALTER TABLE inbox_messages
    ADD COLUMN IF NOT EXISTS topic VARCHAR(100),
    ADD COLUMN IF NOT EXISTS message_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS correlation_id UUID,
    ADD COLUMN IF NOT EXISTS causation_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

ALTER TABLE inbox_messages
    DROP COLUMN IF EXISTS source;

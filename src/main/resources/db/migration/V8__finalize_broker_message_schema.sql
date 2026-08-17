ALTER TABLE inbox_messages
    ALTER COLUMN topic SET NOT NULL,
    ALTER COLUMN message_type SET NOT NULL;

ALTER TABLE outbox_messages
    ALTER COLUMN topic SET NOT NULL,
    ALTER COLUMN message_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inbox_topic
    ON inbox_messages (topic);

CREATE INDEX IF NOT EXISTS idx_inbox_correlation
    ON inbox_messages (correlation_id);

CREATE INDEX IF NOT EXISTS idx_outbox_topic
    ON outbox_messages (topic);

CREATE INDEX IF NOT EXISTS idx_outbox_correlation
    ON outbox_messages (correlation_id);
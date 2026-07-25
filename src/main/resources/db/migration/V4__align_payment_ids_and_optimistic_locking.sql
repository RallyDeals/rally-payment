-- Align payment identifiers with UUID-based service contracts and add optimistic locking

ALTER TABLE payments
    ALTER COLUMN order_id TYPE UUID USING order_id::UUID,
    ALTER COLUMN user_id TYPE UUID USING user_id::UUID;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment_methods
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

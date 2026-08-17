DROP INDEX IF EXISTS idx_payments_order_id;
ALTER TABLE payments
    ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);

CREATE UNIQUE INDEX uq_payment_methods_one_default_per_user
    ON payment_methods (user_id)
    WHERE is_default = TRUE;
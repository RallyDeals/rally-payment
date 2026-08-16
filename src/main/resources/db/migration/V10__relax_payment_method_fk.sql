
ALTER TABLE payments
    DROP CONSTRAINT fk_payments_payment_method;
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_payment_method
        FOREIGN KEY (payment_method_id)
        REFERENCES payment_methods (id)
        ON DELETE SET NULL;

ALTER TABLE payments
    ALTER COLUMN payment_method_id DROP NOT NULL;

ALTER TABLE payment_methods
    ADD COLUMN card_fingerprint VARCHAR(255) NULL;

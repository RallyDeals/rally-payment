-- V10__relax_payment_method_fk.sql
-- Phase 5: relax payments -> payment_methods FK so a saved method can be
-- hard-deleted without disturbing in-flight payments, and add a card
-- fingerprint column for duplicate-same-card detection (FR-007/FR-014, R-5).

-- 1. Drop the old cascade FK and re-add as ON DELETE SET NULL.
ALTER TABLE payments
    DROP CONSTRAINT fk_payments_payment_method;
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_payment_method
        FOREIGN KEY (payment_method_id)
        REFERENCES payment_methods (id)
        ON DELETE SET NULL;

-- 2. payment_method_id becomes nullable: payments may reference a removed method.
ALTER TABLE payments
    ALTER COLUMN payment_method_id DROP NOT NULL;

-- 3. Card fingerprint for duplicate detection on gateway-confirmed adds (R-4).
ALTER TABLE payment_methods
    ADD COLUMN card_fingerprint VARCHAR(255) NULL;

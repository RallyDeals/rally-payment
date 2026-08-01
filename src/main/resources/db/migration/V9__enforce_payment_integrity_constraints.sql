-- V9__enforce_payment_integrity_constraints.sql
-- Enforce data-integrity invariants for Phase 1.
-- Precondition: resolve any existing violations in dev data before applying (see quickstart.md §2).

-- One payment per order: replace the non-unique index with a unique constraint.
DROP INDEX IF EXISTS idx_payments_order_id;
ALTER TABLE payments
    ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);

-- At most one default payment method per user: partial unique index.
CREATE UNIQUE INDEX uq_payment_methods_one_default_per_user
    ON payment_methods (user_id)
    WHERE is_default = TRUE;
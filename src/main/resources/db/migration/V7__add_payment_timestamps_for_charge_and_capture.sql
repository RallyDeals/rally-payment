-- Add dedicated timestamps for charge and capture flows

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS charged_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS captured_at TIMESTAMP;

CREATE TABLE payment_methods (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    token VARCHAR(500) NOT NULL,
    card_brand VARCHAR(50),
    card_last4 VARCHAR(4),
    card_exp_month VARCHAR(2),
    card_exp_year VARCHAR(4)
);

CREATE TABLE payments (
    id UUID NOT NULL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    authorized_at TIMESTAMP,
    failed_at TIMESTAMP,
    voided_at TIMESTAMP,
    payment_method_id UUID NOT NULL,
    payment_intent_id VARCHAR(255),
    stripe_customer_id VARCHAR(255),
    CONSTRAINT fk_payments_payment_method
        FOREIGN KEY (payment_method_id)
        REFERENCES payment_methods (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_payments_payment_method_id ON payments (payment_method_id);
CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_user_id ON payments (user_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_payment_intent_id ON payments (payment_intent_id);
CREATE INDEX idx_payment_methods_user_id ON payment_methods (user_id);
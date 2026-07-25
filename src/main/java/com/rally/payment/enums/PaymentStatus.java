package com.rally.payment.enums;

public enum PaymentStatus {
    PENDING,           // payment_intent.created
    AUTHORIZED,        // payment_intent.amount_capturable_updated
    CHARGED,           // payment_intent.succeeded / charge.succeeded
    CAPTURED,          // capture completed
    FAILED,            // payment_intent.payment_failed
    VOIDED,           // payment_intent.canceled / manual void
    REQUIRES_ACTION,   // payment_intent.requires_action (3DS, etc.)
    REFUNDED,          // refund.created (full)
    PARTIALLY_REFUNDED // refund.created (partial)
}

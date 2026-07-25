package com.rally.payment.domain.enums;

public enum PaymentStatus {
    PENDING,           // payment_intent.created
    SUCCEEDED,         // payment_intent.succeeded / charge.succeeded
    FAILED,            // payment_intent.payment_failed
    CANCELED,          // payment_intent.canceled
    REQUIRES_ACTION,   // payment_intent.requires_action (3DS, etc.)
    REFUNDED,          // refund.created (full)
    PARTIALLY_REFUNDED,// refund.created (partial)
    AUTHORIZED
}
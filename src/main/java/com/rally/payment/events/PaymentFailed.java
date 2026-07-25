package com.rally.payment.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
    UUID aggregateId,
    String paymentIntentId,
    UUID orderId,
    String failureReason,
    Instant occurredAt
) implements DomainEvent {
    public PaymentFailed(UUID aggregateId, String paymentIntentId, UUID orderId, String failureReason) {
        this(aggregateId, paymentIntentId, orderId, failureReason, Instant.now());
    }

    @Override
    public String eventType() {
        return "Payment.Failed";
    }
}

package com.rally.payment.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequiresAction(
    UUID aggregateId,
    String paymentIntentId,
    UUID orderId,
    Instant occurredAt
) implements DomainEvent {
    public PaymentRequiresAction(UUID aggregateId, String paymentIntentId, UUID orderId) {
        this(aggregateId, paymentIntentId, orderId, Instant.now());
    }

    @Override
    public String eventType() {
        return "Payment.RequiresAction";
    }
}

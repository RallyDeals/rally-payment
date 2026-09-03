package com.rally.payment.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequiresAction(
    UUID aggregateId,
    UUID orderId,
    Instant occurredAt
) implements DomainEvent {
    public PaymentRequiresAction(UUID aggregateId, UUID orderId) {
        this(aggregateId, orderId, Instant.now());
    }

    @Override
    public String eventType() {
        return "Payment.RequiresAction";
    }
}

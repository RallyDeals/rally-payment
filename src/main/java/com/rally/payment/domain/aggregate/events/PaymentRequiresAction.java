package com.rally.payment.domain.aggregate.events;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record PaymentRequiresAction(
    UUID aggregateId,
    String paymentIntentId,
    String orderId,
    Instant occurredAt
) implements DomainEvent, Serializable {
    public PaymentRequiresAction(UUID aggregateId, String paymentIntentId, String orderId) {
        this(aggregateId, paymentIntentId, orderId, Instant.now());
    }

    @Override
    public String eventType() {
        return "PaymentRequiresAction";
    }
}
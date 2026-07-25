package com.rally.payment.domain.aggregate.events;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
    UUID aggregateId,
    String paymentIntentId,
    String orderId,
    String failureReason,
    Instant occurredAt
) implements DomainEvent, Serializable {
    public PaymentFailed(UUID aggregateId, String paymentIntentId, String orderId, String failureReason) {
        this(aggregateId, paymentIntentId, orderId, failureReason, Instant.now());
    }

    @Override
    public String eventType() {
        return "PaymentFailed";
    }
}
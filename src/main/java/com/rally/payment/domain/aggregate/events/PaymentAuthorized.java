package com.rally.payment.domain.aggregate.events;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAuthorized(
    UUID aggregateId,
    String paymentIntentId,
    String orderId,
    BigDecimal amount,
    Instant occurredAt
) implements DomainEvent, Serializable {
    public PaymentAuthorized(UUID aggregateId, String paymentIntentId, String orderId, BigDecimal amount) {
        this(aggregateId, paymentIntentId, orderId, amount, Instant.now());
    }

    @Override
    public String eventType() {
        return "PaymentAuthorized";
    }
}
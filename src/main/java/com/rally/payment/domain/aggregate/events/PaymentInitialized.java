package com.rally.payment.domain.aggregate.events;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentInitialized(
    UUID aggregateId,
    String orderId,
    BigDecimal amount,
    Instant occurredAt
) implements DomainEvent, Serializable {
    public PaymentInitialized(UUID aggregateId, String orderId, BigDecimal amount) {
        this(aggregateId, orderId, amount, Instant.now());
    }

    @Override
    public String eventType() {
        return "PaymentInitialized";
    }
}
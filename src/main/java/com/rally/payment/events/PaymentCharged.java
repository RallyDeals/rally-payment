package com.rally.payment.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCharged(
    UUID aggregateId,
    UUID orderId,
    BigDecimal amount,
    Instant occurredAt
) implements DomainEvent {
    public PaymentCharged(UUID aggregateId, UUID orderId, BigDecimal amount) {
        this(aggregateId, orderId, amount, Instant.now());
    }

    @Override
    public String eventType() {
        return "Payment.Charged";
    }
}

package com.rally.payment.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCaptured(
    UUID aggregateId,
    String paymentIntentId,
    UUID orderId,
    BigDecimal amount,
    Instant occurredAt
) implements DomainEvent {
    public PaymentCaptured(UUID aggregateId, String paymentIntentId, UUID orderId, BigDecimal amount) {
        this(aggregateId, paymentIntentId, orderId, amount, Instant.now());
    }

    @Override
    public String eventType() {
        return "Payment.Captured";
    }
}

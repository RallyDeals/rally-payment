package com.rally.payment.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
    UUID aggregateId,
    UUID orderId,
    BigDecimal amount,
    String failureReason,
    String errorCode,
    Instant occurredAt
) implements DomainEvent {
    public PaymentFailed(UUID aggregateId, UUID orderId, BigDecimal amount, String failureReason, String errorCode) {
        this(aggregateId, orderId, amount, failureReason, errorCode, Instant.now());
    }

    @Override
    public String eventType() {
        return "Payment.Failed";
    }
}
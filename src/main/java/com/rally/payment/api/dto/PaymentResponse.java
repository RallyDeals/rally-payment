package com.rally.payment.api.dto;

import com.rally.payment.enums.PaymentStatus;
import com.rally.payment.model.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID orderId,
    UUID userId,
    BigDecimal amount,
    PaymentStatus status,
    String failureReason,
    Instant createdAt,
    Instant paidAt,
    Instant authorizedAt,
    Instant failedAt,
    Instant voidedAt,
    UUID paymentMethodId,
    String paymentIntentId,
    String stripeCustomerId
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getCreatedAt(),
            payment.getPaidAt(),
            payment.getAuthorizedAt(),
            payment.getFailedAt(),
            payment.getVoidedAt(),
            payment.getPaymentMethodId(),
            payment.getPaymentIntentId(),
            payment.getStripeCustomerId()
        );
    }
}

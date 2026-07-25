package com.rally.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
    @NotNull UUID paymentMethodId,
    @NotNull UUID userId,
    @NotNull UUID orderId,
    @NotNull @Positive BigDecimal amount
) {
}

package com.rally.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AuthorizePaymentRequest(
    @NotNull UUID paymentMethodId,
    @NotBlank String paymentIntentId
) {
}

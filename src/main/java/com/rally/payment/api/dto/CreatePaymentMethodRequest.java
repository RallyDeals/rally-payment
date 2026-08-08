package com.rally.payment.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentMethodRequest(
    @NotBlank String paymentMethodId,
    boolean isDefault
) {
}

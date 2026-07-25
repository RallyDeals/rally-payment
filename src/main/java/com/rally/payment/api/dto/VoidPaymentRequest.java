package com.rally.payment.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidPaymentRequest(
    @NotBlank String reason
) {
}

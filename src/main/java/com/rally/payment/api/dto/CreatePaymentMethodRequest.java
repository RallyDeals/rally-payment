package com.rally.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentMethodRequest(
    @NotNull UUID userId,
    @NotBlank String type,
    @NotBlank String token,
    boolean isDefault,
    @NotBlank String cardBrand,
    @NotBlank String cardLast4,
    @NotBlank String cardExpMonth,
    @NotBlank String cardExpYear
) {
}

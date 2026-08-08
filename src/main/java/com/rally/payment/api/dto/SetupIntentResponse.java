package com.rally.payment.api.dto;

public record SetupIntentResponse(
    String setupIntentId,
    String clientSecret,
    boolean requiresAction
) {
}

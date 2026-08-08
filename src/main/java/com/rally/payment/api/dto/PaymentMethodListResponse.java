package com.rally.payment.api.dto;

import java.util.List;

public record PaymentMethodListResponse(List<PaymentMethodResponse> items) {
}
package com.rally.payment.messaging.contract;

import java.util.UUID;

public record PaymentTimeoutRequested(
    UUID orderId
) {
}
package com.rally.payment.messaging.contract;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInitiationRequested(
    UUID userId,
    UUID orderId,
    UUID paymentMethodId,
    BigDecimal amount
) {
}

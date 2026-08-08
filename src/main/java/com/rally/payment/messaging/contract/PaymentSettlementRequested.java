package com.rally.payment.messaging.contract;

import java.util.UUID;

public record PaymentSettlementRequested(
    UUID paymentId,
    UUID orderId
) {
}
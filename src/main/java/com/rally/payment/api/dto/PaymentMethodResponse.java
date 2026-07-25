package com.rally.payment.api.dto;

import com.rally.payment.model.PaymentMethod;
import com.rally.payment.model.PaymentMethodCard;
import java.util.UUID;

public record PaymentMethodResponse(
    UUID id,
    UUID userId,
    String type,
    boolean isDefault,
    String cardBrand,
    String cardLast4,
    String cardExpMonth,
    String cardExpYear
) {
    public static PaymentMethodResponse from(PaymentMethod paymentMethod) {
        PaymentMethodCard card = paymentMethod.getPaymentMethodCard();
        return new PaymentMethodResponse(
            paymentMethod.getId(),
            paymentMethod.getUserId(),
            paymentMethod.getType(),
            paymentMethod.isDefault(),
            card != null ? card.getCardBrand() : null,
            card != null ? card.getCardLast4() : null,
            card != null ? card.getCardExpMonth() : null,
            card != null ? card.getCardExpYear() : null
        );
    }
}

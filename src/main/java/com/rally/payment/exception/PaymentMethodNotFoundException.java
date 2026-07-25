package com.rally.payment.exception;

import java.util.UUID;

public class PaymentMethodNotFoundException extends RuntimeException {

    public PaymentMethodNotFoundException(UUID paymentMethodId) {
        super("Payment method not found: " + paymentMethodId);
    }
}

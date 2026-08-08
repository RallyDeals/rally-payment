package com.rally.payment.exception;

public class DuplicatePaymentMethodException extends RuntimeException {

    public DuplicatePaymentMethodException(String message) {
        super(message);
    }
}

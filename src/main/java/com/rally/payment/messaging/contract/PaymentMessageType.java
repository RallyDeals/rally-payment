package com.rally.payment.messaging.contract;

import java.util.Arrays;
import java.util.Optional;

public enum PaymentMessageType {
    INIT_REQUIRED_CHARGE("Payment.InitRequired.Charge"),
    INIT_REQUIRED_AUTHORIZE("Payment.InitRequired.Authorize"),
    SETTLEMENT_REQUIRED_CAPTURE("Payment.SettlementRequired.Capture"),
    SETTLEMENT_REQUIRED_VOID("Payment.SettlementRequired.Void"),
    TIMEOUT("Payment.Timeout"),
    AUTHORIZED("Payment.Authorized"),
    CHARGED("Payment.Charged"),
    CAPTURED("Payment.Captured"),
    FAILED("Payment.Failed"),
    VOIDED("Payment.Voided"),
    REQUIRES_ACTION("Payment.RequiresAction"),
    INITIALIZED("Payment.Initialized"),
    REFUNDED("Payment.Refunded");

    private final String value;

    PaymentMessageType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<PaymentMessageType> fromValue(String value) {
        return Arrays.stream(values())
            .filter(type -> type.value.equals(value))
            .findFirst();
    }
}

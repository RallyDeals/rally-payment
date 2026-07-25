package com.rally.payment.messaging.contract;

public final class PaymentMessageHeaders {

    public static final String ID = "X-Id";
    public static final String TYPE = "X-Type";
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String CAUSATION_ID = "X-Causation-Id";
    public static final String TRACE_ID = "X-Trace-Id";

    private PaymentMessageHeaders() {
    }
}

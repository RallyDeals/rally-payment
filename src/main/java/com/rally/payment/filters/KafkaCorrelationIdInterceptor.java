package com.rally.payment.filters;

import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

@Component
public class KafkaCorrelationIdInterceptor implements RecordInterceptor<Object, Object> {

    private final BaggageManager baggageManager;

    public KafkaCorrelationIdInterceptor(BaggageManager baggageManager) {
        this.baggageManager = baggageManager;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        String correlationId = extractHeader(record, PaymentMessageHeaders.CORRELATION_ID);
        if (correlationId != null) {
            MDC.put(PaymentMessageHeaders.CORRELATION_ID, correlationId);
            baggageManager.createBaggageInScope(PaymentMessageHeaders.CORRELATION_ID, correlationId);
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(PaymentMessageHeaders.CORRELATION_ID);
    }

    private String extractHeader(ConsumerRecord<Object, Object> record, String key) {
        var header = record.headers().lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}

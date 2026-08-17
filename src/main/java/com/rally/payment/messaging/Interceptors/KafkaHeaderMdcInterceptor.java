package com.rally.payment.messaging.Interceptors;

import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class KafkaHeaderMdcInterceptor implements RecordInterceptor<String, JsonNode> {

    // Define your MDC keys
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_CAUSATION_ID = "causationId";
    private static final String MDC_INCOMING_MESSAGE_ID = "incomingMessageId";

    @Override
    public @Nullable ConsumerRecord<String, JsonNode> intercept(ConsumerRecord<String, JsonNode> record, Consumer<String, JsonNode> consumer) {

        String traceId = extractHeaderValue(record, PaymentMessageHeaders.TRACE_ID);
        String correlationId = extractHeaderValue(record, PaymentMessageHeaders.CORRELATION_ID);
        String causationId = extractHeaderValue(record, PaymentMessageHeaders.CAUSATION_ID);
        String messageId = extractHeaderValue(record, PaymentMessageHeaders.ID);

        if (traceId != null) {
            MDC.put(MDC_TRACE_ID, traceId);
        } else {
            MDC.put(MDC_TRACE_ID, UUID.randomUUID().toString());
        }

        if (correlationId != null) {
            MDC.put(MDC_CORRELATION_ID, correlationId);
        } else {
            MDC.put(MDC_CORRELATION_ID, MDC.get(MDC_TRACE_ID));
        }

        if (causationId != null) {
            MDC.put(MDC_CAUSATION_ID, causationId);
        }

        if (messageId != null) {
            MDC.put(MDC_INCOMING_MESSAGE_ID, messageId);
        }

        return record;
    }

    @Override
    public void success(ConsumerRecord<String, JsonNode> record, Consumer<String, JsonNode> consumer) {
        MDC.clear();
    }

    @Override
    public void failure(ConsumerRecord<String, JsonNode> record, Exception exception, Consumer<String, JsonNode> consumer) {
        MDC.clear();
    }

    private String extractHeaderValue(ConsumerRecord<String, JsonNode> record, String headerKey) {
        Header header = record.headers().lastHeader(headerKey);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }
}
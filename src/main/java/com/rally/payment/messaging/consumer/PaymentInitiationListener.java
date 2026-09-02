package com.rally.payment.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.rally.payment.messaging.contract.PaymentInitiationRequested;
import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import com.rally.payment.messaging.contract.PaymentMessageType;
import com.rally.payment.messaging.contract.PaymentSettlementRequested;
import com.rally.payment.messaging.contract.PaymentTimeoutRequested;
import com.rally.payment.messaging.inbox.InboxMessage;
import com.rally.payment.metrics.PaymentMetrics;
import com.rally.payment.repository.InboxJpaRepository;
import com.rally.payment.service.PaymentService;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class PaymentInitiationListener {

    private static final String DEFAULT_TOPIC = "order.payments_requested";
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final InboxJpaRepository inboxJpaRepository;
    private final PaymentService paymentService;
    private final PaymentMetrics metrics;

    public PaymentInitiationListener(InboxJpaRepository inboxJpaRepository, PaymentService paymentService, PaymentMetrics metrics) {
        this.inboxJpaRepository = inboxJpaRepository;
        this.paymentService = paymentService;
        this.metrics = metrics;
    }

    @Transactional
    @KafkaListener(topics = DEFAULT_TOPIC)
    public void onMessage(
        ConsumerRecord<String, JsonNode> record,
        @Header(PaymentMessageHeaders.ID) String messageId,
        @Header(PaymentMessageHeaders.TYPE) String messageType,
        @Header(value = PaymentMessageHeaders.CORRELATION_ID, required = false) String correlationId,
        @Header(value = PaymentMessageHeaders.TRACEPARENT, required = false) String traceparent,
        @Payload JsonNode payload
    ) {
        PaymentMessageType resolvedType = PaymentMessageType.fromValue(messageType)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported payment message type: " + messageType));

        log.info("Kafka message consumed: messageId={}, type={}, correlationId={}, topic={}, partition={}, offset={}",
                messageId, messageType, correlationId, record.topic(), record.partition(), record.offset());

        InboxMessage inboxMessage = InboxMessage.builder()
            .messageId(messageId)
            .topic(record.topic())
            .messageType(messageType)
            .correlationId(parseUuid(correlationId))
            .traceId(parseTraceId(traceparent))
            .payload(payload)
            .headers(toJsonNode(extractHeaders(record)))
            .status("RECEIVED")
            .causationId(null)
            .build();

        try {
            inboxJpaRepository.save(inboxMessage);
        } catch (DataIntegrityViolationException ex) {
            log.info("Skipping duplicate payment initiation message {}", messageId);
            return;
        }

        metrics.recordKafkaEventProcessed(messageType);
        try {
            dispatch(resolvedType, payload);
        } catch (Exception e) {
            log.error("Kafka message processing failed: messageId={}, type={}, correlationId={}, topic={}",
                    messageId, messageType, correlationId, record.topic(), e);
            throw e;
        }

        inboxMessage.setStatus("PROCESSED");
        inboxMessage.setProcessedAt(Instant.now());
        inboxJpaRepository.save(inboxMessage);

        log.info("Kafka message processed: messageId={}, type={}, correlationId={}, topic={}", messageId, messageType, correlationId, record.topic());
    }

    private void dispatch(PaymentMessageType resolvedType, JsonNode payload) {
        switch (resolvedType) {
            case INIT_REQUIRED_CHARGE, INIT_REQUIRED_AUTHORIZE -> {
                PaymentInitiationRequested requested = OBJECT_MAPPER.convertValue(payload, PaymentInitiationRequested.class);
                paymentService.createPaymentFromInitiation(requested, resolvedType);
            }
            case SETTLEMENT_REQUIRED_CAPTURE -> {
                PaymentSettlementRequested requested = OBJECT_MAPPER.convertValue(payload, PaymentSettlementRequested.class);
                paymentService.capturePaymentFromSettlement(requested);
            }
            case SETTLEMENT_REQUIRED_VOID -> {
                PaymentSettlementRequested requested = OBJECT_MAPPER.convertValue(payload, PaymentSettlementRequested.class);
                paymentService.voidPaymentFromSettlement(requested);
            }
            case TIMEOUT -> {
                PaymentTimeoutRequested requested = OBJECT_MAPPER.convertValue(payload, PaymentTimeoutRequested.class);
                paymentService.resolveTimeout(requested);
            }
            default -> throw new IllegalArgumentException("Unsupported payment message type: " + resolvedType);
        }
    }

    private JsonNode toJsonNode(Object value) {
        return OBJECT_MAPPER.valueToTree(value);
    }

    private Map<String, String> extractHeaders(ConsumerRecord<String, JsonNode> record) {
        Set<String> allowedHeaders = Set.of(
            PaymentMessageHeaders.ID,
            PaymentMessageHeaders.TYPE,
            PaymentMessageHeaders.CORRELATION_ID,
            PaymentMessageHeaders.TRACEPARENT
        );
        Map<String, String> headers = new LinkedHashMap<>();
        record.headers().forEach(header -> {
            if (allowedHeaders.contains(header.key())) {
                headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
            }
        });
        headers.putIfAbsent(PaymentMessageHeaders.ID, record.key());
        return headers;
    }

    private String parseTraceId(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        String[] parts = traceparent.split("-");
        if (parts.length >= 2 && parts[1].length() == 32) {
            return parts[1];
        }
        return null;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return UUID.fromString(value);
    }
}

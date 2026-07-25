package com.rally.payment.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.rally.payment.messaging.contract.PaymentInitiationRequested;
import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import com.rally.payment.messaging.contract.PaymentMessageType;
import com.rally.payment.messaging.inbox.InboxMessage;
import com.rally.payment.repository.InboxJpaRepository;
import com.rally.payment.service.PaymentService;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class PaymentInitiationListener {

    private static final String DEFAULT_TOPIC = "order.payment_initiation_requested";
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final InboxJpaRepository inboxJpaRepository;
    private final PaymentService paymentService;

    public PaymentInitiationListener(InboxJpaRepository inboxJpaRepository, PaymentService paymentService) {
        this.inboxJpaRepository = inboxJpaRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    @KafkaListener(topics = DEFAULT_TOPIC)
    public void onMessage(
        ConsumerRecord<String, PaymentInitiationRequested> record,
        @Header(PaymentMessageHeaders.ID) String messageId,
        @Header(PaymentMessageHeaders.TYPE) String messageType,
        @Header(value = PaymentMessageHeaders.CORRELATION_ID, required = false) String correlationId,
        @Header(value = PaymentMessageHeaders.CAUSATION_ID, required = false) String causationId,
        @Header(value = PaymentMessageHeaders.TRACE_ID, required = false) String traceId,
        @Payload PaymentInitiationRequested payload
    ) {
        PaymentMessageType resolvedType = PaymentMessageType.fromValue(messageType)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported payment initiation message type: " + messageType));

        if (resolvedType != PaymentMessageType.INIT_REQUIRED_CHARGE && resolvedType != PaymentMessageType.INIT_REQUIRED_AUTHORIZE) {
            throw new IllegalArgumentException("Unsupported payment initiation flow: " + messageType);
        }

        if (inboxJpaRepository.existsByMessageId(messageId)) {
            log.info("Skipping duplicate payment initiation message {}", messageId);
            return;
        }

        InboxMessage inboxMessage = InboxMessage.builder()
            .messageId(messageId)
            .topic(record.topic())
            .messageType(messageType)
            .correlationId(parseUuid(correlationId))
            .causationId(causationId)
            .traceId(traceId)
            .payload(toJsonNode(payload))
            .headers(toJsonNode(extractHeaders(record)))
            .status("RECEIVED")
            .build();

        inboxJpaRepository.save(inboxMessage);
        paymentService.createPaymentFromInitiation(payload);

        inboxMessage.setStatus("PROCESSED");
        inboxMessage.setProcessedAt(Instant.now());
        inboxJpaRepository.save(inboxMessage);

        log.info("Stored payment initiation message {} from topic {}", messageId, record.topic());
    }

    private JsonNode toJsonNode(Object value) {
        return OBJECT_MAPPER.valueToTree(value);
    }

    private Map<String, String> extractHeaders(ConsumerRecord<String, PaymentInitiationRequested> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        record.headers().forEach(header -> headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8)));
        headers.putIfAbsent(PaymentMessageHeaders.ID, record.key());
        headers.putIfAbsent(KafkaHeaders.RECEIVED_TOPIC, record.topic());
        return headers;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return UUID.fromString(value);
    }
}

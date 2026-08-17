package com.rally.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rally.payment.config.StripeProperties;
import com.rally.payment.events.DomainEvent;
import com.rally.payment.events.PaymentAuthorized;
import com.rally.payment.events.PaymentCaptured;
import com.rally.payment.events.PaymentCharged;
import com.rally.payment.events.PaymentFailed;
import com.rally.payment.events.PaymentInitialized;
import com.rally.payment.events.PaymentRefunded;
import com.rally.payment.events.PaymentRequiresAction;
import com.rally.payment.events.PaymentVoided;
import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import com.rally.payment.messaging.contract.PaymentMessageType;
import com.rally.payment.messaging.outbox.OutboxMessage;
import com.rally.payment.metrics.PaymentMetrics;
import com.rally.payment.repository.OutboxJpaRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentEventPublisher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PAYMENT_METHOD_CARD = "card";

    private final OutboxJpaRepository outboxRepository;
    private final PaymentMetrics metrics;
    private final StripeProperties stripeProperties;
    private final Tracer tracer;

    public PaymentEventPublisher(
            OutboxJpaRepository outboxRepository,
            PaymentMetrics metrics,
            StripeProperties stripeProperties,
            Tracer tracer
    ) {
        this.outboxRepository = outboxRepository;
        this.metrics = metrics;
        this.stripeProperties = stripeProperties;
        this.tracer = tracer;
    }

    @EventListener
    public void onDomainEvent(DomainEvent event) {
        switch (event) {
            case PaymentCharged e -> {
                metrics.recordPaymentSucceeded(currency(), PAYMENT_METHOD_CARD);
                writeOutcomeOutbox(e.aggregateId(), e.orderId(), e.paymentIntentId(), e.amount(), PaymentMessageType.CHARGED);
            }
            case PaymentCaptured e -> {
                metrics.recordPaymentSucceeded(currency(), PAYMENT_METHOD_CARD);
                writeOutcomeOutbox(e.aggregateId(), e.orderId(), e.paymentIntentId(), e.amount(), PaymentMessageType.CAPTURED);
            }
            case PaymentAuthorized e -> {
                metrics.recordPaymentSucceeded(currency(), PAYMENT_METHOD_CARD);
                writeOutcomeOutbox(e.aggregateId(), e.orderId(), e.paymentIntentId(), e.amount(), PaymentMessageType.AUTHORIZED);
            }
            case PaymentVoided e -> {
                metrics.recordPaymentSucceeded(currency(), PAYMENT_METHOD_CARD);
                writeOutcomeOutbox(e.aggregateId(), e.orderId(), e.paymentIntentId(), e.amount(), PaymentMessageType.VOIDED);
            }
            case PaymentFailed e -> {
                metrics.recordPaymentFailed(currency(), PAYMENT_METHOD_CARD);
                writeFailureOutbox(e);
            }
            case PaymentInitialized e -> {
                metrics.recordPaymentCreated(currency(), PAYMENT_METHOD_CARD);
                log.debug("Payment.Initialized is intentionally not published; order-payment contract does not map it");
            }
            case PaymentRequiresAction e ->
                    log.debug("Payment.RequiresAction is mapped to FAILED at orchestration; no outbox row for {}", e.aggregateId());
            case PaymentRefunded e ->
                    log.debug("Refunds are not yet supported; no outbox row for {}", e.aggregateId());
            default -> log.debug("Unmapped domain event {}; no outbox row", event.eventType());
        }
    }

    private String currency() {
        return stripeProperties.getCurrency() != null ? stripeProperties.getCurrency() : "usd";
    }

    private void writeFailureOutbox(PaymentFailed event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", event.aggregateId());
        payload.put("orderId", event.orderId());
        payload.put("paymentIntentId", event.paymentIntentId());
        payload.put("amount", event.amount());
        payload.put("errorMessage", event.failureReason());
        payload.put("errorCode", event.errorCode());
        writeOutbox(event.aggregateId(), event.orderId(), event.paymentIntentId(), PaymentMessageType.FAILED, payload);
    }

    private void writeOutcomeOutbox(UUID paymentId, UUID orderId, String paymentIntentId, BigDecimal amount, PaymentMessageType type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", paymentId);
        payload.put("orderId", orderId);
        payload.put("paymentIntentId", paymentIntentId);
        payload.put("amount", amount);
        writeOutbox(paymentId, orderId, paymentIntentId, type, payload);
    }

    private void writeOutbox(UUID paymentId, UUID orderId, String paymentIntentId, PaymentMessageType type, Map<String, Object> payload) {

        String currentTraceId = MDC.get("traceId");
        String currentCorrelationId = MDC.get("correlationId");
        String incomingMessageId = MDC.get("incomingMessageId");

        Span currentSpan = tracer.currentSpan();
        String micrometerTraceId = currentSpan != null ? currentSpan.context().traceId() : null;

        String traceIdToUse = micrometerTraceId != null
                ? micrometerTraceId
                : (currentTraceId != null ? currentTraceId : paymentId.toString());

        String correlationIdToUse = currentCorrelationId != null ? currentCorrelationId : orderId.toString();

        String causationIdToUse = incomingMessageId != null ? incomingMessageId : traceIdToUse;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(PaymentMessageHeaders.ID, UUID.randomUUID().toString());
        headers.put(PaymentMessageHeaders.TYPE, type.value());

        headers.put(PaymentMessageHeaders.CAUSATION_ID, causationIdToUse);
        headers.put(PaymentMessageHeaders.CORRELATION_ID, correlationIdToUse);
        headers.put(PaymentMessageHeaders.TRACE_ID, traceIdToUse);

        OutboxMessage outboxMessage = OutboxMessage.builder()
                .messageId(UUID.randomUUID())
                .aggregateId(paymentId)
                .aggregateType("Payment")
                .topic("payment.events")
                .messageKey(orderId.toString())
                .messageType(type.value())
                .correlationId(UUID.fromString(correlationIdToUse))
                .causationId(causationIdToUse)
                .traceId(traceIdToUse)
                .payload(OBJECT_MAPPER.valueToTree(payload))
                .headers(OBJECT_MAPPER.valueToTree(headers))
                .status("PENDING")
                .build();

        outboxRepository.save(outboxMessage);
        log.info("Outbox message {} of type {} enqueued for payment {} (order {}, topic {})",
                outboxMessage.getMessageId(), type.value(), paymentId, orderId, outboxMessage.getTopic());
    }}
package com.rally.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rally.common.exceptions.domain.payment.InvalidPaymentStateException;
import com.rally.common.exceptions.shared.BadRequestException;
import com.rally.common.exceptions.shared.ValidationException;
import com.rally.payment.config.StripeProperties;
import com.rally.payment.enums.PaymentStatus;
import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import com.rally.payment.messaging.inbox.InboxMessage;
import com.rally.payment.metrics.PaymentMetrics;
import com.rally.payment.model.Payment;
import com.rally.payment.model.PaymentMethod;
import com.rally.payment.model.PaymentMethodCard;
import com.rally.payment.repository.InboxJpaRepository;
import com.rally.payment.repository.PaymentJpaRepository;
import com.rally.payment.repository.PaymentMethodJpaRepository;
import com.rally.payment.stripe.StripeMetadata;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import com.stripe.net.Webhook;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import io.micrometer.tracing.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rally.payment.relay.OutboxRelay.randomValidSpanId;

@Slf4j
@Service
public class StripeWebhookService {
    private static final String EVENT_PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String EVENT_PAYMENT_INTENT_CANCELED = "payment_intent.canceled";
    private static final String EVENT_PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";
    private static final String EVENT_SETUP_INTENT_SUCCEEDED = "setup_intent.succeeded";

    private static final String PAYMENT_METHOD_TYPE_CARD = "card";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern OTLP_TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final InboxJpaRepository inboxJpaRepository;
    private final PaymentJpaRepository paymentRepository;
    private final PaymentMethodJpaRepository paymentMethodRepository;
    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;
    private final PaymentMetrics metrics;
    private  final Tracer tracer;
    private final BaggageManager baggageManager;

    public StripeWebhookService(
            InboxJpaRepository inboxJpaRepository,
            PaymentJpaRepository paymentRepository,
            PaymentMethodJpaRepository paymentMethodRepository,
            StripeClient stripeClient,
            StripeProperties stripeProperties,
            PaymentMetrics metrics, Tracer tracer, BaggageManager baggageManager
    ) {
        this.inboxJpaRepository = inboxJpaRepository;
        this.paymentRepository = paymentRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.stripeClient = stripeClient;
        this.stripeProperties = stripeProperties;
        this.metrics = metrics;
        this.tracer = tracer;
        this.baggageManager = baggageManager;
    }

    public Event verifyStripeEvent(String payload, String signature) {
        try {
            return Webhook.constructEvent(payload, signature, stripeProperties.getWebhookSecret());
        } catch (StripeException e) {
            log.warn("Rejected Stripe webhook with invalid signature",e);
            throw new BadRequestException("Invalid Stripe webhook signature");
        }
    }

    @Transactional
    public void processStripeEvent(Event event) {

        String corrId = readMetadata(event, StripeMetadata.CORRELATION_ID);
        String traceId = readMetadata(event, StripeMetadata.TRACE_ID);
        Span span = reParentToStoredTrace(traceId);
        Timer.Sample sample = metrics.startWebhookProcessing();

        try (Tracer.SpanInScope scope = span != null ? tracer.withSpan(span) : null;
             BaggageInScope bg = corrId != null
                     ? baggageManager.createBaggageInScope(PaymentMessageHeaders.CORRELATION_ID, corrId) : null) {
            if (corrId != null) MDC.put(PaymentMessageHeaders.CORRELATION_ID, corrId);
            metrics.recordWebhookReceived(event.getType());
            log.info("Stripe webhook received: eventType={}, eventId={}", event.getType(), event.getId());
            processStripeEventInternal(event, corrId, traceId);
        } finally {
            if (corrId != null) MDC.remove(PaymentMessageHeaders.CORRELATION_ID);
            if (span != null) span.end();
            metrics.stopWebhookProcessing(sample);
        }
    }


    private void processStripeEventInternal(Event event, String corrId, String traceId) {
        String messageId = event.getId();
        InboxMessage inboxMessage = InboxMessage.builder()
                .messageId(messageId)
                .topic("stripe.webhook")
                .messageType(event.getType())
                .correlationId(corrId != null ? parseUuid(corrId) : null)
                .traceId(traceId)
                .payload(OBJECT_MAPPER.valueToTree(event.getData().getObject()))
                .status("RECEIVED")
                .build();

        try {
            inboxJpaRepository.save(inboxMessage);
        } catch (DataIntegrityViolationException ex) {
            log.info("Skipping duplicate Stripe webhook {}", messageId);
            return;
        }

        applyWebhookOutcome(event);

        inboxMessage.setStatus("PROCESSED");
        inboxMessage.setProcessedAt(Instant.now());
        inboxJpaRepository.save(inboxMessage);

        log.info("Stripe webhook processed: eventId={}, eventType={}", messageId, event.getType());
    }

    private void applyWebhookOutcome(Event event) {
        if (event.getType().equals(EVENT_SETUP_INTENT_SUCCEEDED)) {
            try {
                applySetupIntent(event);
            } catch (Exception ex) {
                log.warn("Error while handling setup_intent.succeeded for event {}", event.getId(), ex);
            }
            return;
        }

        String intentId = readIntentId(event);
        if (intentId == null) {
            log.debug("Stripe webhook {} has no payment intent id; ignoring", event.getId());
            return;
        }

        Optional<Payment> byIntent = paymentRepository.findByPaymentIntentId(intentId);
        if (byIntent.isEmpty()) {
            log.debug("Stripe webhook {} references unknown payment intent {}; ignoring", event.getId(), intentId);
            return;
        }

        Payment payment = byIntent.get();
        switch (event.getType()) {
            case EVENT_PAYMENT_INTENT_SUCCEEDED -> applySucceeded(payment, intentId);
            case EVENT_PAYMENT_INTENT_CANCELED -> applyCanceled(payment);
            case EVENT_PAYMENT_INTENT_PAYMENT_FAILED -> applyFailed(payment, intentId, readLastPaymentError(event));
            default -> log.debug("Stripe webhook {} of type {} is not mapped; safe ignore", event.getId(), event.getType());
        }
    }

    private void applySetupIntent(Event event) throws StripeException {
        SetupIntent setupIntent = deserializeSetupIntent(event);
        if (setupIntent == null) {
            throw new IllegalStateException("Failed to deserialize SetupIntent event payload.");
        }

        com.stripe.model.PaymentMethod stripePaymentMethod =
                stripeClient.paymentMethods().retrieve(setupIntent.getPaymentMethod());

        com.stripe.model.PaymentMethod.Card card = stripePaymentMethod.getCard();
        if (card == null) {
            throw new ValidationException("Payment method is not a card.");
        }

        String userIdRaw = setupIntent.getMetadata() != null
                ? setupIntent.getMetadata().get(StripeMetadata.USER_ID)
                : null;
        if (userIdRaw == null) {
            throw new IllegalStateException("USER_ID metadata key is missing on PaymentMethod.");
        }

        String cardFingerprint = card.getFingerprint();

        if (paymentMethodRepository.findByUserIdAndCardFingerprint(UUID.fromString(userIdRaw), cardFingerprint).isPresent()) {
            return;
        }

        PaymentMethodCard cardDetails = PaymentMethodCard.builder()
                .cardBrand(card.getBrand())
                .cardLast4(card.getLast4())
                .cardExpMonth(card.getExpMonth() != null ? String.valueOf(card.getExpMonth()) : null)
                .cardExpYear(card.getExpYear() != null ? String.valueOf(card.getExpYear()) : null)
                .build();

        PaymentMethod paymentMethod = PaymentMethod.builder()
                .id(UUID.randomUUID())
                .paymentMethodCard(cardDetails)
                .cardFingerprint(cardFingerprint)
                .userId(UUID.fromString(userIdRaw))
                .token(stripePaymentMethod.getId())
                .type(PAYMENT_METHOD_TYPE_CARD)
                .build();

        paymentMethodRepository.save(paymentMethod);
    }

    private SetupIntent deserializeSetupIntent(Event event) throws EventDataObjectDeserializationException {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject().isPresent()
                ? (SetupIntent) deserializer.getObject().get()
                : (SetupIntent) deserializer.deserializeUnsafe();
    }

    private void applySucceeded(Payment payment, String intentId) {
        try {
            if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
                payment.capture();
                paymentRepository.save(payment);
            } else if (payment.getStatus() == PaymentStatus.PENDING
                    || payment.getStatus() == PaymentStatus.REQUIRES_ACTION) {
                payment.charge(intentId);
                paymentRepository.save(payment);
            } else {
                log.debug("Webhook payment_intent.succeeded is a no-op for payment {} in state {}",
                        payment.getId(), payment.getStatus());
            }
        } catch (InvalidPaymentStateException e) {
            log.debug("Webhook payment_intent.succeeded safely skipped for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    private void applyCanceled(Payment payment) {
        if (payment.getStatus() == PaymentStatus.VOIDED) {
            log.debug("Webhook payment_intent.canceled is a no-op for already-voided payment {}", payment.getId());
            return;
        }
        try {
            payment.voidPayment("webhook: payment_intent.canceled");
            paymentRepository.save(payment);
        } catch (InvalidPaymentStateException e) {
            log.debug("Webhook payment_intent.canceled safely skipped for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    private void applyFailed(Payment payment, String intentId, String reason) {
        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.debug("Webhook payment_intent.payment_failed is a no-op for already-failed payment {}", payment.getId());
            return;
        }
        try {
            payment.fail(reason, payment.getPaymentMethodId(), intentId);
            paymentRepository.save(payment);
        } catch (InvalidPaymentStateException e) {
            log.debug("Webhook payment_intent.payment_failed safely skipped for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    private String readIntentId(Event event) {
        JsonNode object = readEventObject(event);
        return object != null && object.has("id") ? object.path("id").asText(null) : null;
    }

    private String readLastPaymentError(Event event) {
        JsonNode object = readEventObject(event);
        if (object != null && object.hasNonNull("last_payment_error")) {
            String message = object.path("last_payment_error").path("message").asText(null);
            if (message != null) {
                return message;
            }
        }
        return "webhook: payment_intent.payment_failed";
    }

    private JsonNode readEventObject(Event event) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(event.toJson());
            return root.path("data").path("object");
        } catch (Exception e) {
            log.warn("Could not read Stripe webhook data for event {}: {}", event.getId(), e.getMessage());
            return null;
        }
    }

    private String readMetadata(Event event, String key) {
        JsonNode object = readEventObject(event);
        return object != null && object.path("metadata").hasNonNull(key)
                ? object.path("metadata").get(key).asText(null)
                : null;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return UUID.fromString(value);
    }

    private Span reParentToStoredTrace(String traceId) {
        if (traceId == null || !OTLP_TRACE_ID_PATTERN.matcher(traceId).matches()) return null;
        return tracer.spanBuilder().name("process-stripe-webhook")
                .setParent(tracer.traceContextBuilder()
                        .traceId(traceId).spanId(randomValidSpanId()).sampled(true).build())
                .start();
    }

}
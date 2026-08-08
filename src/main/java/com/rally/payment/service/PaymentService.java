package com.rally.payment.service;

import com.rally.payment.api.dto.AuthorizePaymentRequest;
import com.rally.payment.api.dto.CreatePaymentRequest;
import com.rally.payment.api.dto.FailPaymentRequest;
import com.rally.payment.api.dto.PaymentResponse;
import com.rally.payment.api.dto.VoidPaymentRequest;
import com.rally.payment.config.StripeProperties;
import com.rally.payment.model.PaymentMethodCard;
import com.rally.payment.stripe.StripeMetadata;
import com.rally.payment.exception.PaymentNotFoundException;
import com.rally.payment.enums.PaymentStatus;
import com.rally.payment.messaging.inbox.InboxMessage;
import com.rally.payment.messaging.contract.PaymentInitiationRequested;
import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import com.rally.payment.messaging.contract.PaymentMessageType;
import com.rally.payment.messaging.contract.PaymentSettlementRequested;
import com.rally.payment.messaging.contract.PaymentTimeoutRequested;
import com.rally.payment.model.Payment;
import com.rally.payment.model.PaymentMethod;
import com.rally.payment.messaging.outbox.OutboxMessage;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.payment.repository.InboxJpaRepository;
import com.rally.payment.repository.OutboxJpaRepository;
import com.rally.payment.repository.PaymentJpaRepository;
import com.rally.payment.repository.PaymentMethodJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rally.common.exceptions.domain.payment.InvalidPaymentStateException;
import com.stripe.StripeClient;
import com.stripe.exception.*;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentService {


    private static final String STRIPE_STATUS_SUCCEEDED = "succeeded";
    private static final String STRIPE_STATUS_CANCELED = "canceled";
    private static final String STRIPE_STATUS_REQUIRES_CAPTURE = "requires_capture";
    private static final String STRIPE_STATUS_REQUIRES_ACTION = "requires_action";
    private static final String STRIPE_STATUS_REQUIRES_PAYMENT_METHOD = "requires_payment_method";


    private static final String EVENT_PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String EVENT_PAYMENT_INTENT_CANCELED = "payment_intent.canceled";
    private static final String EVENT_PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";
    private static final String EVENT_SETUP_INTENT_SUCCEEDED = "setup_intent.succeeded";

    private static final String PAYMENT_METHOD_TYPE_CARD = "card";

    private final PaymentJpaRepository paymentRepository;
    private final OutboxJpaRepository outboxJpaRepository;
    private final InboxJpaRepository inboxJpaRepository;
    private final PaymentMethodJpaRepository paymentMethodRepository;
    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;
    private final EntityManager entityManager;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public PaymentService(
            PaymentJpaRepository paymentRepository,
            OutboxJpaRepository outboxJpaRepository,
            InboxJpaRepository inboxJpaRepository,
            PaymentMethodJpaRepository paymentMethodRepository,
            StripeClient stripeClient,
            StripeProperties stripeProperties,
            EntityManager entityManager
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxJpaRepository = outboxJpaRepository;
        this.inboxJpaRepository = inboxJpaRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.stripeClient = stripeClient;
        this.stripeProperties = stripeProperties;
        this.entityManager = entityManager;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        Payment payment = Payment.initialize(
                UUID.randomUUID(),
                request.paymentMethodId(),
                request.userId(),
                request.orderId(),
                request.amount()
        );

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        return PaymentResponse.from(loadPaymentOrThrow(paymentId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForUser(UUID userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForOrder(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(payment -> List.of(PaymentResponse.from(payment)))
                .orElseGet(List::of);
    }

    @Transactional
    public PaymentResponse authorizePayment(UUID paymentId, AuthorizePaymentRequest request) {
        Payment payment = loadPaymentOrThrow(paymentId);
        payment.authorize(request.paymentMethodId(), request.paymentIntentId());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse capturePayment(UUID paymentId) {
        Payment payment = loadPaymentOrThrow(paymentId);
        payment.capture();
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse failPayment(UUID paymentId, FailPaymentRequest request) {
        Payment payment = loadPaymentOrThrow(paymentId);
        payment.fail(request.reason(), request.paymentMethodId(), request.paymentIntentId());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse voidPayment(UUID paymentId, VoidPaymentRequest request) {
        Payment payment = loadPaymentOrThrow(paymentId);
        payment.voidPayment(request.reason());
        return PaymentResponse.from(paymentRepository.save(payment));
    }


    @Transactional
    public PaymentResponse createPaymentFromInitiation(PaymentInitiationRequested request, PaymentMessageType flow) {
        Payment payment = Payment.initialize(
                UUID.randomUUID(),
                request.paymentMethodId(),
                request.userId(),
                request.orderId(),
                request.amount()
        );

        if (paymentRepository.findByOrderId(request.orderId()).isPresent()) {
            log.info("Skipping payment initiation for order {}: order already has a payment", request.orderId());
            return null;
        }

        Payment savedPayment;
        try {
            savedPayment = paymentRepository.save(payment);
            entityManager.flush();
        } catch (DataIntegrityViolationException e) {
            log.info("Skipping payment initiation for order {} (concurrent): order already has a payment", request.orderId());
            return null;
        }

        writeInitializationOutbox(savedPayment);

        switch (flow) {
            case INIT_REQUIRED_CHARGE -> initiateCharge(savedPayment);
            case INIT_REQUIRED_AUTHORIZE -> initiateAuthorization(savedPayment);
            default -> throw new IllegalArgumentException(
                    "Unexpected Payment.MessageType for initiation: " + flow
            );
        }

        return PaymentResponse.from(savedPayment);
    }

    @Transactional
    public void capturePaymentFromSettlement(PaymentSettlementRequested request) {
        Payment payment = loadPaymentForSettlement(request.paymentId(), "capture");
        if (payment == null) {
            return;
        }

        PaymentIntent intent;
        try {
            intent = stripeClient.paymentIntents().capture(
                    payment.getPaymentIntentId(),
                    PaymentIntentCaptureParams.builder().build()
            );
        } catch (CardException e) {
            log.warn("Card error while capturing payment intent for payment {}", payment.getId(), e);
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), e.getMessage());
            return;
        } catch (InvalidRequestException e) {
            log.warn("Invalid Stripe request while capturing payment intent for payment {}", payment.getId(), e);
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), e.getMessage());
            return;
        } catch (ApiConnectionException | ApiException e) {
            log.error("Stripe unavailable while capturing payment intent for payment {}", payment.getId(), e);
            throw new ServiceUnavailableException("Stripe unavailable while capturing payment intent for payment " + payment.getId());
        } catch (StripeException e) {
            log.error("Unexpected Stripe error while capturing payment intent for payment {}", payment.getId(), e);
            throw new ServiceUnavailableException("Unexpected Stripe error while capturing payment intent for payment " + payment.getId());
        }

        if (STRIPE_STATUS_SUCCEEDED.equals(intent.getStatus())) {
            payment.capture();
            writeOutcomeOutbox(payment, PaymentMessageType.CAPTURED);
        } else {
            log.warn("Payment intent not succeeded for capture of payment {}: {}", payment.getId(), intent.getStatus());
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(),
                    "Payment intent not succeeded: " + intent.getStatus());
        }
    }

    @Transactional
    public void voidPaymentFromSettlement(PaymentSettlementRequested request) {
        Payment payment = loadPaymentForSettlement(request.paymentId(), "void");
        if (payment == null) {
            return;
        }

        if (releaseHeldFunds(payment, "void")) {
            payment.voidPayment("void");
            writeOutcomeOutbox(payment, PaymentMessageType.VOIDED);
        }
    }

    @Transactional
    public void resolveTimeout(PaymentTimeoutRequested request) {
        Payment payment = paymentRepository.findByOrderId(request.orderId()).orElse(null);
        if (payment == null) {
            log.info("Skipping timeout resolution for order {}: no payment found", request.orderId());
            return;
        }

        switch (payment.getStatus()) {
            case PENDING, REQUIRES_ACTION ->
                    failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), "timeout");
            case AUTHORIZED -> {
                if (releaseHeldFunds(payment, "timeout")) {
                    payment.voidPayment("timeout");
                    writeOutcomeOutbox(payment, PaymentMessageType.VOIDED);
                }
            }
            default -> log.info(
                    "Skipping timeout resolution for order {}: payment {} is already {}",
                    request.orderId(), payment.getId(), payment.getStatus()
            );
        }
    }

    // =========================================================================
    // Stripe webhook handling
    // (think: a controller action that receives Stripe's HTTP callback,
    // verifies the signature, and applies the event
    // =========================================================================

    public Event verifyStripeEvent(String payload, String signature) {
        try {
            return Webhook.constructEvent(payload, signature, stripeProperties.getWebhookSecret());
        } catch (StripeException e) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature");
        }
    }

    @Transactional
    public void processStripeEvent(Event event) {
        String messageId = event.getId();

        InboxMessage inboxMessage = InboxMessage.builder()
                .messageId(messageId)
                .topic("stripe.webhook")
                .messageType(event.getType())
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
            log.info("Stripe webhook {} has no payment intent id; ignoring", event.getId());
            return;
        }

        Optional<Payment> byIntent = paymentRepository.findByPaymentIntentId(intentId);
        if (byIntent.isEmpty()) {
            log.info("Stripe webhook {} references unknown payment intent {}; ignoring", event.getId(), intentId);
            return;
        }

        Payment payment = byIntent.get();
        switch (event.getType()) {
            case EVENT_PAYMENT_INTENT_SUCCEEDED -> applySucceeded(payment, intentId);
            case EVENT_PAYMENT_INTENT_CANCELED -> applyCanceled(payment);
            case EVENT_PAYMENT_INTENT_PAYMENT_FAILED -> applyFailed(payment, intentId, readLastPaymentError(event));
            default -> log.info("Stripe webhook {} of type {} is not mapped; safe ignore", event.getId(), event.getType());
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
            throw new IllegalArgumentException("Payment method is not a card.");
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
                writeOutcomeOutbox(payment, PaymentMessageType.CAPTURED);
            } else if (payment.getStatus() == PaymentStatus.PENDING
                    || payment.getStatus() == PaymentStatus.REQUIRES_ACTION) {
                payment.charge(intentId);
                writeOutcomeOutbox(payment, PaymentMessageType.CHARGED);
            } else {
                log.info("Webhook payment_intent.succeeded is a no-op for payment {} in state {}",
                        payment.getId(), payment.getStatus());
            }
        } catch (InvalidPaymentStateException e) {
            log.info("Webhook payment_intent.succeeded safely skipped for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    private void applyCanceled(Payment payment) {
        if (payment.getStatus() == PaymentStatus.VOIDED) {
            log.info("Webhook payment_intent.canceled is a no-op for already-voided payment {}", payment.getId());
            return;
        }
        try {
            payment.voidPayment("webhook: payment_intent.canceled");
            writeOutcomeOutbox(payment, PaymentMessageType.VOIDED);
        } catch (InvalidPaymentStateException e) {
            log.info("Webhook payment_intent.canceled safely skipped for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    private void applyFailed(Payment payment, String intentId, String reason) {
        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Webhook payment_intent.payment_failed is a no-op for already-failed payment {}", payment.getId());
            return;
        }
        try {
            payment.fail(reason, payment.getPaymentMethodId(), intentId);
            writeOutcomeOutbox(payment, PaymentMessageType.FAILED);
        } catch (InvalidPaymentStateException e) {
            log.info("Webhook payment_intent.payment_failed safely skipped for payment {}: {}", payment.getId(), e.getMessage());
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

    // =========================================================================
    // Stripe PaymentIntent orchestration helpers
    // =========================================================================

    private void initiateCharge(Payment payment) {
        PaymentMethod paymentMethod = loadPaymentMethod(payment);
        if (paymentMethod == null) {
            return;
        }

        PaymentIntent intent = createAndConfirmIntent(payment, paymentMethod, PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);
        if (intent == null) {
            return;
        }

        log.info("Payment intent created for payment {}: intent {}, status {}",
                payment.getId(), intent.getId(), intent.getStatus());

        applyChargeIntentStatus(payment, paymentMethod, intent);
    }

    private void applyChargeIntentStatus(Payment payment, PaymentMethod paymentMethod, PaymentIntent intent) {
        switch (intent.getStatus()) {
            case STRIPE_STATUS_SUCCEEDED -> {
                payment.charge(intent.getId());
                writeOutcomeOutbox(payment, PaymentMessageType.CHARGED);
            }
            case STRIPE_STATUS_REQUIRES_ACTION ->
                    failPaymentAndPublish(payment, paymentMethod.getId(), intent.getId(), "requires_action: 3DS-SCA not supported in v1");
            case STRIPE_STATUS_REQUIRES_PAYMENT_METHOD ->
                    failPaymentAndPublish(payment, paymentMethod.getId(), intent.getId(), lastPaymentErrorOrDefault(intent, STRIPE_STATUS_REQUIRES_PAYMENT_METHOD));
            default ->
                    failPaymentAndPublish(payment, paymentMethod.getId(), intent.getId(), "Payment intent not succeeded: " + intent.getStatus());
        }
    }

    private void initiateAuthorization(Payment payment) {
        PaymentMethod paymentMethod = loadPaymentMethod(payment);
        if (paymentMethod == null) {
            return;
        }

        PaymentIntent intent = createAndConfirmIntent(payment, paymentMethod, PaymentIntentCreateParams.CaptureMethod.MANUAL);
        if (intent == null) {
            return;
        }

        log.info("Payment intent created for payment {}: intent {}, status {}",
                payment.getId(), intent.getId(), intent.getStatus());

        applyAuthorizeIntentStatus(payment, paymentMethod, intent);
    }

    private void applyAuthorizeIntentStatus(Payment payment, PaymentMethod paymentMethod, PaymentIntent intent) {
        switch (intent.getStatus()) {
            case STRIPE_STATUS_REQUIRES_CAPTURE -> {
                payment.authorize(payment.getPaymentMethodId(), intent.getId());
                writeOutcomeOutbox(payment, PaymentMessageType.AUTHORIZED);
            }
            case STRIPE_STATUS_REQUIRES_ACTION ->
                    failPaymentAndPublish(payment, paymentMethod.getId(), intent.getId(), "requires_action: 3DS-SCA not supported in v1");
            case STRIPE_STATUS_REQUIRES_PAYMENT_METHOD ->
                    failPaymentAndPublish(payment, paymentMethod.getId(), intent.getId(), lastPaymentErrorOrDefault(intent, STRIPE_STATUS_REQUIRES_PAYMENT_METHOD));
            default ->
                    failPaymentAndPublish(payment, paymentMethod.getId(), intent.getId(), "Payment intent not succeeded: " + intent.getStatus());
        }
    }

    private String lastPaymentErrorOrDefault(PaymentIntent intent, String fallback) {
        return intent.getLastPaymentError() != null
                ? intent.getLastPaymentError().getMessage()
                : fallback;
    }

    private PaymentMethod loadPaymentMethod(Payment payment) {
        PaymentMethod paymentMethod = paymentMethodRepository.findById(payment.getPaymentMethodId()).orElse(null);
        if (paymentMethod == null || paymentMethod.getToken() == null || paymentMethod.getToken().isBlank()) {
            log.warn("Missing or blank payment method token for payment {}", payment.getId());
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), null, "missing payment method");
            return null;
        }
        return paymentMethod;
    }

    private PaymentIntent createAndConfirmIntent(Payment payment, PaymentMethod paymentMethod, PaymentIntentCreateParams.CaptureMethod captureMethod) {
        try {
            return stripeClient.paymentIntents().create(
                    buildPaymentIntentParams(payment, paymentMethod.getToken(), captureMethod)
            );
        } catch (CardException e) {
            String intentId = e.getStripeError() != null && e.getStripeError().getPaymentIntent() != null
                    ? e.getStripeError().getPaymentIntent().getId()
                    : null;
            failPaymentAndPublish(payment, paymentMethod.getId(), intentId, e.getMessage());
            return null;
        } catch (InvalidRequestException e) {
            log.warn("Invalid Stripe request while creating payment intent for payment {}", payment.getId(), e);
            failPaymentAndPublish(payment, paymentMethod.getId(), null, e.getMessage());
            return null;
        } catch (ApiConnectionException | ApiException e) {
            log.error("Stripe unavailable while creating payment intent for payment {}", payment.getId(), e);
            throw new ServiceUnavailableException("Stripe unavailable while creating payment intent for payment " + payment.getId());
        } catch (StripeException e) {
            log.error("Unexpected Stripe error while creating payment intent for payment {}", payment.getId(), e);
            throw new ServiceUnavailableException("Unexpected Stripe error while creating payment intent for payment " + payment.getId());
        }
    }

    private boolean releaseHeldFunds(Payment payment, String reason) {
        PaymentIntent intent;
        try {
            intent = stripeClient.paymentIntents().cancel(
                    payment.getPaymentIntentId(),
                    PaymentIntentCancelParams.builder().build()
            );
        } catch (CardException e) {
            log.warn("Card error while canceling payment intent for payment {}", payment.getId(), e);
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), e.getMessage());
            return false;
        } catch (InvalidRequestException e) {
            log.warn("Invalid Stripe request while canceling payment intent for payment {}", payment.getId(), e);
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), e.getMessage());
            return false;
        } catch (ApiConnectionException | ApiException e) {
            log.error("Stripe unavailable while canceling payment intent for payment {}", payment.getId(), e);
            throw new ServiceUnavailableException("Stripe unavailable while canceling payment intent for payment " + payment.getId());
        } catch (StripeException e) {
            log.error("Unexpected Stripe error while canceling payment intent for payment {}", payment.getId(), e);
            throw new ServiceUnavailableException("Unexpected Stripe error while canceling payment intent for payment " + payment.getId());
        }

        if (STRIPE_STATUS_CANCELED.equals(intent.getStatus())) {
            return true;
        }
        log.warn("Payment intent not canceled for payment {}: {}", payment.getId(), intent.getStatus());
        failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(),
                "Payment intent not canceled: " + intent.getStatus());
        return false;
    }

    private PaymentIntentCreateParams buildPaymentIntentParams(Payment payment, String paymentMethodToken, PaymentIntentCreateParams.CaptureMethod captureMethod) {
        return PaymentIntentCreateParams.builder()
                .setAmount(payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .setCurrency(stripeProperties.getCurrency())
                .setCustomer(stripeProperties.getCustomerId())
                .setPaymentMethod(paymentMethodToken)
                .setConfirm(true)
                .setCaptureMethod(captureMethod)
                .putMetadata(StripeMetadata.INTERNAL_PAYMENT_ID, payment.getId().toString())
                .putMetadata(StripeMetadata.ORDER_ID, payment.getOrderId().toString())
                .putMetadata(StripeMetadata.USER_ID, payment.getUserId().toString())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build()
                )
                .build();
    }

    // =========================================================================
    // Small shared helpers
    // =========================================================================

    // Every "call Stripe, it went wrong, mark the payment failed, publish an
    // event" sequence in this class was previously three lines duplicated
    // ~10 times. Centralizing it here doesn't change behavior - it just gives
    // the duplication one name and one place to change.
    private void failPaymentAndPublish(Payment payment, UUID paymentMethodId, String paymentIntentId, String reason) {
        payment.fail(reason, paymentMethodId, paymentIntentId);
        writeOutcomeOutbox(payment, PaymentMessageType.FAILED);
    }

    private Payment loadPaymentOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private Payment loadPaymentForSettlement(UUID paymentId, String flow) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.info("Skipping settlement {} for payment {}: payment not found", flow, paymentId);
            return null;
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            log.info("Skipping settlement {} for payment {}: status is {}, expected AUTHORIZED",
                    flow, payment.getId(), payment.getStatus());
            return null;
        }
        return payment;
    }

    // =========================================================================
    // Outbox writing
    // (Transactional Outbox pattern: instead of publishing to Kafka directly
    // inside the DB transaction - which can't be made atomic with the commit -
    // we write the "message to publish" as a row in the same transaction as
    // the domain change, and a separate poller/relay publishes it afterwards.
    // Closest .NET analogue: MassTransit's or Brighter's outbox pattern.)
    // =========================================================================

    private void writeInitializationOutbox(Payment payment) {
        writeOutbox(payment, PaymentMessageType.INITIALIZED, Map.of(
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "userId", payment.getUserId(),
                "amount", payment.getAmount(),
                "status", payment.getStatus().name()
        ));
    }

    private void writeOutcomeOutbox(Payment payment, PaymentMessageType type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("orderId", payment.getOrderId());
        payload.put("paymentIntentId", payment.getPaymentIntentId());
        payload.put("amount", payment.getAmount());
        writeOutbox(payment, type, payload);
    }

    private void writeOutbox(Payment payment, PaymentMessageType type, Map<String, Object> payload) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(PaymentMessageHeaders.ID, UUID.randomUUID().toString());
        headers.put(PaymentMessageHeaders.TYPE, type.value());
        headers.put(PaymentMessageHeaders.CORRELATION_ID, payment.getOrderId().toString());
        headers.put(PaymentMessageHeaders.CAUSATION_ID, payment.getId().toString());
        headers.put(PaymentMessageHeaders.TRACE_ID, payment.getId().toString());

        OutboxMessage outboxMessage = OutboxMessage.builder()
                .messageId(UUID.randomUUID())
                .aggregateId(payment.getId())
                .aggregateType("Payment")
                .topic("payment.events")
                .messageKey(payment.getOrderId().toString())
                .messageType(type.value())
                .correlationId(payment.getOrderId())
                .causationId(payment.getId().toString())
                .traceId(payment.getId().toString())
                .payload(toJsonNode(payload))
                .headers(toJsonNode(headers))
                .status("PENDING")
                .build();

        outboxJpaRepository.save(outboxMessage);
    }

    private JsonNode toJsonNode(Object value) {
        return OBJECT_MAPPER.valueToTree(value);
    }
}
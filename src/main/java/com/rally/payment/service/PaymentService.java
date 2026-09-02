package com.rally.payment.service;

import com.rally.common.exceptions.domain.payment.InvalidPaymentStateException;
import com.rally.common.exceptions.domain.payment.PaymentNotFoundException;
import com.rally.payment.api.dto.AuthorizePaymentRequest;
import com.rally.payment.api.dto.CreatePaymentRequest;
import com.rally.payment.api.dto.FailPaymentRequest;
import com.rally.payment.api.dto.PaymentResponse;
import com.rally.payment.api.dto.VoidPaymentRequest;
import com.rally.payment.enums.PaymentStatus;
import com.rally.payment.messaging.contract.PaymentInitiationRequested;
import com.rally.payment.messaging.contract.PaymentMessageType;
import com.rally.payment.messaging.contract.PaymentSettlementRequested;
import com.rally.payment.messaging.contract.PaymentTimeoutRequested;
import com.rally.payment.model.Payment;
import com.rally.payment.model.PaymentMethod;
import com.rally.payment.repository.PaymentJpaRepository;
import com.rally.payment.repository.PaymentMethodJpaRepository;
import com.rally.payment.stripe.StripePaymentGateway;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.persistence.EntityManager;
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

    private final PaymentJpaRepository paymentRepository;
    private final PaymentMethodJpaRepository paymentMethodRepository;
    private final StripePaymentGateway stripePaymentGateway;
    private final EntityManager entityManager;

    public PaymentService(
            PaymentJpaRepository paymentRepository,
            PaymentMethodJpaRepository paymentMethodRepository,
            StripePaymentGateway stripePaymentGateway,
            EntityManager entityManager
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.stripePaymentGateway = stripePaymentGateway;
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

    @Transactional
    public PaymentResponse authorizePayment(UUID paymentId, AuthorizePaymentRequest request) {
        Payment payment = loadPaymentOrThrow(paymentId);
        payment.authorize(request.paymentMethodId(), request.paymentIntentId());
        payment.suppressDomainEvents();
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse capturePayment(UUID paymentId) {
        Payment payment = loadPaymentOrThrow(paymentId);
        payment.capture();
        payment.suppressDomainEvents();
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse failPayment(UUID paymentId, FailPaymentRequest request) {
        Payment payment = loadPaymentOrThrow(paymentId);
        payment.fail(request.reason(), request.paymentMethodId(), request.paymentIntentId());
        payment.suppressDomainEvents();
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse voidPayment(UUID paymentId, VoidPaymentRequest request) {
        Payment payment = loadPaymentOrThrow(paymentId);
        payment.voidPayment(request.reason());
        payment.suppressDomainEvents();
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
            intent = stripePaymentGateway.capture(payment.getPaymentIntentId(), payment.getId());
        } catch (CardException e) {
           // log.warn("Card error while capturing payment intent for payment {}", payment.getId(), e);
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), e.getMessage(), stripePaymentGateway.errorCodeOf(e));
            return;
        } catch (InvalidRequestException e) {
            log.warn("Invalid Stripe request while capturing payment intent for payment {}", payment.getId());
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), e.getMessage(), stripePaymentGateway.errorCodeOf(e));
            return;
        }

        if (STRIPE_STATUS_SUCCEEDED.equals(intent.getStatus())) {
            payment.capture();
            paymentRepository.save(payment);
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
            paymentRepository.save(payment);
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
                    paymentRepository.save(payment);
                }
            }
            default -> log.info(
                    "Skipping timeout resolution for order {}: payment {} is already {}",
                    request.orderId(), payment.getId(), payment.getStatus()
            );
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
                paymentRepository.save(payment);
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
                paymentRepository.save(payment);
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
            return stripePaymentGateway.createAndConfirm(payment, paymentMethod.getToken(), captureMethod);
        } catch (CardException e) {
            failPaymentAndPublish(payment, paymentMethod.getId(), stripePaymentGateway.intentIdOf(e), e.getMessage(), stripePaymentGateway.errorCodeOf(e));
            return null;
        } catch (InvalidRequestException e) {
            log.warn("Invalid Stripe request while creating payment intent for payment {}", payment.getId());
            failPaymentAndPublish(payment, paymentMethod.getId(), null, e.getMessage(), stripePaymentGateway.errorCodeOf(e));
            return null;
        }
    }

    private boolean releaseHeldFunds(Payment payment, String reason) {
        PaymentIntent intent;
        try {
            intent = stripePaymentGateway.cancel(payment.getPaymentIntentId(), payment.getId());
        } catch (CardException e) {
           // log.warn("Card error while canceling payment intent for payment {}", payment.getId(), e);
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), e.getMessage(), stripePaymentGateway.errorCodeOf(e));
            return false;
        } catch (InvalidRequestException e) {
            log.warn("Invalid Stripe request while canceling payment intent for payment {}", payment.getId());
            failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(), e.getMessage(), stripePaymentGateway.errorCodeOf(e));
            return false;
        }

        if (STRIPE_STATUS_CANCELED.equals(intent.getStatus())) {
            return true;
        }
        log.warn("Payment intent not canceled for payment {}: {}", payment.getId(), intent.getStatus());
        failPaymentAndPublish(payment, payment.getPaymentMethodId(), payment.getPaymentIntentId(),
                "Payment intent not canceled: " + intent.getStatus());
        return false;
    }

    private void failPaymentAndPublish(Payment payment, UUID paymentMethodId, String paymentIntentId, String reason) {
        failPaymentAndPublish(payment, paymentMethodId, paymentIntentId, reason, null);
    }

    private void failPaymentAndPublish(Payment payment, UUID paymentMethodId, String paymentIntentId, String reason, String errorCode) {
        payment.fail(reason, paymentMethodId, paymentIntentId, errorCode);
        paymentRepository.save(payment);
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
}

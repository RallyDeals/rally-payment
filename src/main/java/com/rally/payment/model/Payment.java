package com.rally.payment.model;

import com.rally.common.exceptions.domain.payment.InvalidPaymentStateException;
import com.rally.payment.enums.PaymentStatus;
import com.rally.payment.events.PaymentAuthorized;
import com.rally.payment.events.PaymentCaptured;
import com.rally.payment.events.PaymentCharged;
import com.rally.payment.events.PaymentFailed;
import com.rally.payment.events.PaymentInitialized;
import com.rally.payment.events.PaymentRequiresAction;
import com.rally.payment.events.PaymentVoided;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AbstractAggregateRoot;

@Slf4j
@Getter
@Setter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends AbstractAggregateRoot<Payment> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "charged_at")
    private Instant chargedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Version
    @Column(name = "version")
    private Long version;

    public static Payment initialize(UUID id, UUID paymentMethodId, UUID userId, UUID orderId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        Payment payment = new Payment();
        payment.id = id;
        payment.paymentMethodId = paymentMethodId;
        payment.userId = userId;
        payment.orderId = orderId;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = Instant.now();
        payment.registerEvent(new PaymentInitialized(id, orderId, amount));

        log.info("Payment {} initialized for order {} (user {}, amount {}) in state PENDING",
                id, orderId, userId, amount);

        return payment;
    }

    public void linkToStripeCustomer(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
        log.debug("Payment {} linked to Stripe customer {}", id, stripeCustomerId);
    }

    public void suppressDomainEvents() {
        clearDomainEvents();
    }

    public void linkToStripePaymentIntent(String stripePaymentIntentId) {
        this.paymentIntentId = stripePaymentIntentId;
        log.debug("Payment {} linked to Stripe payment intent {}", id, stripePaymentIntentId);
    }

    public void charge(String paymentIntentId) {
        if (status == PaymentStatus.CHARGED) {
            return;
        }

        if (status != PaymentStatus.PENDING && status != PaymentStatus.REQUIRES_ACTION) {
            throw new InvalidPaymentStateException(this.id, this.status.toString(), "charge");
        }

        PaymentStatus from = status;
        status = PaymentStatus.CHARGED;
        this.paymentIntentId = paymentIntentId;
        this.failureReason = null;
        this.chargedAt = Instant.now();
        registerEvent(new PaymentCharged(id, paymentIntentId, orderId, amount));
        log.info("Payment {} transitioned {} -> CHARGED (intent {}, order {})", id, from, paymentIntentId, orderId);
    }

    public void capture() {
        if (status == PaymentStatus.CAPTURED) {
            return;
        }

        if (status != PaymentStatus.AUTHORIZED) {
            throw new InvalidPaymentStateException(this.id, this.status.toString(), "capture");
        }

        PaymentStatus from = status;
        status = PaymentStatus.CAPTURED;
        this.capturedAt = Instant.now();
        registerEvent(new PaymentCaptured(id, paymentIntentId, orderId, amount));
        log.info("Payment {} transitioned {} -> CAPTURED (intent {}, order {})", id, from, paymentIntentId, orderId);
    }

    public void authorize(UUID paymentMethodId, String paymentIntentId) {
        if (status == PaymentStatus.AUTHORIZED) {
            return;
        }

        if (status == PaymentStatus.CHARGED || status == PaymentStatus.CAPTURED || status == PaymentStatus.VOIDED) {
            throw new InvalidPaymentStateException(this.id, this.status.toString(), "authorize");
        }

        PaymentStatus from = status;
        status = PaymentStatus.AUTHORIZED;
        this.paymentMethodId = paymentMethodId;
        this.paymentIntentId = paymentIntentId;
        this.authorizedAt = Instant.now();
        registerEvent(new PaymentAuthorized(id, paymentIntentId, orderId, amount));
        log.info("Payment {} transitioned {} -> AUTHORIZED (intent {}, order {})", id, from, paymentIntentId, orderId);
    }

    public void fail(String reason, UUID paymentMethodId, String paymentIntentId) {
        fail(reason, paymentMethodId, paymentIntentId, null);
    }

    public void fail(String reason, UUID paymentMethodId, String paymentIntentId, String errorCode) {
        if (status == PaymentStatus.CHARGED || status == PaymentStatus.CAPTURED || status == PaymentStatus.VOIDED) {
            throw new InvalidPaymentStateException(this.id, this.status.toString(), "fail");
        }

        PaymentStatus from = status;
        status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.paymentMethodId = paymentMethodId;
        this.paymentIntentId = paymentIntentId;
        this.failedAt = Instant.now();
        registerEvent(new PaymentFailed(id, paymentIntentId, orderId, amount, reason, errorCode));
        log.info("Payment {} transitioned {} -> FAILED (intent {}, reason: {}, errorCode: {})",
                id, from, paymentIntentId, reason, errorCode);
    }

    public void voidPayment(String reason) {
        if (status == PaymentStatus.CHARGED || status == PaymentStatus.CAPTURED) {
            throw new InvalidPaymentStateException(this.id, this.status.toString(), "voidPayment");
        }

        PaymentStatus from = status;
        status = PaymentStatus.VOIDED;
        this.voidedAt = Instant.now();
        this.failureReason = reason;
        registerEvent(new PaymentVoided(id, paymentIntentId, orderId, amount));
        log.info("Payment {} transitioned {} -> VOIDED (intent {}, reason: {})", id, from, paymentIntentId, reason);
    }

    public void requireAdditionalAction(UUID paymentMethodId, String paymentIntentId) {
        if (status == PaymentStatus.CHARGED || status == PaymentStatus.CAPTURED || status == PaymentStatus.VOIDED) {
            throw new InvalidPaymentStateException(this.id, this.status.toString(), "requireAdditionalAction");
        }

        PaymentStatus from = status;
        status = PaymentStatus.REQUIRES_ACTION;
        this.paymentMethodId = paymentMethodId;
        this.paymentIntentId = paymentIntentId;
        registerEvent(new PaymentRequiresAction(id, paymentIntentId, orderId));
        log.info("Payment {} transitioned {} -> REQUIRES_ACTION (intent {}, order {})",
                id, from, paymentIntentId, orderId);
    }
}

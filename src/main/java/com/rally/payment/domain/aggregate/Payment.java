package com.rally.payment.domain.aggregate;

import com.rally.common.exceptions.domain.payment.InvalidPaymentStateException;
import com.rally.payment.domain.aggregate.events.*;
import com.rally.payment.domain.enums.PaymentStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "payments")
public class Payment extends AbstractAggregateRoot<Payment> {

    @Id
    private UUID id;

    private String orderId;
    private BigDecimal amount;
    private String userId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String failureReason;
    private Instant createdAt;
    private Instant paidAt;
    private Instant authorizedAt;
    private Instant failedAt;
    private Instant voidedAt;
    private UUID paymentMethodId;
    private String paymentIntentId;
    private String stripeCustomerId;

    protected Payment() {}

    public static Payment initialize(UUID id, UUID paymentMethodId, String userId, String orderId, BigDecimal amount) {
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

        return payment;
    }

    public void linkToStripeCustomer(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public void linkToStripePaymentIntent(String stripePaymentIntentId) {
        this.paymentIntentId = stripePaymentIntentId;
    }

    public void charge(String paymentIntentId) {
        if (status == PaymentStatus.SUCCEEDED) return;

        status = PaymentStatus.SUCCEEDED;
        this.paymentIntentId = paymentIntentId;
        this.failureReason = null;
        this.paidAt = Instant.now();

        registerEvent(new PaymentCharged(this.id, this.paymentIntentId, this.orderId, this.amount));
    }

    public void capture() {
        if (status == PaymentStatus.SUCCEEDED) return;

        if (status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("This Payment must be authorized before capture.");
        }

        status = PaymentStatus.SUCCEEDED;
        this.paidAt = Instant.now();

        registerEvent(new PaymentCharged(this.id, this.paymentIntentId, this.orderId, this.amount));
    }

    public void authorize(UUID paymentMethodId, String paymentIntentId) {
        if (status == PaymentStatus.AUTHORIZED) return;

        if (status == PaymentStatus.SUCCEEDED) {
            throw new InvalidPaymentStateException(this.id,this.status.toString(),"authorize");
        }

        status = PaymentStatus.AUTHORIZED;
        this.paymentMethodId = paymentMethodId;
        this.paymentIntentId = paymentIntentId;
        this.authorizedAt = Instant.now();

        registerEvent(new PaymentAuthorized(this.id, this.paymentIntentId, this.orderId, this.amount));
    }

    public void fail(String reason, UUID paymentMethodId, String paymentIntentId) {
        if (status == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Cannot fail a completed payment.");
        }

        status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.paymentMethodId = paymentMethodId;
        this.paymentIntentId = paymentIntentId;
        this.failedAt = Instant.now();

        registerEvent(new PaymentFailed(this.id, this.paymentIntentId, this.orderId, reason));
    }

    public void voidPayment(String reason) {
        if (status == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Cannot cancel a completed payment.");
        }

        status = PaymentStatus.CANCELED;
        this.voidedAt = Instant.now();
        this.failureReason = reason;

        registerEvent(new PaymentVoided(this.id, this.paymentIntentId, this.orderId, this.amount));
    }

    public void requireAdditionalAction(UUID paymentMethodId, String paymentIntentId) {
        if (status == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Payment already succeeded.");
        }

        status = PaymentStatus.REQUIRES_ACTION;
        this.paymentMethodId = paymentMethodId;
        this.paymentIntentId = paymentIntentId;

        registerEvent(new PaymentRequiresAction(this.id, this.paymentIntentId, this.orderId));
    }

}
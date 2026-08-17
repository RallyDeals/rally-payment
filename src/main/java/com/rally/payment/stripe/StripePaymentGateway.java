package com.rally.payment.stripe;

import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.common.exceptions.shared.ValidationException;
import com.rally.payment.config.StripeProperties;
import com.rally.payment.model.Payment;
import com.rally.payment.service.PaymentProfileService;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodDetachParams;
import com.stripe.param.SetupIntentCreateParams;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StripePaymentGateway {

    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;
    private final PaymentProfileService paymentProfileService;

    public StripePaymentGateway(
            StripeClient stripeClient,
            StripeProperties stripeProperties,
            PaymentProfileService paymentProfileService
    ) {
        this.stripeClient = stripeClient;
        this.stripeProperties = stripeProperties;
        this.paymentProfileService = paymentProfileService;
    }

    public PaymentIntent createAndConfirm(Payment payment, String paymentMethodToken, PaymentIntentCreateParams.CaptureMethod captureMethod) throws CardException, InvalidRequestException {
        try {
            return stripeClient.paymentIntents().create(
                    buildPaymentIntentParams(payment, paymentMethodToken, captureMethod)
            );
        } catch (CardException | InvalidRequestException e) {
            throw e;
        } catch (ApiConnectionException | ApiException e) {
            log.error("Stripe unavailable while creating payment intent for payment {}", payment.getId(), e);
            throw new ServiceUnavailableException("Stripe unavailable while creating payment intent for payment " + payment.getId());
        } catch (StripeException e) {
            log.error("Unexpected Stripe error while creating payment intent for payment {}", payment.getId(), e);
            throw new ServiceUnavailableException("Unexpected Stripe error while creating payment intent for payment " + payment.getId());
        }
    }

    public PaymentIntent capture(String paymentIntentId, UUID paymentId) throws CardException, InvalidRequestException {
        try {
            return stripeClient.paymentIntents().capture(
                    paymentIntentId,
                    PaymentIntentCaptureParams.builder().build()
            );
        } catch (CardException | InvalidRequestException e) {
            throw e;
        } catch (ApiConnectionException | ApiException e) {
            log.error("Stripe unavailable while capturing payment intent for payment {}", paymentId, e);
            throw new ServiceUnavailableException("Stripe unavailable while capturing payment intent for payment " + paymentId);
        } catch (StripeException e) {
            log.error("Unexpected Stripe error while capturing payment intent for payment {}", paymentId, e);
            throw new ServiceUnavailableException("Unexpected Stripe error while capturing payment intent for payment " + paymentId);
        }
    }

    public PaymentIntent cancel(String paymentIntentId, UUID paymentId) throws CardException, InvalidRequestException {
        try {
            return stripeClient.paymentIntents().cancel(
                    paymentIntentId,
                    PaymentIntentCancelParams.builder().build()
            );
        } catch (CardException | InvalidRequestException e) {
            throw e;
        } catch (ApiConnectionException | ApiException e) {
            log.error("Stripe unavailable while canceling payment intent for payment {}", paymentId, e);
            throw new ServiceUnavailableException("Stripe unavailable while canceling payment intent for payment " + paymentId);
        } catch (StripeException e) {
            log.error("Unexpected Stripe error while canceling payment intent for payment {}", paymentId, e);
            throw new ServiceUnavailableException("Unexpected Stripe error while canceling payment intent for payment " + paymentId);
        }
    }

    public PaymentIntentCreateParams buildPaymentIntentParams(Payment payment, String paymentMethodToken, PaymentIntentCreateParams.CaptureMethod captureMethod) {
        return PaymentIntentCreateParams.builder()
                .setAmount(payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .setCurrency(stripeProperties.getCurrency())
                .setCustomer(paymentProfileService.getOrCreateStripeCustomer(payment.getUserId()))
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

    public String errorCodeOf(StripeException e) {
        return e.getStripeError() != null ? e.getStripeError().getCode() : null;
    }

    public String intentIdOf(CardException e) {
        return e.getStripeError() != null && e.getStripeError().getPaymentIntent() != null
                ? e.getStripeError().getPaymentIntent().getId()
                : null;
    }

    public SetupIntent createSetupIntent(UUID userId) {
        SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(paymentProfileService.getOrCreateStripeCustomer(userId))
                .setAutomaticPaymentMethods(SetupIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(SetupIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                .putMetadata(StripeMetadata.USER_ID, userId.toString())
                .build();
        try {
            return stripeClient.setupIntents().create(params);
        } catch (StripeException e) {
            log.error("Stripe unavailable while creating setup intent for user {}", userId, e);
            throw new ServiceUnavailableException("Stripe unavailable while creating setup intent for user " + userId);
        }
    }

    public PaymentMethod retrieveCardDetails(String paymentMethodId) {
        try {
            return stripeClient.paymentMethods().retrieve(paymentMethodId);
        } catch (CardException | InvalidRequestException e) {
            throw new ValidationException("paymentMethodId is not a confirmed card");
        } catch (StripeException e) {
            log.error("Stripe unavailable while retrieving payment method {}", paymentMethodId, e);
            throw new ServiceUnavailableException("Stripe unavailable while retrieving payment method " + paymentMethodId);
        }
    }

    public void detachMethod(String paymentMethodId) {
        try {
            stripeClient.paymentMethods().detach(paymentMethodId, PaymentMethodDetachParams.builder().build());
        } catch (CardException | InvalidRequestException e) {
            log.warn("Gateway refused detach for payment method {}; proceeding with local removal", paymentMethodId);
        } catch (StripeException e) {
            log.error("Stripe unavailable while detaching payment method {}", paymentMethodId, e);
            throw new ServiceUnavailableException("Stripe unavailable while detaching payment method " + paymentMethodId);
        }
    }
}
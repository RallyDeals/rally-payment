package com.rally.payment.stripe;

import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.payment.config.StripeProperties;
import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.param.PaymentMethodDetachParams;
import com.stripe.param.SetupIntentCreateParams;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeCardTokenService {

    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;

    public StripeCardTokenService(StripeClient stripeClient, StripeProperties stripeProperties) {
        this.stripeClient = stripeClient;
        this.stripeProperties = stripeProperties;
    }

    public SetupIntent createSetupIntent(UUID userId) {
        SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(stripeProperties.getCustomerId())
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
            throw new IllegalArgumentException("paymentMethodId is not a confirmed card");
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

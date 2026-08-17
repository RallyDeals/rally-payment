package com.rally.payment.service;

import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.payment.model.PaymentProfile;
import com.rally.payment.repository.PaymentProfileJpaRepository;
import com.rally.payment.stripe.StripeMetadata;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class StripeProvisioningService {

    private final PaymentProfileJpaRepository paymentProfileRepository;
    private final StripeClient stripeClient;

    public StripeProvisioningService(
            PaymentProfileJpaRepository paymentProfileRepository,
            StripeClient stripeClient
    ) {
        this.paymentProfileRepository = paymentProfileRepository;
        this.stripeClient = stripeClient;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String provisionStripeCustomer(UUID userId) {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .putMetadata(StripeMetadata.USER_ID, userId.toString())
                .build();
    // Create stripe customer using Stripe API
        Customer customer;
        try {
            customer = stripeClient.customers().create(params);
        } catch (StripeException e) {
            log.error("Stripe unavailable while creating customer for user {}", userId, e);
            throw new ServiceUnavailableException("Stripe unavailable while creating customer for user " + userId);
        }
    // Create a profile (user_Id,stripe_customer_id);
        PaymentProfile profile = PaymentProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .stripeCustomerId(customer.getId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        paymentProfileRepository.save(profile);
        return customer.getId();
    }
}

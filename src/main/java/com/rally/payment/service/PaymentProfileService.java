package com.rally.payment.service;

import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.common.exceptions.shared.ValidationException;
import com.rally.payment.model.PaymentProfile;
import com.rally.payment.repository.PaymentProfileJpaRepository;
import com.rally.payment.stripe.StripeMetadata;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class PaymentProfileService {

    private final PaymentProfileJpaRepository paymentProfileRepository;
    private  final StripeProvisioningService stripeProvisioningService;
    public PaymentProfileService(
            PaymentProfileJpaRepository paymentProfileRepository,
        StripeProvisioningService stripeProvisioningService
    ) {
        this.paymentProfileRepository = paymentProfileRepository;
        this.stripeProvisioningService = stripeProvisioningService;
    }


    @Transactional(readOnly = true)
    public String getOrCreateStripeCustomer(UUID userId) {
        if (userId == null) {
            throw new ValidationException("userId is required");
        }
        return paymentProfileRepository.findByUserId(userId)
            .map(PaymentProfile::getStripeCustomerId)
            .orElseGet(() -> provisionStripeCustomer(userId));
    }

    private String provisionStripeCustomer(UUID userId) {
        try {
       return stripeProvisioningService.provisionStripeCustomer((userId));

        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent provisioning for user {}; reusing existing profile", userId);
            return paymentProfileRepository.findByUserId(userId)
                .map(PaymentProfile::getStripeCustomerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Payment profile could not be provisioned for user " + userId));
        }
    }

}

package com.rally.payment.service;

import com.rally.common.exceptions.shared.NotFoundException;
import com.rally.common.exceptions.shared.ValidationException;
import com.rally.payment.api.dto.CreatePaymentMethodRequest;
import com.rally.payment.api.dto.PaymentMethodResponse;
import com.rally.payment.api.dto.SetupIntentResponse;
import com.rally.payment.model.PaymentMethod;
import com.rally.payment.model.PaymentMethodCard;
import com.rally.payment.repository.PaymentMethodJpaRepository;
import com.rally.payment.stripe.StripePaymentGateway;
import com.stripe.model.SetupIntent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

    public record PaymentMethodCreationResult(PaymentMethodResponse response, boolean duplicated) {
    }

    private final PaymentMethodJpaRepository paymentMethodRepository;
    private final StripePaymentGateway stripePaymentGateway;

    public PaymentMethodService(
        PaymentMethodJpaRepository paymentMethodRepository,
        StripePaymentGateway stripePaymentGateway
    ) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.stripePaymentGateway = stripePaymentGateway;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> listForUser(UUID userId) {
        return paymentMethodRepository.findByUserId(userId).stream()
            .map(PaymentMethodResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse getForUser(UUID userId, UUID methodId) {
        return PaymentMethodResponse.from(loadOwnedMethod(userId, methodId));
    }

    @Transactional
    public SetupIntentResponse startSetupIntent(UUID userId) {
        if (userId == null) {
            throw new ValidationException("userId is required");
        }
        SetupIntent setupIntent = stripePaymentGateway.createSetupIntent(userId);
        boolean requiresAction = "requires_action".equals(setupIntent.getStatus());
        return new SetupIntentResponse(setupIntent.getId(), setupIntent.getClientSecret(), requiresAction);
    }

    @Transactional
    public PaymentMethodCreationResult confirmCreate(UUID userId, CreatePaymentMethodRequest request) {
        if (request == null || request.paymentMethodId() == null || request.paymentMethodId().isBlank()) {
            throw new ValidationException("paymentMethodId is required");
        }

        com.stripe.model.PaymentMethod gatewayMethod =
            stripePaymentGateway.retrieveCardDetails(request.paymentMethodId());
        com.stripe.model.PaymentMethod.Card card = gatewayMethod.getCard();
        String fingerprint = card != null && card.getFingerprint() != null ? card.getFingerprint() : null;
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new ValidationException("paymentMethodId is not a confirmed card");
        }

        Optional<PaymentMethod> duplicate =
            paymentMethodRepository.findByUserIdAndCardFingerprint(userId, fingerprint);
        if (duplicate.isPresent()) {
            PaymentMethod existing = duplicate.get();
            if (request.isDefault() && !existing.isDefault()) {
                existing.setDefault(true);
                paymentMethodRepository.save(existing);
                clearDefaultForUser(userId, existing.getId());
                paymentMethodRepository.flush();
            }
            return new PaymentMethodCreationResult(PaymentMethodResponse.from(existing), true);
        }

        if (request.isDefault()) {
            clearDefaultForUser(userId, null);
            paymentMethodRepository.flush();
        }

        PaymentMethod method = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .type(gatewayMethod.getType() != null && !gatewayMethod.getType().isBlank()
                ? gatewayMethod.getType()
                : "card")
            .token(request.paymentMethodId())
            .isDefault(request.isDefault())
            .cardFingerprint(fingerprint)
            .paymentMethodCard(PaymentMethodCard.builder()
                .cardBrand(card.getBrand())
                .cardLast4(card.getLast4())
                .cardExpMonth(card.getExpMonth() != null ? String.valueOf(card.getExpMonth()) : null)
                .cardExpYear(card.getExpYear() != null ? String.valueOf(card.getExpYear()) : null)
                .build())
            .build();

        return new PaymentMethodCreationResult(PaymentMethodResponse.from(paymentMethodRepository.save(method)), false);
    }

    @Transactional
    public PaymentMethodResponse setDefault(UUID userId, UUID methodId) {
        PaymentMethod method = loadOwnedMethod(userId, methodId);
        if (!method.isDefault()) {
            clearDefaultForUser(userId, methodId);
            method.setDefault(true);
            paymentMethodRepository.save(method);
            paymentMethodRepository.flush();
        }
        return PaymentMethodResponse.from(method);
    }

    @Transactional
    public PaymentMethodResponse clearDefault(UUID userId, UUID methodId) {
        PaymentMethod method = loadOwnedMethod(userId, methodId);
        if (method.isDefault()) {
            method.setDefault(false);
            paymentMethodRepository.save(method);
        }
        return PaymentMethodResponse.from(method);
    }

    @Transactional
    public void removeForUser(UUID userId, UUID methodId) {
        PaymentMethod method = loadOwnedMethod(userId, methodId);
        stripePaymentGateway.detachMethod(method.getToken());
        paymentMethodRepository.delete(method);
    }

    private void clearDefaultForUser(UUID userId, UUID exceptMethodId) {
        paymentMethodRepository.findByUserIdAndIsDefaultTrue(userId)
            .filter(existing -> !existing.getId().equals(exceptMethodId))
            .ifPresent(existing -> {
                existing.setDefault(false);
                paymentMethodRepository.save(existing);
            });
    }

    private PaymentMethod loadOwnedMethod(UUID userId, UUID methodId) {
        PaymentMethod method = paymentMethodRepository.findById(methodId)
            .orElseThrow(() -> new NotFoundException("PaymentMethod", methodId));
        if (!method.getUserId().equals(userId)) {
            throw new NotFoundException("PaymentMethod", methodId);
        }
        return method;
    }
}
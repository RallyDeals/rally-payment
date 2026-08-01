package com.rally.payment.service;

import com.rally.payment.api.dto.CreatePaymentMethodRequest;
import com.rally.payment.api.dto.PaymentMethodResponse;
import com.rally.payment.exception.PaymentMethodNotFoundException;
import com.rally.payment.model.PaymentMethod;
import com.rally.payment.model.PaymentMethodCard;
import com.rally.payment.repository.PaymentMethodJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

    private final PaymentMethodJpaRepository paymentMethodRepository;

    public PaymentMethodService(PaymentMethodJpaRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Transactional
    public PaymentMethodResponse createPaymentMethod(CreatePaymentMethodRequest request) {
        if (request.isDefault()) {
            paymentMethodRepository.findByUserId(request.userId()).stream()
                .filter(PaymentMethod::isDefault)
                .forEach(existing -> {
                    existing.setDefault(false);
                    paymentMethodRepository.save(existing);
                });
            paymentMethodRepository.flush();
        }

        PaymentMethod paymentMethod = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .userId(request.userId())
            .type(request.type())
            .token(request.token())
            .isDefault(request.isDefault())
            .paymentMethodCard(PaymentMethodCard.builder()
                .cardBrand(request.cardBrand())
                .cardLast4(request.cardLast4())
                .cardExpMonth(request.cardExpMonth())
                .cardExpYear(request.cardExpYear())
                .build())
            .build();

        return PaymentMethodResponse.from(paymentMethodRepository.save(paymentMethod));
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse getPaymentMethod(UUID paymentMethodId) {
        return PaymentMethodResponse.from(loadPaymentMethod(paymentMethodId));
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getPaymentMethodsForUser(UUID userId) {
        return paymentMethodRepository.findByUserId(userId).stream()
            .map(PaymentMethodResponse::from)
            .toList();
    }

    private PaymentMethod loadPaymentMethod(UUID paymentMethodId) {
        return paymentMethodRepository.findById(paymentMethodId)
            .orElseThrow(() -> new PaymentMethodNotFoundException(paymentMethodId));
    }
}

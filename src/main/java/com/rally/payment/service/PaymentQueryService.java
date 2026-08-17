package com.rally.payment.service;

import com.rally.common.exceptions.domain.payment.PaymentNotFoundException;
import com.rally.payment.api.dto.PaymentResponse;
import com.rally.payment.model.Payment;
import com.rally.payment.repository.PaymentJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentQueryService {

    private final PaymentJpaRepository paymentRepository;

    public PaymentQueryService(PaymentJpaRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
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

    private Payment loadPaymentOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
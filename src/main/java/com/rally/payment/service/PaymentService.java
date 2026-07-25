package com.rally.payment.service;

import com.rally.payment.api.dto.AuthorizePaymentRequest;
import com.rally.payment.api.dto.CreatePaymentRequest;
import com.rally.payment.api.dto.FailPaymentRequest;
import com.rally.payment.api.dto.PaymentResponse;
import com.rally.payment.api.dto.VoidPaymentRequest;
import com.rally.payment.exception.PaymentNotFoundException;
import com.rally.payment.model.Payment;
import com.rally.payment.repository.PaymentJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentJpaRepository paymentRepository;

    public PaymentService(PaymentJpaRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
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

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        return PaymentResponse.from(loadPayment(paymentId));
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

    @Transactional
    public PaymentResponse authorizePayment(UUID paymentId, AuthorizePaymentRequest request) {
        Payment payment = loadPayment(paymentId);
        payment.authorize(request.paymentMethodId(), request.paymentIntentId());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse capturePayment(UUID paymentId) {
        Payment payment = loadPayment(paymentId);
        payment.capture();
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse failPayment(UUID paymentId, FailPaymentRequest request) {
        Payment payment = loadPayment(paymentId);
        payment.fail(request.reason(), request.paymentMethodId(), request.paymentIntentId());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse voidPayment(UUID paymentId, VoidPaymentRequest request) {
        Payment payment = loadPayment(paymentId);
        payment.voidPayment(request.reason());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    private Payment loadPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}

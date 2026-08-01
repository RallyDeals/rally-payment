package com.rally.payment.service;

import com.rally.payment.api.dto.AuthorizePaymentRequest;
import com.rally.payment.api.dto.CreatePaymentRequest;
import com.rally.payment.api.dto.FailPaymentRequest;
import com.rally.payment.api.dto.PaymentResponse;
import com.rally.payment.api.dto.VoidPaymentRequest;
import com.rally.payment.messaging.contract.PaymentInitiationRequested;
import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import com.rally.payment.messaging.contract.PaymentMessageType;
import com.rally.payment.exception.PaymentNotFoundException;
import com.rally.payment.model.Payment;
import com.rally.payment.messaging.outbox.OutboxMessage;
import com.rally.payment.repository.OutboxJpaRepository;
import com.rally.payment.repository.PaymentJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentJpaRepository paymentRepository;
    private final OutboxJpaRepository outboxJpaRepository;
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    public PaymentService(
        PaymentJpaRepository paymentRepository,
        OutboxJpaRepository outboxJpaRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxJpaRepository = outboxJpaRepository;

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

    @Transactional
    public PaymentResponse createPaymentFromInitiation(PaymentInitiationRequested request) {
        Payment payment = Payment.initialize(
            UUID.randomUUID(),
            request.paymentMethodId(),
            request.userId(),
            request.orderId(),
            request.amount()
        );

        Payment savedPayment = paymentRepository.save(payment);

        writeInitializationOutbox(savedPayment);

        return PaymentResponse.from(savedPayment);
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

    private void writeInitializationOutbox(Payment payment) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(PaymentMessageHeaders.ID, UUID.randomUUID().toString());
        headers.put(PaymentMessageHeaders.TYPE, PaymentMessageType.INITIALIZED.value());
        headers.put(PaymentMessageHeaders.CORRELATION_ID, payment.getOrderId().toString());
        headers.put(PaymentMessageHeaders.CAUSATION_ID, payment.getId().toString());
        headers.put(PaymentMessageHeaders.TRACE_ID, payment.getId().toString());

        OutboxMessage outboxMessage = OutboxMessage.builder()
            .messageId(UUID.randomUUID())
            .aggregateId(payment.getId())
            .aggregateType("Payment")
            .topic("payment.events")
            .messageKey(payment.getOrderId().toString())
            .messageType(PaymentMessageType.INITIALIZED.value())
            .correlationId(payment.getOrderId())
            .causationId(payment.getId().toString())
            .traceId(payment.getId().toString())
            .payload(toJsonNode(Map.of(
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "userId", payment.getUserId(),
                "amount", payment.getAmount(),
                "status", payment.getStatus().name()
            )))
            .headers(toJsonNode(headers))
            .status("PENDING")
            .build();

        outboxJpaRepository.save(outboxMessage);
    }

    private JsonNode toJsonNode(Object value) {
        return OBJECT_MAPPER.valueToTree(value);
    }
}

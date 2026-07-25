package com.rally.payment.api.controller;

import com.rally.payment.api.dto.AuthorizePaymentRequest;
import com.rally.payment.api.dto.CreatePaymentRequest;
import com.rally.payment.api.dto.FailPaymentRequest;
import com.rally.payment.api.dto.PaymentResponse;
import com.rally.payment.api.dto.VoidPaymentRequest;
import com.rally.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(request));
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getById(@PathVariable UUID paymentId) {
        return paymentService.getPayment(paymentId);
    }

    @GetMapping("/user/{userId}")
    public List<PaymentResponse> getByUser(@PathVariable UUID userId) {
        return paymentService.getPaymentsForUser(userId);
    }

    @GetMapping("/order/{orderId}")
    public List<PaymentResponse> getByOrder(@PathVariable UUID orderId) {
        return paymentService.getPaymentsForOrder(orderId);
    }

    @PostMapping("/{paymentId}/authorize")
    public PaymentResponse authorize(
        @PathVariable UUID paymentId,
        @Valid @RequestBody AuthorizePaymentRequest request
    ) {
        return paymentService.authorizePayment(paymentId, request);
    }

    @PostMapping("/{paymentId}/capture")
    public PaymentResponse capture(@PathVariable UUID paymentId) {
        return paymentService.capturePayment(paymentId);
    }

    @PostMapping("/{paymentId}/fail")
    public PaymentResponse fail(
        @PathVariable UUID paymentId,
        @Valid @RequestBody FailPaymentRequest request
    ) {
        return paymentService.failPayment(paymentId, request);
    }

    @PostMapping("/{paymentId}/void")
    public PaymentResponse voidPayment(
        @PathVariable UUID paymentId,
        @Valid @RequestBody VoidPaymentRequest request
    ) {
        return paymentService.voidPayment(paymentId, request);
    }
}

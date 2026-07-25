package com.rally.payment.api.controller;

import com.rally.payment.api.dto.CreatePaymentMethodRequest;
import com.rally.payment.api.dto.PaymentMethodResponse;
import com.rally.payment.service.PaymentMethodService;
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
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @PostMapping
    public ResponseEntity<PaymentMethodResponse> create(
        @Valid @RequestBody CreatePaymentMethodRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentMethodService.createPaymentMethod(request));
    }

    @GetMapping("/{paymentMethodId}")
    public PaymentMethodResponse getById(@PathVariable UUID paymentMethodId) {
        return paymentMethodService.getPaymentMethod(paymentMethodId);
    }

    @GetMapping("/user/{userId}")
    public List<PaymentMethodResponse> getByUser(@PathVariable UUID userId) {
        return paymentMethodService.getPaymentMethodsForUser(userId);
    }
}

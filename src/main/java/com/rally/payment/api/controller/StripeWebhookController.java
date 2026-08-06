package com.rally.payment.api.controller;

import com.rally.payment.service.PaymentService;
import com.stripe.model.Event;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final PaymentService paymentService;

    public StripeWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("${stripe.webhook.path}")
    public ResponseEntity<Void> handleWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        Event event = paymentService.verifyStripeEvent(payload, signature);
        paymentService.processStripeEvent(event);
        return ResponseEntity.ok().build();
    }
}
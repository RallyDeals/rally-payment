package com.rally.payment.api.controller;

import com.rally.payment.service.StripeWebhookService;
import com.stripe.model.Event;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    public StripeWebhookController(StripeWebhookService stripeWebhookService) {
        this.stripeWebhookService = stripeWebhookService;
    }

    @PostMapping("${stripe.webhook-path}")
    public ResponseEntity<Void> handleWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        Event event = stripeWebhookService.verifyStripeEvent(payload, signature);
        stripeWebhookService.processStripeEvent(event);
        return ResponseEntity.ok().build();
    }
}
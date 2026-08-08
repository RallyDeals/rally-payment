package com.rally.payment.api.controller;

import com.rally.payment.api.dto.CreatePaymentMethodRequest;
import com.rally.payment.api.dto.PaymentMethodListResponse;
import com.rally.payment.api.dto.PaymentMethodResponse;
import com.rally.payment.api.dto.SetupIntentResponse;
import com.rally.payment.service.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/payment-methods")
@Tag(name = "Saved Payment Methods", description = "Buyer-scoped wallet management")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @GetMapping
    @Operation(summary = "List the buyer's saved payment methods (masked)")
    public PaymentMethodListResponse list(@PathVariable UUID userId) {
        return new PaymentMethodListResponse(paymentMethodService.listForUser(userId));
    }

    @GetMapping("/{methodId}")
    @Operation(summary = "Get one of the buyer's saved payment methods")
    public PaymentMethodResponse get(@PathVariable UUID userId, @PathVariable UUID methodId) {
        return paymentMethodService.getForUser(userId, methodId);
    }

    @PostMapping("/setup-intent")
    @Operation(summary = "Start adding a card via a Stripe SetupIntent")
    public ResponseEntity<SetupIntentResponse> createSetupIntent(@PathVariable UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentMethodService.startSetupIntent(userId));
    }

    @PostMapping
    @Operation(summary = "Confirm a completed SetupIntent and save the payment method")
    public ResponseEntity<PaymentMethodResponse> confirmCreate(
        @PathVariable UUID userId,
        @Valid @RequestBody CreatePaymentMethodRequest request
    ) {
        PaymentMethodService.PaymentMethodCreationResult result =
            paymentMethodService.confirmCreate(userId, request);
        HttpStatus status = result.duplicated() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @PutMapping("/{methodId}/default")
    @Operation(summary = "Set the buyer's default payment method")
    public PaymentMethodResponse setDefault(@PathVariable UUID userId, @PathVariable UUID methodId) {
        return paymentMethodService.setDefault(userId, methodId);
    }

    @DeleteMapping("/{methodId}/default")
    @Operation(summary = "Clear the default flag (no silent promotion)")
    public PaymentMethodResponse clearDefault(@PathVariable UUID userId, @PathVariable UUID methodId) {
        return paymentMethodService.clearDefault(userId, methodId);
    }

    @DeleteMapping("/{methodId}")
    @Operation(summary = "Hard-delete a saved payment method")
    public ResponseEntity<Void> remove(@PathVariable UUID userId, @PathVariable UUID methodId) {
        paymentMethodService.removeForUser(userId, methodId);
        return ResponseEntity.noContent().build();
    }
}
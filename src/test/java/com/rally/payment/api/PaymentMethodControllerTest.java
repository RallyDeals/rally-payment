package com.rally.payment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rally.common.exceptions.shared.NotFoundException;
import com.rally.payment.api.controller.PaymentMethodController;
import com.rally.payment.api.dto.PaymentMethodListResponse;
import com.rally.payment.api.dto.PaymentMethodResponse;
import com.rally.payment.api.dto.SetupIntentResponse;

import com.rally.payment.service.PaymentMethodService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentMethodController.class)
class PaymentMethodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentMethodService paymentMethodService;

    private final UUID userId = UUID.randomUUID();
    private final UUID methodId = UUID.randomUUID();

    private PaymentMethodResponse maskedMethod() {
        return new PaymentMethodResponse(
            methodId, userId, "card", true, "visa", "4242", "12", "28", "fp_abc"
        );
    }

    @Test
    void list_wrapsItems_andNeverSerializesRawToken() throws Exception {
        when(paymentMethodService.listForUser(userId)).thenReturn(List.of(maskedMethod()));

        mockMvc.perform(get("/api/payment-methods").header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[0].cardLast4").value("4242"))
            .andExpect(jsonPath("$.items[0].cardFingerprint").value("fp_abc"))
            .andExpect(jsonPath("$.items[0].token").doesNotExist());
    }

    @Test
    void get_crossOwner_returns404() throws Exception {
        when(paymentMethodService.getForUser(userId, methodId))
            .thenThrow(new NotFoundException("PaymentMethod", methodId));

        mockMvc.perform(get("/api/payment-methods/{methodId}", methodId).header("X-User-Id", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void confirmCreate_duplicate_returns200() throws Exception {
        PaymentMethodService.PaymentMethodCreationResult duplicate =
            new PaymentMethodService.PaymentMethodCreationResult(maskedMethod(), true);
        when(paymentMethodService.confirmCreate(eq(userId), any())).thenReturn(duplicate);

        mockMvc.perform(post("/api/payment-methods")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"pm_dup\",\"isDefault\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cardFingerprint").value("fp_abc"));
    }

    @Test
    void confirmCreate_new_returns201() throws Exception {
        PaymentMethodService.PaymentMethodCreationResult created =
            new PaymentMethodService.PaymentMethodCreationResult(maskedMethod(), false);
        when(paymentMethodService.confirmCreate(eq(userId), any())).thenReturn(created);

        mockMvc.perform(post("/api/payment-methods")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"pm_new\",\"isDefault\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void setupIntent_surfacesClientSecret() throws Exception {
        when(paymentMethodService.startSetupIntent(userId))
            .thenReturn(new SetupIntentResponse("seti_1", "seti_1_secret_x", true));

        mockMvc.perform(post("/api/payment-methods/setup-intent").header("X-User-Id", userId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.setupIntentId").value("seti_1"))
            .andExpect(jsonPath("$.clientSecret").value("seti_1_secret_x"))
            .andExpect(jsonPath("$.requiresAction").value(true));
    }

    @Test
    void setDefault_returns200() throws Exception {
        when(paymentMethodService.setDefault(userId, methodId)).thenReturn(maskedMethod());

        mockMvc.perform(put("/api/payment-methods/{methodId}/default", methodId).header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void clearDefault_returns200() throws Exception {
        PaymentMethodResponse cleared = new PaymentMethodResponse(
            methodId, userId, "card", false, "visa", "4242", "12", "28", "fp_abc"
        );
        when(paymentMethodService.clearDefault(userId, methodId)).thenReturn(cleared);

        mockMvc.perform(delete("/api/payment-methods/{methodId}/default", methodId).header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void remove_owned_returns204() throws Exception {
        mockMvc.perform(delete("/api/payment-methods/{methodId}", methodId).header("X-User-Id", userId))
            .andExpect(status().isNoContent());
    }
}
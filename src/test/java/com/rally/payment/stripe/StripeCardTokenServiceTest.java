package com.rally.payment.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.payment.config.StripeProperties;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.param.PaymentMethodDetachParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.service.PaymentMethodService;
import com.stripe.service.SetupIntentService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripeCardTokenServiceTest {

    @Mock
    private StripeClient stripeClient;

    @Mock
    private SetupIntentService setupIntentService;

    @Mock
    private PaymentMethodService stripePaymentMethodService;

    private StripeCardTokenService tokenService;

    @BeforeEach
    void setUp() {
        StripeProperties stripeProperties = new StripeProperties();
        stripeProperties.setCustomerId("cus_test");
        tokenService = new StripeCardTokenService(stripeClient, stripeProperties);
    }

    @Test
    void createSetupIntent_requiresAction_surfacesSetupIntent() throws Exception {
        when(stripeClient.setupIntents()).thenReturn(setupIntentService);
        SetupIntent setupIntent = new SetupIntent();
        setupIntent.setId("seti_1");
        setupIntent.setClientSecret("seti_1_secret_x");
        setupIntent.setStatus("requires_action");
        when(setupIntentService.create(any(SetupIntentCreateParams.class))).thenReturn(setupIntent);

        SetupIntent result = tokenService.createSetupIntent(UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo("requires_action");
        assertThat(result.getId()).isEqualTo("seti_1");
        assertThat(result.getClientSecret()).isEqualTo("seti_1_secret_x");
        verify(setupIntentService).create(any(SetupIntentCreateParams.class));
    }

    @Test
    void createSetupIntent_succeeded_returnsSucceededSetup() throws Exception {
        when(stripeClient.setupIntents()).thenReturn(setupIntentService);
        SetupIntent setupIntent = new SetupIntent();
        setupIntent.setId("seti_2");
        setupIntent.setStatus("succeeded");
        when(setupIntentService.create(any(SetupIntentCreateParams.class))).thenReturn(setupIntent);

        SetupIntent result = tokenService.createSetupIntent(UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo("succeeded");
    }

    @Test
    void createSetupIntent_gatewayFailure_throwsServiceUnavailable() throws Exception {
        when(stripeClient.setupIntents()).thenReturn(setupIntentService);
        when(setupIntentService.create(any(SetupIntentCreateParams.class)))
            .thenThrow(new ApiException("boom", null, null, 500, null));

        assertThrows(ServiceUnavailableException.class, () -> tokenService.createSetupIntent(UUID.randomUUID()));
    }

    @Test
    void retrieveCardDetails_returnsMaskedCardData() throws Exception {
        when(stripeClient.paymentMethods()).thenReturn(stripePaymentMethodService);
        PaymentMethod.Card card = new PaymentMethod.Card();
        card.setBrand("visa");
        card.setLast4("4242");
        card.setExpMonth(12L);
        card.setExpYear(2028L);
        card.setFingerprint("fp_abc");
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId("pm_123");
        paymentMethod.setType("card");
        paymentMethod.setCard(card);
        when(stripePaymentMethodService.retrieve("pm_123")).thenReturn(paymentMethod);

        PaymentMethod result = tokenService.retrieveCardDetails("pm_123");

        assertThat(result.getId()).isEqualTo("pm_123");
        assertThat(result.getCard().getBrand()).isEqualTo("visa");
        assertThat(result.getCard().getLast4()).isEqualTo("4242");
        assertThat(result.getCard().getFingerprint()).isEqualTo("fp_abc");
        verify(stripePaymentMethodService).retrieve("pm_123");
    }

    @Test
    void detachMethod_detachesGatewayReference() throws Exception {
        when(stripeClient.paymentMethods()).thenReturn(stripePaymentMethodService);
        PaymentMethod detached = new PaymentMethod();
        detached.setId("pm_123");
        when(stripePaymentMethodService.detach(anyString(), any(PaymentMethodDetachParams.class)))
            .thenReturn(detached);

        tokenService.detachMethod("pm_123");

        verify(stripePaymentMethodService).detach(anyString(), any(PaymentMethodDetachParams.class));
    }
}
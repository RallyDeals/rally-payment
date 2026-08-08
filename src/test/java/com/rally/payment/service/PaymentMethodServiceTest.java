package com.rally.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rally.common.exceptions.shared.NotFoundException;
import com.rally.payment.api.dto.CreatePaymentMethodRequest;
import com.rally.payment.api.dto.PaymentMethodResponse;
import com.rally.payment.api.dto.SetupIntentResponse;

import com.rally.payment.model.PaymentMethod;
import com.rally.payment.model.PaymentMethodCard;
import com.rally.payment.repository.PaymentMethodJpaRepository;
import com.rally.payment.stripe.StripeCardTokenService;
import com.stripe.model.SetupIntent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

    @Mock
    private PaymentMethodJpaRepository paymentMethodRepository;

    @Mock
    private StripeCardTokenService stripeCardTokenService;

    @InjectMocks
    private PaymentMethodService paymentMethodService;

    private static com.stripe.model.PaymentMethod confirmedCard(String pmId, String fingerprint) {
        com.stripe.model.PaymentMethod.Card card = new com.stripe.model.PaymentMethod.Card();
        card.setBrand("visa");
        card.setLast4("4242");
        card.setExpMonth(12L);
        card.setExpYear(2028L);
        card.setFingerprint(fingerprint);
        com.stripe.model.PaymentMethod pm = new com.stripe.model.PaymentMethod();
        pm.setId(pmId);
        pm.setType("card");
        pm.setCard(card);
        return pm;
    }

    @Test
    void confirmCreate_newCard_savesMaskedMethodWithFingerprint() {
        UUID userId = UUID.randomUUID();
        com.stripe.model.PaymentMethod gatewayPm = confirmedCard("pm_new", "fp_abc");
        when(stripeCardTokenService.retrieveCardDetails("pm_new")).thenReturn(gatewayPm);
        when(paymentMethodRepository.findByUserIdAndCardFingerprint(userId, "fp_abc")).thenReturn(Optional.empty());
        when(paymentMethodRepository.save(any(PaymentMethod.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethodService.PaymentMethodCreationResult result =
            paymentMethodService.confirmCreate(userId, new CreatePaymentMethodRequest("pm_new", true));

        assertThat(result.duplicated()).isFalse();
        assertThat(result.response().cardFingerprint()).isEqualTo("fp_abc");
        assertThat(result.response().cardLast4()).isEqualTo("4242");
        assertThat(result.response().cardBrand()).isEqualTo("visa");
        assertThat(result.response().isDefault()).isTrue();
        verify(paymentMethodRepository).save(any(PaymentMethod.class));
    }

    @Test
    void confirmCreate_duplicateCard_collapsesIntoSavedMethod() {
        UUID userId = UUID.randomUUID();
        PaymentMethod existing = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .type("card")
            .token("pm_old")
            .isDefault(false)
            .cardFingerprint("fp_abc")
            .paymentMethodCard(PaymentMethodCard.builder()
                .cardBrand("visa")
                .cardLast4("4242")
                .cardExpMonth("12")
                .cardExpYear("28")
                .build())
            .build();
        when(stripeCardTokenService.retrieveCardDetails("pm_dup")).thenReturn(confirmedCard("pm_dup", "fp_abc"));
        when(paymentMethodRepository.findByUserIdAndCardFingerprint(userId, "fp_abc"))
            .thenReturn(Optional.of(existing));

        PaymentMethodService.PaymentMethodCreationResult result =
            paymentMethodService.confirmCreate(userId, new CreatePaymentMethodRequest("pm_dup", false));

        assertThat(result.duplicated()).isTrue();
        assertThat(result.response().id()).isEqualTo(existing.getId());
        verify(paymentMethodRepository, never()).save(any(PaymentMethod.class));
    }

    @Test
    void confirmCreate_newDefault_clearsPreviousDefault() {
        UUID userId = UUID.randomUUID();
        PaymentMethod previousDefault = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .type("card")
            .token("pm_old")
            .isDefault(true)
            .cardFingerprint("fp_old")
            .build();
        when(stripeCardTokenService.retrieveCardDetails("pm_new")).thenReturn(confirmedCard("pm_new", "fp_abc"));
        when(paymentMethodRepository.findByUserIdAndCardFingerprint(userId, "fp_abc")).thenReturn(Optional.empty());
        when(paymentMethodRepository.findByUserIdAndIsDefaultTrue(userId)).thenReturn(Optional.of(previousDefault));
        when(paymentMethodRepository.save(any(PaymentMethod.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethodService.PaymentMethodCreationResult result =
            paymentMethodService.confirmCreate(userId, new CreatePaymentMethodRequest("pm_new", true));

        assertThat(previousDefault.isDefault()).isFalse();
        assertThat(result.response().isDefault()).isTrue();
    }

    @Test
    void startSetupIntent_requiresAction_nothingPersisted() {
        UUID userId = UUID.randomUUID();
        SetupIntent setupIntent = new SetupIntent();
        setupIntent.setId("seti_1");
        setupIntent.setClientSecret("seti_1_secret_x");
        setupIntent.setStatus("requires_action");
        when(stripeCardTokenService.createSetupIntent(userId)).thenReturn(setupIntent);

        SetupIntentResponse response = paymentMethodService.startSetupIntent(userId);

        assertThat(response.requiresAction()).isTrue();
        assertThat(response.setupIntentId()).isEqualTo("seti_1");
        assertThat(response.clientSecret()).isEqualTo("seti_1_secret_x");
        verify(paymentMethodRepository, never()).save(any(PaymentMethod.class));
    }

    @Test
    void startSetupIntent_succeeded_returnsNonAction() {
        UUID userId = UUID.randomUUID();
        SetupIntent setupIntent = new SetupIntent();
        setupIntent.setId("seti_2");
        setupIntent.setClientSecret("seti_2_secret_y");
        setupIntent.setStatus("succeeded");
        when(stripeCardTokenService.createSetupIntent(userId)).thenReturn(setupIntent);

        SetupIntentResponse response = paymentMethodService.startSetupIntent(userId);

        assertThat(response.requiresAction()).isFalse();
    }

    @Test
    void listForUser_returnsOnlyOwnedMethods() {
        UUID userId = UUID.randomUUID();
        PaymentMethod owned = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .type("card")
            .token("pm_1")
            .isDefault(true)
            .cardFingerprint("fp_1")
            .paymentMethodCard(PaymentMethodCard.builder().cardBrand("visa").cardLast4("4242").build())
            .build();
        when(paymentMethodRepository.findByUserId(userId)).thenReturn(List.of(owned));

        List<PaymentMethodResponse> result = paymentMethodService.listForUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(userId);
        assertThat(result.get(0).cardFingerprint()).isEqualTo("fp_1");
    }

    @Test
    void getForUser_crossOwner_throwsNotFound() {
        UUID requester = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        PaymentMethod other = PaymentMethod.builder()
            .id(methodId)
            .userId(owner)
            .type("card")
            .token("pm_other")
            .build();
        when(paymentMethodRepository.findById(methodId)).thenReturn(Optional.of(other));

        assertThrows(NotFoundException.class, () -> paymentMethodService.getForUser(requester, methodId));
    }

    @Test
    void removeForUser_ownedMethod_detachesAndDeletes() {
        UUID userId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        PaymentMethod owned = PaymentMethod.builder()
            .id(methodId)
            .userId(userId)
            .type("card")
            .token("pm_owned")
            .build();
        when(paymentMethodRepository.findById(methodId)).thenReturn(Optional.of(owned));

        paymentMethodService.removeForUser(userId, methodId);

        verify(stripeCardTokenService).detachMethod("pm_owned");
        verify(paymentMethodRepository).delete(owned);
    }

    @Test
    void setDefault_switchesDefaultAtomically() {
        UUID userId = UUID.randomUUID();
        PaymentMethod currentDefault = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .type("card")
            .token("pm_default")
            .isDefault(true)
            .build();
        PaymentMethod target = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .type("card")
            .token("pm_target")
            .isDefault(false)
            .build();
        when(paymentMethodRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(paymentMethodRepository.findByUserIdAndIsDefaultTrue(userId)).thenReturn(Optional.of(currentDefault));
        when(paymentMethodRepository.save(any(PaymentMethod.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethodResponse response = paymentMethodService.setDefault(userId, target.getId());

        assertThat(currentDefault.isDefault()).isFalse();
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void clearDefault_doesNotPromoteAnother() {
        UUID userId = UUID.randomUUID();
        PaymentMethod defaultMethod = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .type("card")
            .token("pm_default")
            .isDefault(true)
            .build();
        when(paymentMethodRepository.findById(defaultMethod.getId())).thenReturn(Optional.of(defaultMethod));
        when(paymentMethodRepository.save(any(PaymentMethod.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethodResponse response = paymentMethodService.clearDefault(userId, defaultMethod.getId());

        assertThat(response.isDefault()).isFalse();
    }
}
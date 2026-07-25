package com.rally.payment.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethod {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "token", nullable = false, length = 500)
    private String token;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "cardBrand", column = @Column(name = "card_brand")),
        @AttributeOverride(name = "cardLast4", column = @Column(name = "card_last4")),
        @AttributeOverride(name = "cardExpMonth", column = @Column(name = "card_exp_month")),
        @AttributeOverride(name = "cardExpYear", column = @Column(name = "card_exp_year"))
    })
    private PaymentMethodCard paymentMethodCard;

    @Version
    @Column(name = "version")
    private Long version;
}

package com.rally.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {

    private String apiKey;
    private String webhookSecret;
    private String webhookPath;
    private String currency = "usd";
}

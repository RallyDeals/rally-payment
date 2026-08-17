package com.rally.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "outbox.relay")
public class OutboxRelayProperties {

    private long pollIntervalMs = 1000;

    private int batchSize = 100;

    private long healthStaleMs = 5 * 60 * 1000;
}

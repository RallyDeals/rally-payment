package com.rally.payment.health;

import com.rally.payment.config.OutboxRelayProperties;
import com.rally.payment.repository.OutboxJpaRepository;
import java.time.Instant;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OutboxLagHealthIndicator implements HealthIndicator {

    private static final String STATUS_PENDING = "PENDING";

    private final OutboxJpaRepository outboxJpaRepository;
    private final OutboxRelayProperties properties;

    public OutboxLagHealthIndicator(OutboxJpaRepository outboxJpaRepository, OutboxRelayProperties properties) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Instant cutoff = Instant.now().minusMillis(properties.getHealthStaleMs());
        long stalePending = outboxJpaRepository.countByStatusAndCreatedAtBefore(STATUS_PENDING, cutoff);

        if (stalePending > 0) {
            return Health.down()
                    .withDetail("stalePendingMessages", stalePending)
                    .withDetail("threshold", properties.getHealthStaleMs() + "ms")
                    .build();
        }

        return Health.up()
                .withDetail("pendingMessages", outboxJpaRepository.countByStatus(STATUS_PENDING))
                .build();
    }
}
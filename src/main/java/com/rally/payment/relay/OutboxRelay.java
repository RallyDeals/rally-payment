package com.rally.payment.relay;

import com.rally.payment.config.OutboxRelayProperties;
import com.rally.payment.messaging.outbox.OutboxMessage;
import com.rally.payment.repository.OutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxRelay {

    private final OutboxJpaRepository outboxJpaRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxRelayProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(
        OutboxJpaRepository outboxJpaRepository,
        OutboxPublisher outboxPublisher,
        OutboxRelayProperties properties,
        PlatformTransactionManager transactionManager
    ) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.outboxPublisher = outboxPublisher;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
    public void relayPending() {
        transactionTemplate.executeWithoutResult(status -> relayBatch());
    }

    private void relayBatch() {
        List<OutboxMessage> pending = outboxJpaRepository.findPendingForUpdateSkipLocked(properties.getBatchSize());

        if (pending.isEmpty()) {
            return;
        }

        int published = 0;
        int retried = 0;
        int failed = 0;

        for (OutboxMessage message : pending) {
            try {
                boolean ok = outboxPublisher.publish(message);
                if (!ok) {
                    throw new RuntimeException("publish returned false");
                }
                message.setStatus("PUBLISHED");
                message.setPublishedAt(Instant.now());
                message.setLastError(null);
                published++;
            } catch (Exception e) {
                int retries = message.getRetryCount() + 1;
                message.setRetryCount(retries);
                message.setLastError(e.getMessage());
                if (retries >= message.getMaxRetries()) {
                    message.setStatus("FAILED");
                    failed++;
                } else {
                    message.setStatus("PENDING");
                    retried++;
                }
                log.warn("Relay failed for outbox message {} (attempt {} of {}) on topic {}: {}",
                    message.getMessageId(), message.getRetryCount(), message.getMaxRetries(),
                    message.getTopic(), e.getMessage());
            }

            outboxJpaRepository.save(message);
        }

        log.info("Outbox relay run: {} pending, {} published, {} retried, {} failed",
            pending.size(), published, retried, failed);
    }
}
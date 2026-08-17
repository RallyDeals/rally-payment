package com.rally.payment.relay;

import com.rally.payment.config.OutboxRelayProperties;
import com.rally.payment.messaging.outbox.OutboxMessage;
import com.rally.payment.metrics.PaymentMetrics;
import com.rally.payment.repository.OutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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

    private static final Pattern OTLP_TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final OutboxJpaRepository outboxJpaRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxRelayProperties properties;
    private final PaymentMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    private final Tracer tracer;

    public OutboxRelay(
        OutboxJpaRepository outboxJpaRepository,
        OutboxPublisher outboxPublisher,
        OutboxRelayProperties properties,
        PaymentMetrics metrics,
        PlatformTransactionManager transactionManager,
        Tracer tracer
    ) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.outboxPublisher = outboxPublisher;
        this.properties = properties;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.tracer = tracer;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
    public void relayPending() {
        transactionTemplate.executeWithoutResult(status -> relayBatch());
    }

    private void relayBatch() {
        List<OutboxMessage> pending = outboxJpaRepository.findPendingForUpdateSkipLocked(properties.getBatchSize());
        metrics.setOutboxPending(pending.size());

        if (pending.isEmpty()) {
            return;
        }

        int published = 0;
        int retried = 0;
        int failed = 0;

        for (OutboxMessage message : pending) {

            Span span = null;
            String traceId = message.getTraceId();
            if (traceId != null && OTLP_TRACE_ID_PATTERN.matcher(traceId).matches()) {
                span = tracer.spanBuilder()
                        .name("relay-outbox-message")
                        .setParent(tracer.traceContextBuilder().traceId(traceId).build())
                        .start();
            }

            try (Tracer.SpanInScope spanInScope = span != null ? tracer.withSpan(span) : null) {
                boolean ok = outboxPublisher.publish(message);
                if (!ok) {
                    throw new RuntimeException("publish returned false");
                }

                message.setStatus("PUBLISHED");
                message.setPublishedAt(Instant.now());
                message.setLastError(null);
                published++;
                log.info("Outbox message {} of type {} published to topic {}",
                        message.getMessageId(), message.getMessageType(), message.getTopic());

            } catch (Exception e) {
                int retries = message.getRetryCount() + 1;
                message.setRetryCount(retries);
                message.setLastError(e.getMessage());
                if (retries >= message.getMaxRetries()) {
                    message.setStatus("FAILED");
                    failed++;
                    metrics.recordOutboxPublishFailure();
                } else {
                    message.setStatus("PENDING");
                    retried++;
                }
                log.warn("Relay failed for outbox message {} (attempt {} of {}) on topic {}: {}",
                    message.getMessageId(), message.getRetryCount(), message.getMaxRetries(),
                    message.getTopic(), e.getMessage());
            } finally {
                if (span != null) {
                    span.end();
                }
            }

            outboxJpaRepository.save(message);
        }

        log.info("Outbox relay run: {} pending, {} published, {} retried, {} failed",
            pending.size(), published, retried, failed);
    }
}
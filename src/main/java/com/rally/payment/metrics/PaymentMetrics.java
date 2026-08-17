package com.rally.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong outboxPending = new AtomicLong();

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("outbox.relay.pending.count", outboxPending, value -> value.doubleValue())
                .description("Outbox rows not yet published")
                .register(meterRegistry);
    }

    public void recordPaymentCreated(String currency, String method) {
        counter("payment.created.count", currency, method).increment();
    }

    public void recordPaymentSucceeded(String currency, String method) {
        counter("payment.succeeded.count", currency, method).increment();
    }

    public void recordPaymentFailed(String currency, String method) {
        counter("payment.failed.count", currency, method).increment();
    }

    public void recordWebhookReceived(String eventType) {
        meterRegistry.counter("stripe.webhook.received.count", "eventType", eventType).increment();
    }

    public Timer.Sample startWebhookProcessing() {
        return Timer.start(meterRegistry);
    }

    public void stopWebhookProcessing(Timer.Sample sample) {
        sample.stop(meterRegistry.timer("stripe.webhook.processing.duration"));
    }

    public void recordOutboxPublishFailure() {
        meterRegistry.counter("outbox.relay.publish.failures.count").increment();
    }

    public void setOutboxPending(long count) {
        outboxPending.set(count);
    }

    public void recordKafkaEventProcessed(String messageType) {
        meterRegistry.counter("kafka.event.processed.count", "messageType", messageType).increment();
    }

    private Counter counter(String name, String currency, String method) {
        return meterRegistry.counter(name, "currency", currency, "method", method);
    }
}
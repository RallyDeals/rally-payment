package com.rally.payment.health;

import org.apache.kafka.common.PartitionInfo;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private static final String PAYMENT_TOPIC = "payment.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaHealthIndicator(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Health health() {
        try {
            List<PartitionInfo> partitions = kafkaTemplate.partitionsFor(PAYMENT_TOPIC);
            return Health.up()
                    .withDetail("topic", PAYMENT_TOPIC)
                    .withDetail("partitions", partitions.size())
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
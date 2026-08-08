package com.rally.payment.relay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rally.payment.messaging.contract.PaymentMessageHeaders;
import com.rally.payment.messaging.outbox.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public boolean publish(OutboxMessage message) {
        List<Header> kafkaHeaders = toKafkaHeaders(message);
        ProducerRecord<String, Object> record = new ProducerRecord<>(
            message.getTopic(),
            null,
            message.getMessageId().toString(),
            (Object) message.getPayload(),
            kafkaHeaders
        );

        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing outbox message {}", message.getMessageId(), e);
            return false;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to publish outbox message {} to topic {}: {}", message.getMessageId(), message.getTopic(), e.getMessage());
            return false;
        }
    }

    private List<Header> toKafkaHeaders(OutboxMessage message) {
        List<Header> headers = new ArrayList<>();

        if (message.getHeaders() != null && message.getHeaders().isObject()) {
            Map<String, String> source = OBJECT_MAPPER.convertValue(
                message.getHeaders(),
                new TypeReference<Map<String, String>>() {
                }
            );
            source.forEach((key, value) ->
                headers.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8))));
        }

        headers.add(new RecordHeader(
            PaymentMessageHeaders.ID,
            message.getMessageId().toString().getBytes(StandardCharsets.UTF_8)
        ));

        return headers;
    }
}
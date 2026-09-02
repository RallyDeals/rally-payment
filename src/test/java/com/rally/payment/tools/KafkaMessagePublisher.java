package com.rally.payment.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

@Component
public final class KafkaMessagePublisher {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TOPIC = "order.payments_requested";

    private static final String MESSAGE_TYPE = "Payment.InitRequired.Authorize";

    private static final String PAYMENT_METHOD_ID = "a82d6b38-60d7-4b71-9b19-1a9e8f1bb54e";
    private static final String USER_ID = "60ce018b-400f-4363-a3c3-e8a85d36dce4";
    private static final String ORDER_ID = UUID.randomUUID().toString();
    private static final String PAYMENT_ID = "REPLACE_WITH_PAYMENT_ID";
    private static final String AMOUNT = "150.00";
    private static final String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    public static void main(String[] args) throws Exception {
        boolean settlement = MESSAGE_TYPE.startsWith("Payment.Settlement");
        ObjectNode payload = settlement ? settlementPayload() : initiationPayload();

        UUID messageId = UUID.randomUUID();

        Map<String, String> headers = Map.of(
                "X-Id", messageId.toString(),
                "X-Type", MESSAGE_TYPE,
                "traceparent", traceparent,
                "X-Correlation-Id",UUID.randomUUID().toString()

        );

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, ORDER_ID, payload.toString());
            headers.forEach((k, v) -> record.headers().add(new RecordHeader(k, v.getBytes())));
            var metadata = producer.send(record).get();

            System.out.println("Published to topic=" + metadata.topic()
                    + " partition=" + metadata.partition()
                    + " offset=" + metadata.offset());
            System.out.println("Headers: " + headers);
            System.out.println("Payload: " + payload);
        }
    }

    private static ObjectNode initiationPayload() {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("userId", USER_ID);
        payload.put("orderId", ORDER_ID);
        payload.put("paymentMethodId", PAYMENT_METHOD_ID);
        payload.put("amount", AMOUNT);
        return payload;
    }

    private static ObjectNode settlementPayload() {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("paymentId", PAYMENT_ID);
        payload.put("orderId", ORDER_ID);
        return payload;
    }

    private static Map<String, Object> producerProps() {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.ACKS_CONFIG, "all"
        );
    }

    private KafkaMessagePublisher() {
    }
}

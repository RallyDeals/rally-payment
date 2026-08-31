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

public final class KafkaMessagePublisher {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TOPIC = "order.payments_requested";

    private static final String MESSAGE_TYPE = "Payment.InitRequired.Authorize";

    private static final String PAYMENT_METHOD_ID = "c21f969b-5779-4328-82d1-935104d49d94";
    private static final String USER_ID = "4dc618d7-290b-4eee-8cb5-6115e6085b6b";
    private static final String ORDER_ID = UUID.randomUUID().toString();
    private static final String PAYMENT_ID = "REPLACE_WITH_PAYMENT_ID";
    private static final String AMOUNT = "150.00";

    public static void main(String[] args) throws Exception {
        boolean settlement = MESSAGE_TYPE.startsWith("Payment.Settlement");
        ObjectNode payload = settlement ? settlementPayload() : initiationPayload();

        UUID messageId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Map<String, String> headers = Map.of(
                "X-Id", messageId.toString(),
                "X-Type", MESSAGE_TYPE,
                "X-Correlation-Id", correlationId.toString(),
                "X-Causation-Id", settlement ? "payment.authorized" : messageId.toString(),
                "X-Trace-Id", "trace-" + MESSAGE_TYPE + "-" + System.currentTimeMillis()
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

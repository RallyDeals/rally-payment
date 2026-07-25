package com.rally.payment.messaging.inbox;

import com.rally.payment.enums.InboxMessageSource;
import com.rally.payment.messaging.support.JsonMapAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inbox_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxMessage implements Serializable {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private String messageId;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(name = "headers", columnDefinition = "jsonb")
    private Map<String, String> headers;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "RECEIVED";

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Builder.Default
    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 5;

    @Column(name = "last_error")
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private InboxMessageSource source;

    @PrePersist
    public void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}

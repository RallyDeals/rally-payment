package com.rally.payment.model;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;


@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage implements Serializable {
    private UUID id;
    private UUID aggregateId;
    private String aggregateType;
    private String eventType;
    private String payload;
    private Map<String, String> headers;
    @Builder.Default
    private String status = "PENDING";
    private int retryCount;
    @Builder.Default
    private int maxRetries =5;
    private Instant createdAt;
    private Instant publishedAt;
    private String lastError;


}
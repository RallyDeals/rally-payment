package com.rally.payment.model;

import com.rally.payment.enums.InboxMessageSource;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;


@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InboxMessage implements Serializable {
    private String messageId;
    private String payload;
    private Map<String, String> headers;
    @Builder.Default
    private String status = "RECEIVED";
    private Instant receivedAt;
    private Instant processedAt;
    private int retryCount;
    @Builder.Default
    private int maxRetries = 5;
    private String lastError;
    private InboxMessageSource source;


}
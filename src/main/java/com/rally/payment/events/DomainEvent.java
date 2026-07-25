package com.rally.payment.events;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public interface DomainEvent extends Serializable {
    UUID aggregateId();
    Instant occurredAt();
    String eventType();
}
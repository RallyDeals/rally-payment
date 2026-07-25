package com.rally.payment.domain.aggregate;

import com.rally.payment.domain.aggregate.events.DomainEvent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class Aggregate implements Serializable {

    private UUID id;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected Aggregate() {}

    protected Aggregate(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    protected void setId(UUID id) {
        this.id = id;
    }

    public List<DomainEvent> getDomainEvents() {
        return List.copyOf(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    protected void raiseDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Aggregate other = (Aggregate) obj;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
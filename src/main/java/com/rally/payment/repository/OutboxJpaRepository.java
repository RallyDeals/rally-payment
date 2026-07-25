package com.rally.payment.repository;

import com.rally.payment.messaging.outbox.OutboxMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findTop100ByStatusOrderByCreatedAtAsc(String status);

    List<OutboxMessage> findByAggregateId(UUID aggregateId);

    List<OutboxMessage> findByStatusAndRetryCountLessThan(String status, int retryCount);
}

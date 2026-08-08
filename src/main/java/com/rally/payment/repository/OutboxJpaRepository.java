package com.rally.payment.repository;

import com.rally.payment.messaging.outbox.OutboxMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findTop100ByStatusOrderByCreatedAtAsc(String status);

    List<OutboxMessage> findByAggregateId(UUID aggregateId);

    List<OutboxMessage> findByStatusAndRetryCountLessThan(String status, int retryCount);

    @Query(value = "SELECT * FROM outbox_messages "
        + "WHERE status = 'PENDING' AND retry_count < max_retries "
        + "ORDER BY created_at ASC "
        + "LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxMessage> findPendingForUpdateSkipLocked(@Param("limit") int limit);
}

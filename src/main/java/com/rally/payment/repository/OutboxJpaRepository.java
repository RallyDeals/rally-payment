package com.rally.payment.repository;

import com.rally.payment.model.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxMessage, UUID> {

    @Query("SELECT o FROM OutboxEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxMessage> findPendingByCreatedAtAsc(int batchSize);

    @Query("SELECT o FROM OutboxEntity o WHERE o.aggregateId = :aggregateId")
    List<OutboxMessage> findByAggregateId(@Param("aggregateId") UUID aggregateId);

    @Query("SELECT o FROM OutboxEntity o WHERE o.status = 'FAILED' AND o.retryCount < o.maxRetries")
    List<OutboxMessage> findFailedWithRetries();
}
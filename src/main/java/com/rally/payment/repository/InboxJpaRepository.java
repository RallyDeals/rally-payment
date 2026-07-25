package com.rally.payment.repository;

import com.rally.payment.model.InboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface InboxJpaRepository extends JpaRepository<InboxMessage, String> {

    Optional<InboxMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);
}
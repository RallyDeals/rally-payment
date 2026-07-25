package com.rally.payment.repository;

import com.rally.payment.messaging.inbox.InboxMessage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InboxJpaRepository extends JpaRepository<InboxMessage, String> {

    Optional<InboxMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);
}

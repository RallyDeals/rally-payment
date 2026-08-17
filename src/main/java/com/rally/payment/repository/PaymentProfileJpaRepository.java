package com.rally.payment.repository;

import com.rally.payment.model.PaymentProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentProfileJpaRepository extends JpaRepository<PaymentProfile, UUID> {

    Optional<PaymentProfile> findByUserId(UUID userId);
}

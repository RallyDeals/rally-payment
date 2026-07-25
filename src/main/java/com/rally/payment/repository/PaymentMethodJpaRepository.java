package com.rally.payment.repository;

import com.rally.payment.model.PaymentMethod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethod, UUID> {

    Optional<PaymentMethod> findByToken(String token);

    List<PaymentMethod> findByUserId(UUID userId);
}

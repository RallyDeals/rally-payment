package com.rally.payment.repository;

import com.rally.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByPaymentIntentId(String paymentIntentId);

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByUserId(String userId);

    List<Payment> findByStatus(String status);

    @Query("SELECT p FROM PaymentEntity p WHERE p.paymentMethodId = :paymentMethodId")
    List<Payment> findByPaymentMethodId(@Param("paymentMethodId") UUID paymentMethodId);
}
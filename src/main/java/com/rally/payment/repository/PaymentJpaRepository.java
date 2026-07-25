package com.rally.payment.repository;

import com.rally.payment.enums.PaymentStatus;
import com.rally.payment.model.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByPaymentIntentId(String paymentIntentId);

    Optional<Payment> findByOrderId(UUID orderId);

    List<Payment> findByUserId(UUID userId);

    List<Payment> findByStatus(PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.paymentMethodId = :paymentMethodId")
    List<Payment> findByPaymentMethodId(@Param("paymentMethodId") UUID paymentMethodId);
}

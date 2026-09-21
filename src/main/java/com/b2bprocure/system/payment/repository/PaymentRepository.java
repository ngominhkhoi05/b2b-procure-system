package com.b2bprocure.system.payment.repository;

import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPaymentCode(String paymentCode);

    boolean existsByPaymentCode(String paymentCode);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.paymentMethod != 'COD' AND p.expiredAt < :now")
    List<Payment> findExpiredPayments(@Param("status") PaymentStatus status, @Param("now") LocalDateTime now);

}

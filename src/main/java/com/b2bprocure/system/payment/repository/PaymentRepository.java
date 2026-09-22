package com.b2bprocure.system.payment.repository;

import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    Optional<Payment> findByAppTransId(String appTransId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.appTransId = :appTransId")
    Optional<Payment> findByAppTransIdWithLock(@Param("appTransId") String appTransId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") Long id);

    boolean existsByPaymentCode(String paymentCode);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.paymentMethod = 'ZALOPAY' AND p.expiredAt <= :now")
    List<Payment> findExpiredPayments(@Param("status") PaymentStatus status, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.paymentMethod = 'ZALOPAY' AND p.expiredAt <= :now ORDER BY p.id ASC")
    List<Payment> findExpiredPaymentsWithLock(@Param("status") PaymentStatus status, @Param("now") LocalDateTime now);

    /**
     * Find ZaloPay payments eligible for supplier confirmation timeout (paid orders).
     * Only payments with status SUCCESS and paidAt <= deadline are eligible.
     *
     * @param status Payment status (SUCCESS)
     * @param deadline Payments paid before or at this deadline have expired
     * @return List of payments eligible for supplier timeout processing
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p JOIN FETCH p.order o WHERE p.status = :status AND p.paidAt IS NOT NULL AND p.paidAt <= :deadline ORDER BY p.id ASC")
    List<Payment> findPaidZaloPayPaymentsForTimeout(
            @Param("status") PaymentStatus status,
            @Param("deadline") LocalDateTime deadline);

}

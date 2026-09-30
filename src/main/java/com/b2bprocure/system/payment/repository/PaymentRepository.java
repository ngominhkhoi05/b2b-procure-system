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

    /**
     * Step 7 — Batch-fetch Payments by order IDs in a single SQL roundtrip.
     * Used by {@code OrderLifecycleServiceImpl.getOrders(...)} to populate
     * {@code OrderResponse.paymentMethod} / {@code paymentStatus} without N+1.
     *
     * Each Order has at most one Payment (unique constraint on {@code order_id}).
     */
    @Query("SELECT p FROM Payment p WHERE p.order.id IN :orderIds")
    List<Payment> findAllByOrderIdIn(@Param("orderIds") java.util.Collection<Long> orderIds);

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

    /**
     * Find all payments currently in REFUND_PENDING state for retry processing.
     * Used by the pending-refund scheduler to pick up refunds that failed inline
     * (e.g. ZaloPay API was unreachable when supplier rejected / buyer cancelled).
     *
     * <p>Idempotency: RefundService.processRefund re-checks the status under pessimistic
     * write lock, so duplicate processing between inline call and scheduler is safe.
     *
     * <p>No {@code PESSIMISTIC_WRITE} here: it would force Hibernate to merge
     * the lock into the scheduler's outer transaction and could conflict with the
     * per-payment lock that {@link RefundServiceImpl#processRefund} acquires on
     * the same row inside the same transaction. The inner {@code findByIdWithLock}
     * already serializes per-row writes correctly.
     *
     * <p>{@code JOIN FETCH p.order}: required so that
     * {@code Order order = current.getOrder()} inside {@code RefundServiceImpl}
     * does not trigger a lazy load outside its transactional boundary (the
     * scheduler's transaction closes once this query returns).
     *
     * @return Payments awaiting refund, oldest first.
     */
    @Query("SELECT p FROM Payment p JOIN FETCH p.order o WHERE p.status = com.b2bprocure.system.common.enums.PaymentStatus.REFUND_PENDING ORDER BY p.updatedAt ASC")
    List<Payment> findPendingRefundsWithLock();

}

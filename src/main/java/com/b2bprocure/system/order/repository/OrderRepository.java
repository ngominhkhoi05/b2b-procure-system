package com.b2bprocure.system.order.repository;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    @Query("SELECT o FROM Order o " +
            "JOIN FETCH o.buyerCompany " +
            "JOIN FETCH o.supplierCompany " +
            "JOIN FETCH o.createdBy " +
            "WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    Page<Order> findByBuyerCompanyId(Long buyerCompanyId, Pageable pageable);

    Page<Order> findBySupplierCompanyId(Long supplierCompanyId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Find COD orders eligible for supplier confirmation timeout processing.
     * COD orders: PENDING_CONFIRMATION where createdAt <= deadline (meaning expired)
     *
     * @param status Only look for orders with this status (PENDING_CONFIRMATION)
     * @param deadline Orders created before or at this deadline have expired
     * @return List of COD orders that have exceeded their supplier confirmation deadline
     */
    @Query("SELECT o FROM Order o " +
            "WHERE o.status = :status AND o.createdAt <= :deadline " +
            "ORDER BY o.id ASC")
    List<Order> findExpiredCodOrders(
            @Param("status") OrderStatus status,
            @Param("deadline") LocalDateTime deadline);

    /**
     * Role-aware Order list query for Order History / Query.
     *
     * Visibility is enforced entirely at the SQL layer (per Step 7 spec §11):
     * <ul>
     *   <li>BUYER  → filter by {@code createdBy.id = :buyerUserId} OR {@code buyerCompany.id = :buyerCompanyId}</li>
     *   <li>SUPPLIER → filter by {@code EXISTS (OrderItem → Product.supplier_company_id = :supplierCompanyId)}</li>
     *   <li>ADMIN → all parameters null → no visibility restriction</li>
     * </ul>
     *
     * The query uses an explicit {@code LEFT JOIN Payment pay ON pay.order = o} to load payment
     * data in a single SQL roundtrip (avoids N+1 for the list). Payment fields are NOT mapped
     * to the {@code Order} entity (no association) — service composes the response by hand.
     */
    @Query(value = """
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.buyerCompany
            JOIN FETCH o.supplierCompany
            LEFT JOIN com.b2bprocure.system.payment.entity.Payment pay ON pay.order = o
            WHERE
              (CAST(:status AS string) IS NULL OR o.status = :status)
              AND (CAST(:paymentMethod AS string) IS NULL OR pay.paymentMethod = :paymentMethod)
              AND (CAST(:paymentStatus AS string) IS NULL OR pay.status = :paymentStatus)
              AND (CAST(:fromDate AS timestamp) IS NULL OR o.createdAt >= :fromDate)
              AND (CAST(:toDateExclusive AS timestamp) IS NULL OR o.createdAt < :toDateExclusive)
              AND (
                CAST(:buyerUserId AS string) IS NULL
                OR CAST(:buyerCompanyId AS string) IS NULL
                OR o.createdBy.id = :buyerUserId
                OR o.buyerCompany.id = :buyerCompanyId
              )
              AND (
                CAST(:supplierCompanyId AS string) IS NULL
                OR EXISTS (
                  SELECT 1 FROM OrderItem oi
                  WHERE oi.order = o
                    AND oi.product.supplierCompany.id = :supplierCompanyId
                )
              )
            """,
           countQuery = """
            SELECT COUNT(DISTINCT o) FROM Order o
            LEFT JOIN com.b2bprocure.system.payment.entity.Payment pay ON pay.order = o
            WHERE
              (CAST(:status AS string) IS NULL OR o.status = :status)
              AND (CAST(:paymentMethod AS string) IS NULL OR pay.paymentMethod = :paymentMethod)
              AND (CAST(:paymentStatus AS string) IS NULL OR pay.status = :paymentStatus)
              AND (CAST(:fromDate AS timestamp) IS NULL OR o.createdAt >= :fromDate)
              AND (CAST(:toDateExclusive AS timestamp) IS NULL OR o.createdAt < :toDateExclusive)
              AND (
                CAST(:buyerUserId AS string) IS NULL
                OR CAST(:buyerCompanyId AS string) IS NULL
                OR o.createdBy.id = :buyerUserId
                OR o.buyerCompany.id = :buyerCompanyId
              )
              AND (
                CAST(:supplierCompanyId AS string) IS NULL
                OR EXISTS (
                  SELECT 1 FROM OrderItem oi
                  WHERE oi.order = o
                    AND oi.product.supplierCompany.id = :supplierCompanyId
                )
              )
            """)
    Page<Order> searchOrders(
            @Param("status") OrderStatus status,
            @Param("paymentMethod") com.b2bprocure.system.common.enums.PaymentMethod paymentMethod,
            @Param("paymentStatus") com.b2bprocure.system.common.enums.PaymentStatus paymentStatus,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDateExclusive") LocalDateTime toDateExclusive,
            @Param("buyerUserId") Long buyerUserId,
            @Param("buyerCompanyId") Long buyerCompanyId,
            @Param("supplierCompanyId") Long supplierCompanyId,
            Pageable pageable
    );

}

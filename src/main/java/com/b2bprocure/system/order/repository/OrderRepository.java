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

}

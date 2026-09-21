package com.b2bprocure.system.order.repository;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

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

}

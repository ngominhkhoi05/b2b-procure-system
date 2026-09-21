package com.b2bprocure.system.order.repository;

import com.b2bprocure.system.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    @Query("SELECT osh FROM OrderStatusHistory osh " +
            "LEFT JOIN FETCH osh.changedBy " +
            "WHERE osh.order.id = :orderId " +
            "ORDER BY osh.createdAt ASC")
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(@Param("orderId") Long orderId);

}

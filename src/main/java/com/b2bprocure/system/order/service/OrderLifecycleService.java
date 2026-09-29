package com.b2bprocure.system.order.service;

import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.order.dto.CancelOrderRequest;
import com.b2bprocure.system.order.dto.OrderDetailResponse;
import com.b2bprocure.system.order.dto.OrderResponse;
import com.b2bprocure.system.order.dto.OrderStatusHistoryResponse;
import com.b2bprocure.system.order.dto.RejectOrderRequest;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreatePaymentResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface OrderLifecycleService {

    OrderResponse confirmOrder(Long orderId);

    OrderResponse rejectOrder(Long orderId, RejectOrderRequest request);

    OrderResponse cancelOrder(Long orderId, CancelOrderRequest request);

    /**
     * Re-initiate ZaloPay payment for an order whose Payment is still PENDING.
     *
     * Used by the Order Detail page so a buyer who closed the ZaloPay tab by
     * accident can resume payment without going back to checkout. Delegates to
     * {@code ZaloPayService.initiatePayment(paymentId, orderId)} which is
     * idempotent — if the Payment already has a ZaloPay order, the existing URL
     * is returned as-is.
     *
     * Buyer-only. Validates ownership (creator OR same buyer company) and that
     * the Payment method is ZALOPAY + status is PENDING.
     */
    ZaloPayCreatePaymentResponse retryZaloPayPayment(Long orderId);

    OrderResponse updateToPreparing(Long orderId);

    OrderResponse updateToShipping(Long orderId);

    OrderResponse updateToCompleted(Long orderId);

    OrderDetailResponse getOrderDetail(Long orderId);

    List<OrderStatusHistoryResponse> getOrderStatusHistory(Long orderId);

    /**
     * Step 7 — Order History / Order Query: paginated, filtered list.
     *
     * Visibility (Buyer / Supplier / Admin) is enforced at the SQL layer based on the
     * currently authenticated principal — caller-supplied IDs are intentionally NOT used
     * to determine ownership.
     *
     * Default sort is applied at the controller layer via {@link Pageable}
     * (page=0, size=20, sort=createdAt, direction=DESC).
     */
    PageResponse<OrderResponse> getOrders(
            String status,
            String paymentMethod,
            String paymentStatus,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable);
}

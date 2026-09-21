package com.b2bprocure.system.order.service;

import com.b2bprocure.system.order.dto.CancelOrderRequest;
import com.b2bprocure.system.order.dto.OrderDetailResponse;
import com.b2bprocure.system.order.dto.OrderResponse;
import com.b2bprocure.system.order.dto.OrderStatusHistoryResponse;
import com.b2bprocure.system.order.dto.RejectOrderRequest;

import java.util.List;

public interface OrderLifecycleService {

    OrderResponse confirmOrder(Long orderId);

    OrderResponse rejectOrder(Long orderId, RejectOrderRequest request);

    OrderResponse cancelOrder(Long orderId, CancelOrderRequest request);

    OrderResponse updateToPreparing(Long orderId);

    OrderResponse updateToShipping(Long orderId);

    OrderResponse updateToCompleted(Long orderId);

    OrderDetailResponse getOrderDetail(Long orderId);

    List<OrderStatusHistoryResponse> getOrderStatusHistory(Long orderId);
}

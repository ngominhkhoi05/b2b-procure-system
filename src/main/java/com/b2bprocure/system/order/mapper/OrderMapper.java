package com.b2bprocure.system.order.mapper;

import com.b2bprocure.system.order.dto.OrderDetailResponse;
import com.b2bprocure.system.order.dto.OrderItemResponse;
import com.b2bprocure.system.order.dto.OrderResponse;
import com.b2bprocure.system.order.dto.OrderStatusHistoryResponse;
import com.b2bprocure.system.order.dto.PaymentSummaryResponse;
import com.b2bprocure.system.order.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    /**
     * Map an Order entity to an OrderResponse (list summary).
     *
     * The {@code paymentMethod} and {@code paymentStatus} fields are intentionally
     * NOT mapped here — they are populated by the service from a joined Payment
     * row to avoid an N+1 lookup. See {@code OrderLifecycleServiceImpl.getOrders}.
     */
    @Mapping(target = "buyerCompanyId", source = "buyerCompany.id")
    @Mapping(target = "supplierCompanyId", source = "supplierCompany.id")
    @Mapping(target = "createdBy", source = "createdBy.id")
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    OrderResponse toResponse(Order order);

    /**
     * Map an Order entity plus its related entities to a fully-populated OrderDetailResponse.
     *
     * The {@code payment}, {@code buyerCompanyName}, and {@code supplierCompanyName} fields
     * are populated by the caller (service) — we expose them as explicit parameters so the
     * service can fill them from already-fetched entities (avoiding any lazy-load N+1).
     */
    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "orderCode", source = "order.orderCode")
    @Mapping(target = "buyerCompanyId", source = "order.buyerCompany.id")
    @Mapping(target = "supplierCompanyId", source = "order.supplierCompany.id")
    @Mapping(target = "createdBy", source = "order.createdBy.id")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "subtotal", source = "order.subtotal")
    @Mapping(target = "commissionRate", source = "order.commissionRate")
    @Mapping(target = "commissionAmount", source = "order.commissionAmount")
    @Mapping(target = "totalAmount", source = "order.totalAmount")
    @Mapping(target = "shippingCompanyName", source = "order.shippingCompanyName")
    @Mapping(target = "shippingPhone", source = "order.shippingPhone")
    @Mapping(target = "shippingAddress", source = "order.shippingAddress")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "updatedAt", source = "order.updatedAt")
    @Mapping(target = "items", source = "items")
    @Mapping(target = "statusHistory", source = "statusHistory")
    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "buyerCompanyName", source = "buyerCompanyName")
    @Mapping(target = "supplierCompanyName", source = "supplierCompanyName")
    OrderDetailResponse toDetailResponse(
            Order order,
            List<OrderItemResponse> items,
            List<OrderStatusHistoryResponse> statusHistory,
            PaymentSummaryResponse payment,
            String buyerCompanyName,
            String supplierCompanyName
    );

}

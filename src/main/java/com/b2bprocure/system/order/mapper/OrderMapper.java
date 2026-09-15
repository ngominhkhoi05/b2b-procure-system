package com.b2bprocure.system.order.mapper;

import com.b2bprocure.system.order.dto.OrderDetailResponse;
import com.b2bprocure.system.order.dto.OrderItemResponse;
import com.b2bprocure.system.order.dto.OrderResponse;
import com.b2bprocure.system.order.dto.OrderStatusHistoryResponse;
import com.b2bprocure.system.order.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "buyerCompanyId", source = "buyerCompany.id")
    @Mapping(target = "supplierCompanyId", source = "supplierCompany.id")
    @Mapping(target = "createdBy", source = "createdBy.id")
    OrderResponse toResponse(Order order);

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
    OrderDetailResponse toDetailResponse(
            Order order,
            List<OrderItemResponse> items,
            List<OrderStatusHistoryResponse> statusHistory
    );

}

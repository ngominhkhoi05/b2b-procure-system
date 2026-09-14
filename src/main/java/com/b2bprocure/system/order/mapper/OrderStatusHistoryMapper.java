package com.b2bprocure.system.order.mapper;

import com.b2bprocure.system.order.dto.OrderStatusHistoryResponse;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderStatusHistoryMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "changedBy", source = "changedBy.id")
    OrderStatusHistoryResponse toResponse(OrderStatusHistory history);

    List<OrderStatusHistoryResponse> toResponseList(List<OrderStatusHistory> histories);

}

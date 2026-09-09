package com.b2bprocure.system.order.mapper;

import com.b2bprocure.system.order.dto.OrderItemResponse;
import com.b2bprocure.system.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productImageUrl", source = "product.imageUrl")
    OrderItemResponse toResponse(OrderItem orderItem);

    List<OrderItemResponse> toResponseList(List<OrderItem> orderItems);

}

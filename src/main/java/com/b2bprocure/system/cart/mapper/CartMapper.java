package com.b2bprocure.system.cart.mapper;

import com.b2bprocure.system.cart.dto.CartItemResponse;
import com.b2bprocure.system.cart.dto.CartResponse;
import com.b2bprocure.system.cart.entity.Cart;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CartMapper {

    @Mapping(target = "id", source = "cart.id")
    @Mapping(target = "userId", source = "cart.user.id")
    @Mapping(target = "createdAt", source = "cart.createdAt")
    @Mapping(target = "updatedAt", source = "cart.updatedAt")
    @Mapping(target = "items", source = "items")
    CartResponse toResponse(Cart cart, List<CartItemResponse> items);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "items", ignore = true)
    CartResponse toResponse(Cart cart);

}

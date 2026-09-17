package com.b2bprocure.system.cart.mapper;

import com.b2bprocure.system.cart.dto.CartItemResponse;
import com.b2bprocure.system.cart.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CartItemMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "productImageUrl", source = "product.imageUrl")
    @Mapping(target = "supplierCompanyId", source = "product.supplierCompany.id")
    @Mapping(target = "supplierCompanyName", source = "product.supplierCompany.name")
    @Mapping(target = "productStatus", source = "product.status")
    @Mapping(target = "categoryStatus", source = "product.category.status")
    @Mapping(target = "stockQuantity", source = "product.stockQuantity")
    @Mapping(target = "availableQuantity", source = "product.availableQuantity")
    @Mapping(target = "quantity", source = "quantity")
    @Mapping(target = "unitPrice", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    @Mapping(target = "available", ignore = true)
    CartItemResponse toResponse(CartItem cartItem);

    List<CartItemResponse> toResponseList(List<CartItem> cartItems);

}

package com.b2bprocure.system.cart.mapper;

import com.b2bprocure.system.cart.dto.CartItemResponse;
import com.b2bprocure.system.cart.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface CartMapper {

    @Mapping(target = "id", source = "cartItem.id")
    @Mapping(target = "productId", source = "cartItem.product.id")
    @Mapping(target = "sku", source = "cartItem.product.sku")
    @Mapping(target = "productName", source = "cartItem.product.name")
    @Mapping(target = "productImageUrl", source = "cartItem.product.imageUrl")
    @Mapping(target = "supplierCompanyId", source = "cartItem.product.supplierCompany.id")
    @Mapping(target = "supplierCompanyName", source = "cartItem.product.supplierCompany.name")
    @Mapping(target = "quantity", source = "cartItem.quantity")
    @Mapping(target = "productStatus", source = "cartItem.product.status")
    @Mapping(target = "categoryStatus", source = "cartItem.product.category.status")
    @Mapping(target = "stockQuantity", source = "cartItem.product.stockQuantity")
    @Mapping(target = "availableQuantity", source = "cartItem.product.availableQuantity")
    @Mapping(target = "unitPrice", source = "unitPrice")
    @Mapping(target = "subtotal", source = "subtotal")
    @Mapping(target = "available", source = "available")
    CartItemResponse toItemResponse(CartItem cartItem, BigDecimal unitPrice, BigDecimal subtotal, Boolean available);

}

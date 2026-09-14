package com.b2bprocure.system.cart.service;

import com.b2bprocure.system.cart.dto.AddToCartRequest;
import com.b2bprocure.system.cart.dto.CartItemResponse;
import com.b2bprocure.system.cart.dto.CartResponse;
import com.b2bprocure.system.cart.dto.UpdateCartItemRequest;

public interface CartService {

    CartResponse getCart();

    CartItemResponse addToCart(AddToCartRequest request);

    CartItemResponse updateCartItem(Long productId, UpdateCartItemRequest request);

    void removeCartItem(Long productId);

    void clearCart();

}

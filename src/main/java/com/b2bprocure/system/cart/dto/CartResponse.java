package com.b2bprocure.system.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cart response containing items, total amount, and item count")
public class CartResponse {

    @Schema(description = "Cart ID", example = "1")
    private Long cartId;

    @Builder.Default
    @Schema(description = "List of cart items")
    private List<CartItemResponse> items = new ArrayList<>();

    @Schema(description = "Total amount of all available items in cart", example = "9000000.00")
    private BigDecimal totalAmount;

    @Schema(description = "Total number of distinct items in cart", example = "1")
    private Integer totalItems;

}

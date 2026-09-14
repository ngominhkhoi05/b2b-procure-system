package com.b2bprocure.system.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to add a product to cart")
public class AddToCartRequest {

    @NotNull(message = "Product ID is required")
    @Schema(description = "ID of the product to add", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than zero")
    @Schema(description = "Quantity to add", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

}

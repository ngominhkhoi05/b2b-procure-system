package com.b2bprocure.system.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cart item response with dynamically calculated price and availability")
public class CartItemResponse {

    @Schema(description = "Product ID", example = "10")
    private Long productId;

    @Schema(description = "Product SKU", example = "SKU-1001")
    private String sku;

    @Schema(description = "Product Name", example = "Product A")
    private String productName;

    @Schema(description = "Product Image URL", example = "https://example.com/image.png")
    private String productImageUrl;

    @Schema(description = "Supplier Company ID", example = "100")
    private Long supplierCompanyId;

    @Schema(description = "Supplier Company Name", example = "ABC Supplier")
    private String supplierCompanyName;

    @Schema(description = "Quantity in cart", example = "100")
    private Integer quantity;

    @Schema(description = "Unit price based on product price tiers and current quantity", example = "90000.00")
    private BigDecimal unitPrice;

    @Schema(description = "Subtotal (quantity * unitPrice)", example = "9000000.00")
    private BigDecimal subtotal;

    @Schema(description = "Current product status", example = "ACTIVE")
    private String productStatus;

    @Schema(description = "Current category status", example = "ACTIVE")
    private String categoryStatus;

    @Schema(description = "Current stock quantity available", example = "150")
    private Integer stockQuantity;

    @Schema(description = "Whether item is available (product active, category active, sufficient stock)", example = "true")
    private Boolean available;

}

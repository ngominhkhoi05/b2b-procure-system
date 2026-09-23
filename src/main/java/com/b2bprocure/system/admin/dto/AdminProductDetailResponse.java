package com.b2bprocure.system.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.b2bprocure.system.product.dto.ProductPriceResponse;
import com.b2bprocure.system.product.dto.ProductResponse;

import java.util.List;

/**
 * Step 8 — Admin Product Detail response.
 *
 * Embeds the standard ProductResponse plus the full list of price tiers
 * for admin visibility. Price tiers are not included in the standard
 * ProductResponse to keep list queries lightweight.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminProductDetailResponse {

    /**
     * Full product detail as returned by the standard ProductResponse.
     * Includes supplier, category, stock, reserved, status, etc.
     */
    private ProductResponse product;

    /**
     * All price tiers for this product, ordered by minQuantity ascending.
     */
    private List<ProductPriceResponse> priceTiers;
}

package com.b2bprocure.system.product.service;

import com.b2bprocure.system.product.dto.CreateProductPriceRequest;
import com.b2bprocure.system.product.dto.ProductPriceResponse;
import com.b2bprocure.system.product.dto.UpdateProductPriceRequest;

import java.util.List;
import java.util.Optional;

public interface ProductPriceService {

    ProductPriceResponse createPriceTier(Long productId, CreateProductPriceRequest request);

    List<ProductPriceResponse> getPriceTiers(Long productId);

    ProductPriceResponse updatePriceTier(Long productId, Long priceId, UpdateProductPriceRequest request);

    void deletePriceTier(Long productId, Long priceId);

    Optional<ProductPriceResponse> findPriceForQuantity(Long productId, Integer quantity);

}

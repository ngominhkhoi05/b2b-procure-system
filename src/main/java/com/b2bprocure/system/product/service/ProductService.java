package com.b2bprocure.system.product.service;

import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.product.dto.CreateProductRequest;
import com.b2bprocure.system.product.dto.ProductResponse;
import com.b2bprocure.system.product.dto.ProductSearchRequest;
import com.b2bprocure.system.product.dto.ProductStatusUpdateRequest;
import com.b2bprocure.system.product.dto.UpdateProductRequest;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    PageResponse<ProductResponse> getProducts(ProductSearchRequest request, Pageable pageable);

    ProductResponse getProductById(Long id);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    ProductResponse updateProductStatus(Long id, ProductStatusUpdateRequest request);

}

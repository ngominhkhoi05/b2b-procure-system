package com.b2bprocure.system.product.service;

import com.b2bprocure.system.admin.dto.AdminProductDetailResponse;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.response.SliceResponse;
import com.b2bprocure.system.product.dto.CreateProductRequest;
import com.b2bprocure.system.product.dto.ProductResponse;
import com.b2bprocure.system.product.dto.ProductSearchRequest;
import com.b2bprocure.system.product.dto.ProductStatusUpdateRequest;
import com.b2bprocure.system.product.dto.UpdateProductRequest;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    /**
     * List products. The concrete response type (Page or Slice) depends on
     * the caller's intent, not the role; callers decide by calling
     * {@link #getProductsSlice} (no count) or this method (with count).
     */
    PageResponse<ProductResponse> getProducts(ProductSearchRequest request, Pageable pageable);

    /**
     * Slice variant of {@link #getProducts}: drops the {@code count(*)}
     * query entirely by fetching pageSize + 1 rows and detecting
     * {@code hasNext} from the result size. Use for infinite-scroll UIs
     * (BUYER browse page) where the absolute total is not displayed.
     */
    SliceResponse<ProductResponse> getProductsSlice(ProductSearchRequest request, Pageable pageable);

    ProductResponse getProductById(Long id);

    /**
     * Step 8 — Admin Product Detail: returns full product detail plus all price tiers.
     */
    AdminProductDetailResponse getProductDetailForAdmin(Long id);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    ProductResponse updateProductStatus(Long id, ProductStatusUpdateRequest request);
}

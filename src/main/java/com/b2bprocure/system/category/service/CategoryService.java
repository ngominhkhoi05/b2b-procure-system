package com.b2bprocure.system.category.service;

import com.b2bprocure.system.category.dto.CategoryResponse;
import com.b2bprocure.system.category.dto.CategoryStatusUpdateRequest;
import com.b2bprocure.system.category.dto.CreateCategoryRequest;
import com.b2bprocure.system.category.dto.UpdateCategoryRequest;
import com.b2bprocure.system.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

    CategoryResponse createCategory(CreateCategoryRequest request);

    PageResponse<CategoryResponse> getCategories(String status, String keyword, Pageable pageable);

    CategoryResponse getCategoryById(Long id);

    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);

    CategoryResponse updateCategoryStatus(Long id, CategoryStatusUpdateRequest request);

    /**
     * Step 8 — Admin Category Delete.
     *
     * Only succeeds when no Product references the category.
     * Returns void (no entity retrieval needed by caller).
     */
    void deleteCategory(Long id);
}

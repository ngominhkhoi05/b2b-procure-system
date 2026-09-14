package com.b2bprocure.system.category.service;

import com.b2bprocure.system.category.dto.CategoryResponse;
import com.b2bprocure.system.category.dto.CategoryStatusUpdateRequest;
import com.b2bprocure.system.category.dto.CreateCategoryRequest;
import com.b2bprocure.system.category.dto.UpdateCategoryRequest;
import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.mapper.CategoryMapper;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can create categories");
        }

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new BusinessException("Category name is required", HttpStatus.BAD_REQUEST);
        }

        if (categoryRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new BusinessException("Category name already exists: " + trimmedName, HttpStatus.CONFLICT);
        }

        Category category = categoryMapper.toEntity(request);
        category.setName(trimmedName);
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription().trim());
        }
        category.setStatus("ACTIVE");
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created successfully with id: {}", savedCategory.getId());
        return categoryMapper.toResponse(savedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> getCategories(String status, String keyword, Pageable pageable) {
        Page<Category> categoryPage;

        if (SecurityUtil.isAdmin()) {
            String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
            String pattern = (keyword != null && !keyword.isBlank()) ? "%" + keyword.trim().toLowerCase() + "%" : null;

            if (normalizedStatus != null && pattern == null) {
                categoryPage = categoryRepository.findByStatus(normalizedStatus, pageable);
            } else if (normalizedStatus == null && pattern == null) {
                categoryPage = categoryRepository.findAll(pageable);
            } else {
                categoryPage = categoryRepository.searchCategories(normalizedStatus, pattern, pageable);
            }
        } else {
            // BUYER and SUPPLIER only see ACTIVE categories directly from database query
            String pattern = (keyword != null && !keyword.isBlank()) ? "%" + keyword.trim().toLowerCase() + "%" : null;
            if (pattern != null) {
                categoryPage = categoryRepository.searchCategories("ACTIVE", pattern, pageable);
            } else {
                categoryPage = categoryRepository.findByStatus("ACTIVE", pageable);
            }
        }

        List<CategoryResponse> content = categoryPage.getContent().stream()
                .map(categoryMapper::toResponse)
                .toList();

        return PageResponse.of(categoryPage, content);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category;
        if (SecurityUtil.isAdmin()) {
            category = categoryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        } else {
            // BUYER and SUPPLIER only see ACTIVE categories.
            // If INACTIVE or non-existent, return 404 to avoid leaking existence.
            category = categoryRepository.findByIdAndStatus(id, "ACTIVE")
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        }

        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can update categories");
        }

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new BusinessException("Category name is required", HttpStatus.BAD_REQUEST);
        }

        if (!trimmedName.equalsIgnoreCase(category.getName())) {
            if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
                throw new BusinessException("Category name already exists: " + trimmedName, HttpStatus.CONFLICT);
            }
        }

        categoryMapper.updateEntity(request, category);
        category.setName(trimmedName);
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription().trim());
        }
        category.setUpdatedAt(LocalDateTime.now());

        Category savedCategory = categoryRepository.save(category);
        log.info("Category updated successfully with id: {}", savedCategory.getId());
        return categoryMapper.toResponse(savedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategoryStatus(Long id, CategoryStatusUpdateRequest request) {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Access denied: Only administrators can update category status");
        }

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new BusinessException("Status is required", HttpStatus.BAD_REQUEST);
        }

        String normalizedStatus = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
            throw new BusinessException(
                    "Invalid category status: " + request.getStatus() + ". Allowed statuses: ACTIVE, INACTIVE",
                    HttpStatus.BAD_REQUEST
            );
        }

        category.setStatus(normalizedStatus);
        category.setUpdatedAt(LocalDateTime.now());

        Category savedCategory = categoryRepository.save(category);
        log.info("Category status updated to {} for id: {}", normalizedStatus, savedCategory.getId());
        return categoryMapper.toResponse(savedCategory);
    }

}

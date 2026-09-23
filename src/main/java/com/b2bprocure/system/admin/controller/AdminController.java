package com.b2bprocure.system.admin.controller;

import com.b2bprocure.system.admin.dto.AdminCompanyResponse;
import com.b2bprocure.system.admin.dto.AdminProductDetailResponse;
import com.b2bprocure.system.admin.dto.AdminStatisticsResponse;
import com.b2bprocure.system.admin.dto.CommissionRateResponse;
import com.b2bprocure.system.admin.dto.CreateCommissionRateRequest;
import com.b2bprocure.system.admin.service.AdminCommissionRateService;
import com.b2bprocure.system.admin.service.AdminStatisticsService;
import com.b2bprocure.system.category.dto.CategoryResponse;
import com.b2bprocure.system.category.dto.CategoryStatusUpdateRequest;
import com.b2bprocure.system.category.dto.CreateCategoryRequest;
import com.b2bprocure.system.category.dto.UpdateCategoryRequest;
import com.b2bprocure.system.category.service.CategoryService;
import com.b2bprocure.system.common.response.ApiResponse;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.company.dto.CompanyResponse;
import com.b2bprocure.system.company.dto.CompanyStatusUpdateRequest;
import com.b2bprocure.system.company.service.CompanyService;
import com.b2bprocure.system.order.dto.OrderDetailResponse;
import com.b2bprocure.system.order.dto.OrderResponse;
import com.b2bprocure.system.order.service.OrderLifecycleService;
import com.b2bprocure.system.product.dto.CreateProductRequest;
import com.b2bprocure.system.product.dto.ProductResponse;
import com.b2bprocure.system.product.dto.ProductSearchRequest;
import com.b2bprocure.system.product.dto.ProductStatusUpdateRequest;
import com.b2bprocure.system.product.dto.UpdateProductRequest;
import com.b2bprocure.system.product.service.ProductService;
import com.b2bprocure.system.user.dto.UserResponse;
import com.b2bprocure.system.user.dto.UserStatusUpdateRequest;
import com.b2bprocure.system.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Step 8 — Admin Management controller.
 *
 * Single entry point for all admin-scoped operations: User, Company, Product, Category,
 * Order (read-only), Commission Rate, and basic Statistics.
 *
 * All endpoints require {@code ROLE_ADMIN}. Per-method authorization is enforced
 * via {@code @PreAuthorize} at the controller boundary AND in the service layer.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin Management APIs")
public class AdminController {

    private final UserService userService;
    private final CompanyService companyService;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final OrderLifecycleService orderLifecycleService;
    private final AdminCommissionRateService adminCommissionRateService;
    private final AdminStatisticsService adminStatisticsService;

    // =========================================================================
    // USER MANAGEMENT
    // =========================================================================

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<UserResponse> response = userService.getUsers(role, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", response));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId) {
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", response));
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UserStatusUpdateRequest request
    ) {
        UserResponse response = userService.updateUserStatus(userId, request);
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", response));
    }

    // =========================================================================
    // COMPANY MANAGEMENT
    // =========================================================================

    @GetMapping("/companies")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CompanyResponse>>> listCompanies(
            @RequestParam(required = false) String companyType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<CompanyResponse> response = companyService.getCompanies(companyType, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("Companies retrieved successfully", response));
    }

    @GetMapping("/companies/{companyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminCompanyResponse>> getCompany(@PathVariable Long companyId) {
        AdminCompanyResponse response = companyService.getCompanyByIdForAdmin(companyId);
        return ResponseEntity.ok(ApiResponse.success("Company retrieved successfully", response));
    }

    @PatchMapping("/companies/{companyId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompanyStatus(
            @PathVariable Long companyId,
            @Valid @RequestBody CompanyStatusUpdateRequest request
    ) {
        CompanyResponse response = companyService.updateCompanyStatus(companyId, request);
        return ResponseEntity.ok(ApiResponse.success("Company status updated successfully", response));
    }

    // =========================================================================
    // PRODUCT MANAGEMENT
    // =========================================================================

    @GetMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierCompanyId,
            @RequestParam(required = false) String status,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .supplierCompanyId(supplierCompanyId)
                .status(status)
                .build();
        PageResponse<ProductResponse> response = productService.getProducts(request, pageable);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", response));
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminProductDetailResponse>> getProduct(@PathVariable Long productId) {
        AdminProductDetailResponse response = productService.getProductDetailForAdmin(productId);
        return ResponseEntity.ok(ApiResponse.success("Product detail retrieved successfully", response));
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.ok(ApiResponse.success("Product created successfully", response));
    }

    @PutMapping("/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductResponse response = productService.updateProduct(productId, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @PatchMapping("/products/{productId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProductStatus(
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        ProductResponse response = productService.updateProductStatus(productId, request);
        return ResponseEntity.ok(ApiResponse.success("Product status updated successfully", response));
    }

    // =========================================================================
    // CATEGORY MANAGEMENT
    // =========================================================================

    @GetMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> listCategories(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<CategoryResponse> response = categoryService.getCategories(status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", response));
    }

    @GetMapping("/categories/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable Long categoryId) {
        CategoryResponse response = categoryService.getCategoryById(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Category retrieved successfully", response));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.ok(ApiResponse.success("Category created successfully", response));
    }

    @PutMapping("/categories/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        CategoryResponse response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    @PatchMapping("/categories/{categoryId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategoryStatus(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryStatusUpdateRequest request
    ) {
        CategoryResponse response = categoryService.updateCategoryStatus(categoryId, request);
        return ResponseEntity.ok(ApiResponse.success("Category status updated successfully", response));
    }

    @DeleteMapping("/categories/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully"));
    }

    // =========================================================================
    // ORDER MANAGEMENT — read-only
    // =========================================================================

    /**
     * Admin Order list. Reuses {@code OrderLifecycleService.getOrders(...)} with
     * all role-filter parameters null, which disables the BUYER/SUPPLIER visibility
     * filter inside the SQL query.
     *
     * NO state transition endpoints exposed — Admin does NOT modify Order state from
     * this controller. State changes remain only in OrderLifecycleController endpoints
     * (auth-by-supplier/buyer — Admin still cannot invoke them anyway).
     */
    @GetMapping("/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<OrderResponse> response = orderLifecycleService.getOrders(
                status, paymentMethod, paymentStatus, fromDate, toDate, pageable);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", response));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(@PathVariable Long orderId) {
        OrderDetailResponse response = orderLifecycleService.getOrderDetail(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order detail retrieved successfully", response));
    }

    // =========================================================================
    // COMMISSION RATE MANAGEMENT
    // =========================================================================

    @GetMapping("/commission-rates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CommissionRateResponse>>> listCommissionRates(
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "effectiveFrom", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<CommissionRateResponse> response = adminCommissionRateService.listRates(pageable);
        return ResponseEntity.ok(ApiResponse.success("Commission rates retrieved successfully", response));
    }

    @PostMapping("/commission-rates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionRateResponse>> createCommissionRate(
            @Valid @RequestBody CreateCommissionRateRequest request
    ) {
        CommissionRateResponse response = adminCommissionRateService.createRate(request);
        return ResponseEntity.ok(ApiResponse.success("Commission rate created successfully", response));
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    @GetMapping("/statistics/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminStatisticsResponse>> getStatisticsOverview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        AdminStatisticsResponse response = adminStatisticsService.getOverview(fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", response));
    }
}

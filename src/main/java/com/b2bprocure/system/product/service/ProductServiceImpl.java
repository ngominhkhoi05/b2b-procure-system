package com.b2bprocure.system.product.service;

import com.b2bprocure.system.admin.dto.AdminProductDetailResponse;
import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.common.constant.SecurityConstants;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.response.SliceResponse;
import com.b2bprocure.system.common.storage.CloudinaryStorageService;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.product.dto.CreateProductRequest;
import com.b2bprocure.system.product.dto.ProductPriceResponse;
import com.b2bprocure.system.product.dto.ProductResponse;
import com.b2bprocure.system.product.dto.ProductSearchRequest;
import com.b2bprocure.system.product.dto.ProductStatusUpdateRequest;
import com.b2bprocure.system.product.dto.UpdateProductRequest;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
import com.b2bprocure.system.product.mapper.ProductMapper;
import com.b2bprocure.system.product.mapper.ProductPriceMapper;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final CategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ProductMapper productMapper;
    private final ProductPriceMapper productPriceMapper;
    private final CloudinaryStorageService cloudinaryStorage;

    private User getCurrentAuthenticatedUser() {
        Optional<Long> userIdOpt = SecurityUtil.getCurrentUserId();
        if (userIdOpt.isPresent()) {
            return userRepository.findByIdWithRoleAndCompany(userIdOpt.get())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userIdOpt.get()));
        }
        String username = SecurityUtil.getCurrentUsernameOrThrow();
        return userRepository.findByUsernameWithRoleAndCompany(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName);
        boolean isSupplier = SecurityConstants.ROLE_SUPPLIER.equalsIgnoreCase(roleName);

        if (!isAdmin && !isSupplier) {
            throw new AccessDeniedException("Access denied: You do not have permission to create products");
        }

        Company supplierCompany;
        if (isAdmin) {
            if (request.getSupplierCompanyId() == null) {
                throw new BusinessException("Supplier company ID is required for administrator", HttpStatus.BAD_REQUEST);
            }
            supplierCompany = companyRepository.findById(request.getSupplierCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Company", "id", request.getSupplierCompanyId()));
            if (!"SUPPLIER".equalsIgnoreCase(supplierCompany.getCompanyType())) {
                throw new BusinessException("Company must be of type SUPPLIER", HttpStatus.BAD_REQUEST);
            }
        } else {
            if (currentUser.getCompany() == null) {
                throw new BusinessException("Current user does not belong to any supplier company", HttpStatus.BAD_REQUEST);
            }
            if (!"SUPPLIER".equalsIgnoreCase(currentUser.getCompany().getCompanyType())) {
                throw new BusinessException("Current user's company must be of type SUPPLIER", HttpStatus.BAD_REQUEST);
            }
            supplierCompany = currentUser.getCompany();
        }

        if (request.getCategoryId() == null) {
            throw new BusinessException("Category ID is required", HttpStatus.BAD_REQUEST);
        }
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
        if (!"ACTIVE".equalsIgnoreCase(category.getStatus())) {
            throw new BusinessException("Cannot create product with INACTIVE category", HttpStatus.BAD_REQUEST);
        }

        String trimmedSku = request.getSku() != null ? request.getSku().trim() : "";
        if (trimmedSku.isEmpty()) {
            throw new BusinessException("SKU is required", HttpStatus.BAD_REQUEST);
        }
        if (productRepository.existsBySkuIgnoreCase(trimmedSku)) {
            throw new BusinessException("SKU already exists: " + trimmedSku, HttpStatus.CONFLICT);
        }

        int stockQuantity = 0;
        if (request.getStockQuantity() != null) {
            if (request.getStockQuantity() < 0) {
                throw new BusinessException("Stock quantity must be zero or positive", HttpStatus.BAD_REQUEST);
            }
            stockQuantity = request.getStockQuantity();
        }

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new BusinessException("Product name is required", HttpStatus.BAD_REQUEST);
        }

        Product product = productMapper.toEntity(request);
        product.setSupplierCompany(supplierCompany);
        product.setCategory(category);
        product.setSku(trimmedSku);
        product.setName(trimmedName);
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription().trim());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl().trim());
        }
        if (request.getImagePublicId() != null) {
            String trimmedPublicId = request.getImagePublicId().trim();
            product.setImagePublicId(trimmedPublicId.isEmpty() ? null : trimmedPublicId);
        }
        product.setStockQuantity(stockQuantity);
        product.setStatus("ACTIVE");
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully with id: {}, sku: {}", savedProduct.getId(), savedProduct.getSku());
        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProducts(ProductSearchRequest request, Pageable pageable) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName);
        boolean isSupplier = SecurityConstants.ROLE_SUPPLIER.equalsIgnoreCase(roleName);
        boolean isBuyer = SecurityConstants.ROLE_BUYER.equalsIgnoreCase(roleName);

        Long supplierCompanyId;
        Long categoryId = request.getCategoryId();
        String status;
        String categoryStatus;

        if (isAdmin) {
            supplierCompanyId = request.getSupplierCompanyId();
            status = (request.getStatus() != null && !request.getStatus().isBlank())
                    ? request.getStatus().trim().toUpperCase()
                    : null;
            categoryStatus = null; // Admin can view products even if their category is INACTIVE
        } else if (isSupplier) {
            if (currentUser.getCompany() == null) {
                throw new BusinessException("Current user does not belong to any supplier company", HttpStatus.BAD_REQUEST);
            }
            // Supplier only sees products belonging to own company
            supplierCompanyId = currentUser.getCompany().getId();
            status = (request.getStatus() != null && !request.getStatus().isBlank())
                    ? request.getStatus().trim().toUpperCase()
                    : null;
            categoryStatus = null; // Supplier can view their own products even if their category is INACTIVE
        } else if (isBuyer) {
            supplierCompanyId = request.getSupplierCompanyId();
            // Buyer ONLY sees ACTIVE products whose category is ACTIVE
            status = "ACTIVE";
            categoryStatus = "ACTIVE";
        } else {
            throw new AccessDeniedException("Access denied: You do not have permission to view products");
        }

        // Tier 1: rely on the partial index `idx_products_listable_browse`.
        //   - BUYER: only listable rows (status=ACTIVE AND has prices) → listableOnly=true
        //   - Admin/Supplier: any status / any price state → listableOnly=false.
        //   The DB keeps `is_listable` in sync via triggers (V20 migration).
        boolean listableOnly = isBuyer;

        // Keyword is the trigger that switches between LIKE (small data, low cost)
        // and FTS (1M scale, GIN index). Trim once here so both paths share the
        // exact same input. Empty / blank keyword -> null = no search applied.
        String keyword = (request.getKeyword() != null && !request.getKeyword().isBlank())
                ? request.getKeyword().trim()
                : null;

        // Step 1: page over ids. Two paths:
        //   - keyword present -> FTS (Postgres websearch_to_tsquery + ts_rank)
        //   - keyword absent  -> existing JPQL filter-only search (unchanged)
        List<Long> ids;
        long totalElements;

        if (keyword != null) {
            // The FTS query has its own ORDER BY (ts_rank DESC). If we let the
            // caller's Pageable.sort through, Spring Data will append another
            // ORDER BY clause and Postgres will reject the query. Strip sort
            // for this path; ranking is the only ordering that makes sense
            // when a keyword is present.
            Pageable ftsPageable = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize()
            );
            Page<Long> idPage = productRepository.searchProductIdsByFts(
                    supplierCompanyId,
                    categoryId,
                    status,
                    categoryStatus,
                    keyword,
                    isBuyer,
                    ftsPageable
            );
            ids = idPage.getContent();
            totalElements = idPage.getTotalElements();
        } else {
            Page<Product> productPage = productRepository.searchProducts(
                    supplierCompanyId,
                    categoryId,
                    status,
                    categoryStatus,
                    null,
                    listableOnly,
                    pageable
            );
            ids = productPage.getContent().stream().map(Product::getId).toList();
            totalElements = productPage.getTotalElements();
        }

        return getProductsContent(ids, pageable, totalElements);
    }

    @Override
    @Transactional(readOnly = true)
    public SliceResponse<ProductResponse> getProductsSlice(ProductSearchRequest request, Pageable pageable) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isBuyer = SecurityConstants.ROLE_BUYER.equalsIgnoreCase(roleName);

        // The Slice path is only meaningful for BUYER browse; force the same
        // visibility filter regardless of what the client sent. Admin/Supplier
        // callers should use getProducts() which preserves totalElements.
        if (!isBuyer) {
            throw new BusinessException("Slice variant is only available for BUYER", HttpStatus.FORBIDDEN);
        }

        Long categoryId = request.getCategoryId();
        Long supplierCompanyId = request.getSupplierCompanyId();
        String keyword = (request.getKeyword() != null && !request.getKeyword().isBlank())
                ? request.getKeyword().trim()
                : null;
        String pattern = null;
        if (keyword != null) {
            pattern = "%" + keyword.toLowerCase() + "%";
        }

        // Fetch pageSize + 1 rows to detect hasNext without running count(*).
        Pageable slicePageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize() + 1,
                pageable.getSort()
        );

        // For the FTS path, sort comes from the query itself (ts_rank DESC);
        // Pageable.sort is ignored. For LIKE path, the sort from the caller is
        // honored by the partial index ordering.
        Pageable slicePageableNoSort = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize() + 1
        );

        List<Long> ids;
        boolean hasNext;

        if (keyword != null) {
            List<Long> rawIds = productRepository.searchProductIdsSliceByFts(
                    supplierCompanyId,
                    categoryId,
                    keyword,
                    slicePageableNoSort
            );
            ids = trimToPageSize(rawIds, pageable.getPageSize());
            hasNext = rawIds.size() > pageable.getPageSize();
        } else {
            List<Product> rawProducts = productRepository.searchProductSlice(
                    supplierCompanyId,
                    categoryId,
                    pattern,
                    slicePageable
            );
            ids = rawProducts.stream().map(Product::getId).toList();
            ids = trimToPageSize(ids, pageable.getPageSize());
            hasNext = rawProducts.size() > pageable.getPageSize();
        }

        if (ids.isEmpty()) {
            return SliceResponse.of(List.of(), pageable, false);
        }

        // Build the actual response list by reusing the existing pipeline.
        List<Product> products = productRepository.findAllByIdInWithDetails(ids);

        // Reorder to match the ranking from the search query (Hibernate does
        // not guarantee ordering across a JOIN FETCH).
        Map<Long, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Product> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        List<ProductPrice> allTiers = batchFetchPriceTiers(ordered.stream().map(Product::getId).toList());
        priceTiersByProductId = allTiers.stream()
                .collect(Collectors.groupingBy(pp -> pp.getProduct().getId()));

        List<ProductResponse> content = ordered.stream()
                .map(productMapper::toResponse)
                .map(this::attachPriceSummary)
                .toList();

        return SliceResponse.of(content, pageable, hasNext);
    }

    /**
     * Shared content pipeline for both {@link #getProducts} and
     * {@link #getProductsSlice}: fetch full Products with relationships,
     * reorder to match the input id list, batch-fetch price tiers, build
     * response DTOs. Wrapped in a small helper so the two paths share
     * exactly the same logic and cannot drift apart.
     */
    private PageResponse<ProductResponse> getProductsContent(List<Long> ids, Pageable pageable, long totalElements) {
        // Step 2: batch fetch Products with relationships in 1 SQL query
        // (regardless of page size). Skip when ids is empty so we do not
        // issue a "WHERE id IN ()" query which is invalid in some DBs.
        if (ids.isEmpty()) {
            return PageResponse.of(List.of(), pageable, totalElements);
        }

        List<Product> products = productRepository.findAllByIdInWithDetails(ids);

        // Reorder to match the ranking returned by FTS. The JPQL findAllByIdInWithDetails
        // does not preserve the caller's ordering (Hibernate returns rows in an
        // unspecified order); for the FTS path the ordering is meaningful (ts_rank DESC),
        // so we restore it here.
        Map<Long, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Product> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        // Batch-fetch price tiers for the whole page in 1 SQL query (no N+1).
        // Result is grouped by productId so attachPriceSummary is O(1) per product.
        List<ProductPrice> allTiers = batchFetchPriceTiers(ordered.stream().map(Product::getId).toList());
        priceTiersByProductId = allTiers.stream()
                .collect(Collectors.groupingBy(pp -> pp.getProduct().getId()));

        List<ProductResponse> content = ordered.stream()
                .map(productMapper::toResponse)
                .map(this::attachPriceSummary)
                .toList();

        return PageResponse.of(content, pageable, totalElements);
    }

    /**
     * If the list has more than pageSize items (because we asked for
     * pageSize + 1), drop the extra. Otherwise return as-is.
     */
    private static List<Long> trimToPageSize(List<Long> ids, int pageSize) {
        if (ids.size() > pageSize) {
            return ids.subList(0, pageSize);
        }
        return ids;
    }

    /**
     * Attach price summary (priceFrom, priceTo, tierCount) to a ProductResponse.
     *
     * Single SQL query for the whole page (already executed earlier via
     * {@link #batchFetchPriceTiers(List)}), so this method is in-memory
     * lookup only — no extra round-trip per product.
     *
     * If a product has no tiers (filtered out for BUYER anyway, but still
     * possible for admin/supplier views), the three fields stay null.
     */
    private List<ProductPrice> batchFetchPriceTiers(List<Long> productIds) {
        if (productIds.isEmpty()) return List.of();
        return productPriceRepository.findByProductIdInOrderByMinQuantityAsc(productIds);
    }

    private ProductResponse attachPriceSummary(ProductResponse response) {
        List<ProductPrice> tiers = priceTiersByProductId.get(response.getId());
        if (tiers == null || tiers.isEmpty()) {
            // Leave priceFrom/priceTo/tierCount as null) — mapper default.
            return response;
        }
        // Already sorted ASC by minQuantity from the query, so [0] is min and
        // [last] is max unitPrice within the tier set for that product.
        BigDecimal minPrice = tiers.get(0).getUnitPrice();
        BigDecimal maxPrice = tiers.get(tiers.size() - 1).getUnitPrice();
        response.setPriceFrom(minPrice);
        response.setPriceTo(maxPrice);
        response.setTierCount(tiers.size());
        return response;
    }

    /** Populated by getProducts() before attachPriceSummary runs. */
    private Map<Long, List<ProductPrice>> priceTiersByProductId = Map.of();

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName);
        boolean isSupplier = SecurityConstants.ROLE_SUPPLIER.equalsIgnoreCase(roleName);
        boolean isBuyer = SecurityConstants.ROLE_BUYER.equalsIgnoreCase(roleName);

        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (isAdmin) {
            return productMapper.toResponse(product);
        }

        if (isSupplier) {
            if (currentUser.getCompany() == null || !product.getSupplierCompany().getId().equals(currentUser.getCompany().getId())) {
                throw new AccessDeniedException("Access denied: You do not have permission to access this product");
            }
            return productMapper.toResponse(product);
        }

        if (isBuyer) {
            // Buyer only sees ACTIVE product whose category is also ACTIVE
            // Return 404 to avoid leaking existence
            if (!"ACTIVE".equalsIgnoreCase(product.getStatus()) || !"ACTIVE".equalsIgnoreCase(product.getCategory().getStatus())) {
                throw new ResourceNotFoundException("Product", "id", id);
            }
            // Buyer also requires the product to have at least one price_product
            if (!productPriceRepository.existsByProductId(id)) {
                throw new ResourceNotFoundException("Product", "id", id);
            }
            return productMapper.toResponse(product);
        }

        throw new AccessDeniedException("Access denied: You do not have permission to access this product");
    }

    @Override
    @Transactional(readOnly = true)
    public AdminProductDetailResponse getProductDetailForAdmin(Long id) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        if (!SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Access denied: Only administrators can access this endpoint");
        }

        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        ProductResponse productResponse = productMapper.toResponse(product);

        // Fetch price tiers: one batched SQL query (sorted asc by minQuantity).
        // No N+1: a single call to findByProductIdOrderByMinQuantityAsc.
        List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByMinQuantityAsc(id);
        List<ProductPriceResponse> priceTiers = prices.stream()
                .map(productPriceMapper::toResponse)
                .toList();

        return AdminProductDetailResponse.builder()
                .product(productResponse)
                .priceTiers(priceTiers)
                .build();
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName);
        boolean isSupplier = SecurityConstants.ROLE_SUPPLIER.equalsIgnoreCase(roleName);

        if (!isAdmin && !isSupplier) {
            throw new AccessDeniedException("Access denied: You do not have permission to update products");
        }

        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (isSupplier) {
            if (currentUser.getCompany() == null || !product.getSupplierCompany().getId().equals(currentUser.getCompany().getId())) {
                throw new AccessDeniedException("Access denied: You do not have permission to update this product");
            }
        }

        // Validate and update category if changed
        if (request.getCategoryId() != null && !request.getCategoryId().equals(product.getCategory().getId())) {
            Category newCategory = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            if (!"ACTIVE".equalsIgnoreCase(newCategory.getStatus())) {
                throw new BusinessException("Cannot update product to an INACTIVE category", HttpStatus.BAD_REQUEST);
            }
            product.setCategory(newCategory);
        }

        // Validate and update SKU if provided and changed
        if (request.getSku() != null && !request.getSku().isBlank()) {
            String trimmedSku = request.getSku().trim();
            if (!trimmedSku.equalsIgnoreCase(product.getSku())) {
                if (productRepository.existsBySkuIgnoreCaseAndIdNot(trimmedSku, id)) {
                    throw new BusinessException("SKU already exists: " + trimmedSku, HttpStatus.CONFLICT);
                }
                product.setSku(trimmedSku);
            }
        }

        // Validate stock quantity if provided
        if (request.getStockQuantity() != null) {
            if (request.getStockQuantity() < 0) {
                throw new BusinessException("Stock quantity must be zero or positive", HttpStatus.BAD_REQUEST);
            }
            int currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
            if (request.getStockQuantity() < currentReserved) {
                throw new BusinessException(
                        String.format("Stock quantity (%d) cannot be less than currently reserved quantity (%d)",
                                request.getStockQuantity(), currentReserved),
                        HttpStatus.BAD_REQUEST
                );
            }
            product.setStockQuantity(request.getStockQuantity());
        }

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new BusinessException("Product name is required", HttpStatus.BAD_REQUEST);
        }

        // Snapshot old image references BEFORE the mapper overwrites them.
        // (The mapper only writes simple fields with null=ignore semantics;
        // imagePublicId is ignored entirely by the mapper and handled below.)
        String oldImageUrl      = product.getImageUrl();
        String oldImagePublicId = product.getImagePublicId();

        productMapper.updateEntity(request, product);
        product.setName(trimmedName);
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription().trim());
        }

        // Handle product image replace / clear with Cloudinary cleanup.
        // Only meaningful when the request actually carries image fields;
        // for a SKU-only update, both newUrl and newPublicId are null and
        // this is a no-op.
        replaceProductImage(
            product,
            request.getImageUrl(),
            request.getImagePublicId(),
            oldImageUrl,
            oldImagePublicId);

        product.setUpdatedAt(LocalDateTime.now());

        Product savedProduct = productRepository.save(product);
        log.info("Product updated successfully with id: {}", savedProduct.getId());
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Apply a single image replace/clear decision for a product.
     *
     * Decision matrix:
     *   newUrl blank/empty  → user wants to clear the image → destroy old, set null.
     *   newUrl == oldUrl    → no-op (could be a save with no change), do not destroy.
     *   newUrl != oldUrl    → user uploaded a new image → destroy old, save new + publicId.
     *   newUrl present, oldUrl null (first image upload on existing product)
     *                        → no destroy needed, just save new + publicId.
     *
     * Why is the destroy call inside the @Transactional method but its failure
     * is only logged?
     *   Losing the old Cloudinary file is annoying but never blocks the
     *   supplier from saving the new image. Rolling back the whole product
     *   save because the delete call failed would punish the supplier for
     *   our housekeeping problem. A separate cron (future work) can sweep
     *   orphans.
     */
    private void replaceProductImage(
        Product product,
        String newUrl,
        String newPublicId,
        String oldUrl,
        String oldPublicId
    ) {
        // No image fields sent → nothing to do (also avoids the case where
        // the request was a no-op save and both args are null).
        if (newUrl == null && newPublicId == null) {
            return;
        }

        boolean clearing = (newUrl == null || newUrl.isBlank());
        boolean changing = !clearing && !newUrl.equals(oldUrl);

        if (!clearing && !changing) {
            return; // URL unchanged — nothing to do (also no destroy).
        }

        // Best-effort cleanup of the previous Cloudinary asset. We only
        // destroy when there is actually a previous publicId to clean up.
        // (First-time upload: oldPublicId is null → skip destroy.)
        if (oldPublicId != null && !oldPublicId.isBlank()) {
            try {
                cloudinaryStorage.delete(oldPublicId);
            } catch (Exception ex) {
                // CloudinaryStorageService.delete already swallows + logs
                // non-fatal failures (per its contract: "failures are
                // logged but never thrown"). This catch is a defensive
                // belt-and-braces in case that contract ever changes.
                log.warn("Failed to delete old product image (publicId={}): {}",
                    oldPublicId, ex.getMessage());
            }
        }

        if (clearing) {
            product.setImageUrl(null);
            product.setImagePublicId(null);
        } else {
            product.setImageUrl(newUrl.trim());
            product.setImagePublicId(
                newPublicId != null && !newPublicId.isBlank() ? newPublicId.trim() : null);
        }
    }

    @Override
    @Transactional
    public ProductResponse updateProductStatus(Long id, ProductStatusUpdateRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName);
        boolean isSupplier = SecurityConstants.ROLE_SUPPLIER.equalsIgnoreCase(roleName);

        if (!isAdmin && !isSupplier) {
            throw new AccessDeniedException("Access denied: You do not have permission to update product status");
        }

        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (isSupplier) {
            if (currentUser.getCompany() == null || !product.getSupplierCompany().getId().equals(currentUser.getCompany().getId())) {
                throw new AccessDeniedException("Access denied: You do not have permission to update status of this product");
            }
        }

        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new BusinessException("Status is required", HttpStatus.BAD_REQUEST);
        }

        String normalizedStatus = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
            throw new BusinessException(
                    "Invalid product status: " + request.getStatus() + ". Allowed statuses: ACTIVE, INACTIVE",
                    HttpStatus.BAD_REQUEST
            );
        }

        product.setStatus(normalizedStatus);
        product.setUpdatedAt(LocalDateTime.now());

        Product savedProduct = productRepository.save(product);
        log.info("Product status updated to {} for id: {}", normalizedStatus, savedProduct.getId());
        return productMapper.toResponse(savedProduct);
    }

}

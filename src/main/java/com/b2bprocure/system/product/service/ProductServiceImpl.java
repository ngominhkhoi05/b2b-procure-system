package com.b2bprocure.system.product.service;

import com.b2bprocure.system.admin.dto.AdminProductDetailResponse;
import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.common.constant.SecurityConstants;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

        String pattern = (request.getKeyword() != null && !request.getKeyword().isBlank())
                ? "%" + request.getKeyword().trim().toLowerCase() + "%"
                : null;

        // For Buyer: require at least one price_product to be visible
        // For Admin/Supplier: see all products (no price requirement)
        boolean requireHasPrices = isBuyer;

        Page<Product> productPage = productRepository.searchProducts(
                supplierCompanyId,
                categoryId,
                status,
                categoryStatus,
                pattern,
                requireHasPrices,
                pageable
        );

        List<ProductResponse> content = productPage.getContent().stream()
                .map(productMapper::toResponse)
                .toList();

        return PageResponse.of(productPage, content);
    }

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

        productMapper.updateEntity(request, product);
        product.setName(trimmedName);
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription().trim());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl().trim());
        }
        product.setUpdatedAt(LocalDateTime.now());

        Product savedProduct = productRepository.save(product);
        log.info("Product updated successfully with id: {}", savedProduct.getId());
        return productMapper.toResponse(savedProduct);
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

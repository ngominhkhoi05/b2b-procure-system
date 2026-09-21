package com.b2bprocure.system.product.service;

import com.b2bprocure.system.common.constant.SecurityConstants;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.product.dto.CreateProductPriceRequest;
import com.b2bprocure.system.product.dto.ProductPriceResponse;
import com.b2bprocure.system.product.dto.UpdateProductPriceRequest;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
import com.b2bprocure.system.product.mapper.ProductPriceMapper;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPriceServiceImpl implements ProductPriceService {

    private final ProductPriceRepository productPriceRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
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

    private Product validateProductAndMutatePermission(Long productId) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName);
        boolean isSupplier = SecurityConstants.ROLE_SUPPLIER.equalsIgnoreCase(roleName);

        if (!isAdmin && !isSupplier) {
            throw new AccessDeniedException("Access denied: You do not have permission to manage product price tiers");
        }

        Product product = productRepository.findByIdWithDetails(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (isSupplier) {
            if (currentUser.getCompany() == null || !product.getSupplierCompany().getId().equals(currentUser.getCompany().getId())) {
                throw new AccessDeniedException("Access denied: You do not have permission to manage prices for this product");
            }
        }

        return product;
    }

    private void validateTierValues(Integer minQuantity, Integer maxQuantity, BigDecimal unitPrice) {
        if (minQuantity == null || minQuantity < 1) {
            throw new BusinessException("Minimum quantity must be at least 1", HttpStatus.BAD_REQUEST);
        }
        if (maxQuantity != null && maxQuantity < minQuantity) {
            throw new BusinessException("Maximum quantity must be greater than or equal to minimum quantity", HttpStatus.BAD_REQUEST);
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Unit price must be greater than zero", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isOverlapping(Integer minA, Integer maxA, Integer minB, Integer maxB) {
        boolean bStartsBeforeOrAtAEnds = (maxA == null || minB <= maxA);
        boolean aStartsBeforeOrAtBEnds = (maxB == null || minA <= maxB);
        return bStartsBeforeOrAtAEnds && aStartsBeforeOrAtBEnds;
    }

    private void checkOverlap(Long productId, Integer minQuantity, Integer maxQuantity, Long excludePriceId) {
        List<ProductPrice> existingPrices = productPriceRepository.findByProductId(productId);
        for (ProductPrice existing : existingPrices) {
            if (excludePriceId != null && existing.getId().equals(excludePriceId)) {
                continue;
            }
            if (isOverlapping(minQuantity, maxQuantity, existing.getMinQuantity(), existing.getMaxQuantity())) {
                String requestedRange = String.format("[%d, %s]", minQuantity, maxQuantity != null ? maxQuantity : "∞");
                String existingRange = String.format("[%d, %s]", existing.getMinQuantity(), existing.getMaxQuantity() != null ? existing.getMaxQuantity() : "∞");
                throw new BusinessException(
                        String.format("Price tier %s overlaps with existing tier %s", requestedRange, existingRange),
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    @Override
    @Transactional
    public ProductPriceResponse createPriceTier(Long productId, CreateProductPriceRequest request) {
        Product product = validateProductAndMutatePermission(productId);

        validateTierValues(request.getMinQuantity(), request.getMaxQuantity(), request.getUnitPrice());
        checkOverlap(productId, request.getMinQuantity(), request.getMaxQuantity(), null);

        ProductPrice productPrice = productPriceMapper.toEntity(request);
        productPrice.setProduct(product);
        productPrice.setCreatedAt(LocalDateTime.now());
        productPrice.setUpdatedAt(LocalDateTime.now());

        ProductPrice savedPrice = productPriceRepository.save(productPrice);
        log.info("Price tier created for product id {}: [min={}, max={}, price={}]",
                productId, savedPrice.getMinQuantity(), savedPrice.getMaxQuantity(), savedPrice.getUnitPrice());

        return productPriceMapper.toResponse(savedPrice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceResponse> getPriceTiers(Long productId) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName);
        boolean isSupplier = SecurityConstants.ROLE_SUPPLIER.equalsIgnoreCase(roleName);
        boolean isBuyer = SecurityConstants.ROLE_BUYER.equalsIgnoreCase(roleName);

        Product product = productRepository.findByIdWithDetails(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (isSupplier) {
            if (currentUser.getCompany() == null || !product.getSupplierCompany().getId().equals(currentUser.getCompany().getId())) {
                throw new AccessDeniedException("Access denied: You do not have permission to view prices for this product");
            }
        } else if (isBuyer) {
            // Buyer can only view prices if product and its category are ACTIVE
            if (!"ACTIVE".equalsIgnoreCase(product.getStatus()) || !"ACTIVE".equalsIgnoreCase(product.getCategory().getStatus())) {
                throw new ResourceNotFoundException("Product", "id", productId);
            }
        } else if (!isAdmin) {
            throw new AccessDeniedException("Access denied: You do not have permission to view prices for this product");
        }

        List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByMinQuantityAsc(productId);
        return prices.stream()
                .map(productPriceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductPriceResponse updatePriceTier(Long productId, Long priceId, UpdateProductPriceRequest request) {
        validateProductAndMutatePermission(productId);

        ProductPrice price = productPriceRepository.findById(priceId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductPrice", "id", priceId));

        if (!price.getProduct().getId().equals(productId)) {
            throw new BusinessException("Price tier with id " + priceId + " does not belong to product with id " + productId, HttpStatus.BAD_REQUEST);
        }

        validateTierValues(request.getMinQuantity(), request.getMaxQuantity(), request.getUnitPrice());
        checkOverlap(productId, request.getMinQuantity(), request.getMaxQuantity(), priceId);

        productPriceMapper.updateEntity(request, price);
        price.setMinQuantity(request.getMinQuantity());
        price.setMaxQuantity(request.getMaxQuantity());
        price.setUnitPrice(request.getUnitPrice());
        price.setUpdatedAt(LocalDateTime.now());

        ProductPrice savedPrice = productPriceRepository.save(price);
        log.info("Price tier updated for product id {} and price id {}", productId, priceId);

        return productPriceMapper.toResponse(savedPrice);
    }

    @Override
    @Transactional
    public void deletePriceTier(Long productId, Long priceId) {
        validateProductAndMutatePermission(productId);

        ProductPrice price = productPriceRepository.findById(priceId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductPrice", "id", priceId));

        if (!price.getProduct().getId().equals(productId)) {
            throw new BusinessException("Price tier with id " + priceId + " does not belong to product with id " + productId, HttpStatus.BAD_REQUEST);
        }

        productPriceRepository.delete(price);
        log.info("Price tier deleted with id {} for product id {}", priceId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductPriceResponse> findPriceForQuantity(Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            return Optional.empty();
        }

        List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByMinQuantityAsc(productId);
        return com.b2bprocure.system.common.util.PriceTierResolver
                .resolveUnitPrice(prices, quantity)
                .map(price -> findMatchingTier(prices, quantity, price))
                .map(productPriceMapper::toResponse);
    }

    /**
     * Resolve the {@link ProductPrice} tier that corresponds to the unit
     * price returned by {@link com.b2bprocure.system.common.util.PriceTierResolver}.
     * If the quantity falls into a gap, prefer the last tier with that price;
     * if the quantity exceeds the last tier's max, also return the last tier
     * (which is what the resolver would have used).
     */
    private ProductPrice findMatchingTier(List<ProductPrice> prices, int quantity, java.math.BigDecimal price) {
        return prices.stream()
                .filter(p -> p.getUnitPrice() != null && p.getUnitPrice().compareTo(price) == 0)
                .reduce((a, b) -> b) // last tier with matching price
                .orElse(null);
    }

}

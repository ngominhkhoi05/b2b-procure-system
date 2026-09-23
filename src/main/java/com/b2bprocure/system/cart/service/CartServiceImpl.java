package com.b2bprocure.system.cart.service;

import com.b2bprocure.system.cart.dto.AddToCartRequest;
import com.b2bprocure.system.cart.dto.CartItemResponse;
import com.b2bprocure.system.cart.dto.CartResponse;
import com.b2bprocure.system.cart.dto.UpdateCartItemRequest;
import com.b2bprocure.system.cart.entity.Cart;
import com.b2bprocure.system.cart.entity.CartItem;
import com.b2bprocure.system.cart.mapper.CartMapper;
import com.b2bprocure.system.cart.repository.CartItemRepository;
import com.b2bprocure.system.cart.repository.CartRepository;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    private User getCurrentBuyerUser() {
        Long userId = SecurityUtil.getCurrentUserIdOrThrow();
        if (!SecurityUtil.isBuyer()) {
            throw new AccessDeniedException("Access denied: Only buyers can perform cart operations");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    /**
     * Resolve the unit price for a cart item using the shared
     * {@link com.b2bprocure.system.common.util.PriceTierResolver}.
     *
     * <p>Falls back to the LAST tier's unit price when the quantity exceeds
     * the last tier's {@code max_quantity}. Returns {@code null} when the
     * quantity is below all tiers or falls into a gap between tiers; in those
     * cases the cart response will expose {@code unitPrice=null} and
     * {@code available=false}.
     */
    private BigDecimal calculateUnitPrice(List<ProductPrice> tiers, Integer quantity) {
        return com.b2bprocure.system.common.util.PriceTierResolver
                .resolveUnitPrice(tiers, quantity).orElse(null);
    }

    private boolean isAvailable(Product product, Integer quantity) {
        boolean isProductActive = "ACTIVE".equalsIgnoreCase(product.getStatus());
        boolean isCategoryActive = product.getCategory() != null && "ACTIVE".equalsIgnoreCase(product.getCategory().getStatus());
        int availableStock = product.getAvailableQuantity() != null ? product.getAvailableQuantity() : 0;
        boolean isStockSufficient = quantity != null && availableStock >= quantity;
        return isProductActive && isCategoryActive && isStockSufficient;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart() {
        User buyer = getCurrentBuyerUser();

        Optional<Cart> cartOpt = cartRepository.findByUserId(buyer.getId());
        if (cartOpt.isEmpty()) {
            return CartResponse.builder()
                    .cartId(null)
                    .items(new ArrayList<>())
                    .totalAmount(BigDecimal.ZERO)
                    .totalItems(0)
                    .build();
        }

        Cart cart = cartOpt.get();
        List<CartItem> cartItems = cartItemRepository.findByCartIdWithProductDetails(cart.getId());
        if (cartItems.isEmpty()) {
            return CartResponse.builder()
                    .cartId(cart.getId())
                    .items(new ArrayList<>())
                    .totalAmount(BigDecimal.ZERO)
                    .totalItems(0)
                    .build();
        }

        List<Long> productIds = cartItems.stream()
                .map(ci -> ci.getProduct().getId())
                .distinct()
                .toList();

        List<ProductPrice> priceTiers = productPriceRepository.findByProductIdInOrderByMinQuantityAsc(productIds);
        Map<Long, List<ProductPrice>> pricesByProduct = priceTiers.stream()
                .collect(Collectors.groupingBy(pp -> pp.getProduct().getId()));

        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            List<ProductPrice> tiers = pricesByProduct.getOrDefault(p.getId(), Collections.emptyList());
            BigDecimal unitPrice = calculateUnitPrice(tiers, ci.getQuantity());
            BigDecimal subtotal = unitPrice != null ? unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity())) : null;
            boolean available = isAvailable(p, ci.getQuantity());

            if (subtotal != null) {
                totalAmount = totalAmount.add(subtotal);
            }
            itemResponses.add(cartMapper.toItemResponse(ci, unitPrice, subtotal, available));
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemResponses)
                .totalAmount(totalAmount)
                .totalItems(itemResponses.size())
                .build();
    }

    @Override
    @Transactional
    public CartItemResponse addToCart(AddToCartRequest request) {
        User buyer = getCurrentBuyerUser();

        Product product = productRepository.findByIdWithDetails(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new BusinessException("Product is inactive and cannot be added to cart", HttpStatus.BAD_REQUEST);
        }

        if (product.getCategory() == null || !"ACTIVE".equalsIgnoreCase(product.getCategory().getStatus())) {
            throw new BusinessException("Product category is inactive and cannot be added to cart", HttpStatus.BAD_REQUEST);
        }

        Cart cart = cartRepository.findByUserId(buyer.getId()).orElseGet(() -> {
            Cart newCart = Cart.builder()
                    .user(buyer)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return cartRepository.save(newCart);
        });

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndProductIdWithDetails(cart.getId(), product.getId());

        int availableStock = product.getAvailableQuantity() != null ? product.getAvailableQuantity() : 0;
        CartItem savedItem;

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            int totalQuantity = existingItem.getQuantity() + request.getQuantity();

            if (totalQuantity > availableStock) {
                throw new BusinessException("Total quantity (" + totalQuantity + ") exceeds available stock (" + availableStock + ")", HttpStatus.BAD_REQUEST);
            }

            existingItem.setQuantity(totalQuantity);
            existingItem.setUpdatedAt(LocalDateTime.now());
            savedItem = cartItemRepository.save(existingItem);
        } else {
            int requestedQuantity = request.getQuantity();

            if (requestedQuantity > availableStock) {
                throw new BusinessException("Requested quantity (" + requestedQuantity + ") exceeds available stock (" + availableStock + ")", HttpStatus.BAD_REQUEST);
            }

            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(requestedQuantity)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            savedItem = cartItemRepository.save(newItem);
        }

        List<ProductPrice> tiers = productPriceRepository.findByProductIdOrderByMinQuantityAsc(product.getId());
        BigDecimal unitPrice = calculateUnitPrice(tiers, savedItem.getQuantity());
        BigDecimal subtotal = unitPrice != null ? unitPrice.multiply(BigDecimal.valueOf(savedItem.getQuantity())) : null;
        boolean available = isAvailable(product, savedItem.getQuantity());

        return cartMapper.toItemResponse(savedItem, unitPrice, subtotal, available);
    }

    @Override
    @Transactional
    public CartItemResponse updateCartItem(Long productId, UpdateCartItemRequest request) {
        User buyer = getCurrentBuyerUser();

        Cart cart = cartRepository.findByUserId(buyer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", productId));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductIdWithDetails(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", productId));

        Product product = cartItem.getProduct();

        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new BusinessException("Product is inactive and cannot be updated in cart", HttpStatus.BAD_REQUEST);
        }

        if (product.getCategory() == null || !"ACTIVE".equalsIgnoreCase(product.getCategory().getStatus())) {
            throw new BusinessException("Product category is inactive and cannot be updated in cart", HttpStatus.BAD_REQUEST);
        }

        int requestedQuantity = request.getQuantity();
        int availableStock = product.getAvailableQuantity() != null ? product.getAvailableQuantity() : 0;

        if (requestedQuantity > availableStock) {
            throw new BusinessException("Requested quantity (" + requestedQuantity + ") exceeds available stock (" + availableStock + ")", HttpStatus.BAD_REQUEST);
        }

        cartItem.setQuantity(requestedQuantity);
        cartItem.setUpdatedAt(LocalDateTime.now());
        CartItem savedItem = cartItemRepository.save(cartItem);

        List<ProductPrice> tiers = productPriceRepository.findByProductIdOrderByMinQuantityAsc(product.getId());
        BigDecimal unitPrice = calculateUnitPrice(tiers, savedItem.getQuantity());
        BigDecimal subtotal = unitPrice != null ? unitPrice.multiply(BigDecimal.valueOf(savedItem.getQuantity())) : null;
        boolean available = isAvailable(product, savedItem.getQuantity());

        return cartMapper.toItemResponse(savedItem, unitPrice, subtotal, available);
    }

    @Override
    @Transactional
    public void removeCartItem(Long productId) {
        User buyer = getCurrentBuyerUser();

        Cart cart = cartRepository.findByUserId(buyer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", productId));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", productId));

        cartItemRepository.delete(cartItem);
    }

    @Override
    @Transactional
    public void clearCart() {
        User buyer = getCurrentBuyerUser();

        Optional<Cart> cartOpt = cartRepository.findByUserId(buyer.getId());
        cartOpt.ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
    }

}

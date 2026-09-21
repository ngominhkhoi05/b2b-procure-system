package com.b2bprocure.system.order.service;

import com.b2bprocure.system.cart.entity.Cart;
import com.b2bprocure.system.cart.entity.CartItem;
import com.b2bprocure.system.cart.repository.CartItemRepository;
import com.b2bprocure.system.cart.repository.CartRepository;
import com.b2bprocure.system.common.enums.ErrorCode;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.common.enums.SettingKey;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.order.dto.CheckoutRequest;
import com.b2bprocure.system.order.dto.CheckoutResponse;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.setting.service.SystemSettingService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final SystemSettingService systemSettingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckoutResponse checkout(CheckoutRequest request) {
        // 1. Authenticate & Authorize User
        Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
        if (!SecurityUtil.isBuyer()) {
            throw new AccessDeniedException("Access denied: Only buyers can perform checkout");
        }

        User buyer = userRepository.findByIdWithRoleAndCompany(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        if (buyer.getStatus() != null && "BLOCKED".equalsIgnoreCase(buyer.getStatus())) {
            throw new BusinessException("User account is blocked", HttpStatus.FORBIDDEN);
        }
        if (buyer.getStatus() != null && !"ACTIVE".equalsIgnoreCase(buyer.getStatus())) {
            throw new BusinessException("User account is not active", HttpStatus.BAD_REQUEST);
        }

        Company buyerCompany = buyer.getCompany();
        if (buyerCompany == null) {
            throw new BusinessException("Buyer does not belong to any company", HttpStatus.BAD_REQUEST);
        }
        if (!"BUYER".equalsIgnoreCase(buyerCompany.getCompanyType())) {
            throw new BusinessException("Buyer company must be of type BUYER", HttpStatus.BAD_REQUEST);
        }
        if (buyerCompany.getStatus() != null && !"ACTIVE".equalsIgnoreCase(buyerCompany.getStatus())) {
            throw new BusinessException("Buyer company is not active", HttpStatus.BAD_REQUEST);
        }

        // Validate shipping information from buyer company profile
        String shippingCompanyName = buyerCompany.getName();
        String shippingPhone = buyerCompany.getPhone();
        String shippingAddress = buyerCompany.getAddress();

        if (shippingCompanyName == null || shippingCompanyName.isBlank()
                || shippingPhone == null || shippingPhone.isBlank()
                || shippingAddress == null || shippingAddress.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_SHIPPING_INFO,
                    "Missing shipping information in buyer company profile: company name, phone, and address are required");
        }

        // 2. Validate Request & CartItems
        if (request.getCartItemIds() == null || request.getCartItemIds().isEmpty()) {
            throw new BusinessException("Cart item IDs must not be empty", HttpStatus.BAD_REQUEST);
        }
        if (request.getPaymentMethod() == null) {
            throw new BusinessException("Payment method is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getPaymentMethod() != PaymentMethod.COD && request.getPaymentMethod() != PaymentMethod.ZALOPAY) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PAYMENT_METHOD,
                    "Payment method '" + request.getPaymentMethod() + "' is not supported for checkout. Supported methods: COD, ZALOPAY");
        }

        Cart cart = cartRepository.findByUserId(buyer.getId()).orElse(null);

        List<Long> requestedItemIds = request.getCartItemIds().stream().distinct().toList();
        List<CartItem> selectedItems;

        if (cart == null) {
            Long firstId = requestedItemIds.get(0);
            CartItem item = cartItemRepository.findById(firstId)
                    .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", firstId));
            throw new BusinessException("Cart item with ID " + firstId + " does not belong to the current user's cart", HttpStatus.BAD_REQUEST);
        }

        // Load all items in a single query with eager fetch to avoid N+1
        selectedItems = cartItemRepository.findByCartIdAndIdInWithProductDetails(cart.getId(), requestedItemIds);

        if (selectedItems.size() != requestedItemIds.size()) {
            Set<Long> foundIds = selectedItems.stream().map(CartItem::getId).collect(Collectors.toSet());
            for (Long itemId : requestedItemIds) {
                if (!foundIds.contains(itemId)) {
                    CartItem item = cartItemRepository.findById(itemId)
                            .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));
                    throw new BusinessException("Cart item with ID " + itemId + " does not belong to the current user's cart", HttpStatus.BAD_REQUEST);
                }
            }
        }

        for (CartItem item : selectedItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_CART_ITEM, "Invalid quantity for cart item: " + item.getId());
            }

            Product product = item.getProduct();
            if (product == null || !"ACTIVE".equalsIgnoreCase(product.getStatus())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE,
                        "Product is inactive or unavailable: " + (product != null ? product.getName() : item.getId()));
            }

            if (product.getCategory() == null || !"ACTIVE".equalsIgnoreCase(product.getCategory().getStatus())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE,
                        "Product category is inactive for product: " + product.getName());
            }

            if (product.getSupplierCompany() == null) {
                throw new BusinessException("Product does not belong to a valid supplier: " + product.getName(), HttpStatus.BAD_REQUEST);
            }
        }

        // 3. Validate Single Supplier Rule
        List<Long> supplierIds = selectedItems.stream()
                .map(item -> item.getProduct().getSupplierCompany().getId())
                .distinct()
                .toList();

        if (supplierIds.size() > 1) {
            throw new BusinessException(ErrorCode.MULTIPLE_SUPPLIERS_NOT_ALLOWED,
                    "Checkout cannot contain products from multiple suppliers");
        }

        Company supplierCompany = selectedItems.get(0).getProduct().getSupplierCompany();
        if (supplierCompany.getStatus() != null && !"ACTIVE".equalsIgnoreCase(supplierCompany.getStatus())) {
            throw new BusinessException("Supplier company is not active", HttpStatus.BAD_REQUEST);
        }

        // 4. Lock Products using Pessimistic Write Lock (sorted by ID ASC)
        Map<Long, Integer> quantityByProductId = selectedItems.stream()
                .collect(Collectors.groupingBy(item -> item.getProduct().getId(), Collectors.summingInt(CartItem::getQuantity)));

        List<Long> sortedProductIds = quantityByProductId.keySet().stream().sorted().toList();
        List<Product> lockedProducts = productRepository.findByIdInWithLock(sortedProductIds);

        if (lockedProducts.size() != sortedProductIds.size()) {
            throw new BusinessException("Some products could not be found or locked for checkout", HttpStatus.BAD_REQUEST);
        }

        Map<Long, Product> lockedProductMap = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 5. Stock Validation & Stock Reservation
        for (Product product : lockedProducts) {
            int requestedQty = quantityByProductId.get(product.getId());
            int availableQty = product.getAvailableQuantity();
            if (requestedQty > availableQty) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                        String.format("Insufficient stock for product '%s' (requested: %d, available: %d)",
                                product.getName(), requestedQty, availableQty));
            }
            product.setReservedQuantity(product.getReservedQuantity() + requestedQty);
            product.validateInvariants();
        }
        productRepository.saveAll(lockedProducts);

        // 6. Calculate Tier Price & Order Totals
        List<ProductPrice> priceTiers = productPriceRepository.findByProductIdInOrderByMinQuantityAsc(sortedProductIds);
        Map<Long, List<ProductPrice>> tiersByProduct = priceTiers.stream()
                .collect(Collectors.groupingBy(pp -> pp.getProduct().getId()));

        BigDecimal subtotal = BigDecimal.ZERO;

        record ItemCalculation(CartItem cartItem, Product product, BigDecimal unitPrice, BigDecimal itemSubtotal) {}
        List<ItemCalculation> itemCalculations = new ArrayList<>();

        for (CartItem item : selectedItems) {
            Product product = lockedProductMap.get(item.getProduct().getId());
            List<ProductPrice> tiers = tiersByProduct.getOrDefault(product.getId(), Collections.emptyList());
            BigDecimal unitPrice = calculateUnitPrice(tiers, item.getQuantity());

            if (unitPrice == null) {
                throw new BusinessException(ErrorCode.NO_MATCHING_PRICE_TIER,
                        String.format("No matching price tier found for product '%s' with quantity %d",
                                product.getName(), item.getQuantity()));
            }

            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);
            itemCalculations.add(new ItemCalculation(item, product, unitPrice, itemSubtotal));
        }

        BigDecimal totalAmount = subtotal; // Checkout totalAmount = subtotal (shipping fee = 0 in MVP)

        // 7. Create Order
        // NOTE: Commission is NOT calculated or snapshotted at checkout.
        // commissionRate and commissionAmount remain null at checkout and will be calculated
        // when the Order reaches COMPLETED status.
        String orderCode = generateUniqueOrderCode();
        Order order = Order.builder()
                .buyerCompany(buyerCompany)
                .supplierCompany(supplierCompany)
                .createdBy(buyer)
                .orderCode(orderCode)
                .status(OrderStatus.PENDING_CONFIRMATION)
                .subtotal(subtotal)
                .commissionRate(null)
                .commissionAmount(null)
                .totalAmount(totalAmount)
                .shippingCompanyName(shippingCompanyName)
                .shippingPhone(shippingPhone)
                .shippingAddress(shippingAddress)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);

        // 8. Create OrderStatusHistory
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .changedBy(buyer)
                .status(OrderStatus.PENDING_CONFIRMATION)
                .note("Order placed via checkout")
                .createdAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(history);

        // 9. Create OrderItems (Snapshot product name, unit price, quantity, subtotal)
        for (ItemCalculation calc : itemCalculations) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(calc.product());
            orderItem.setProductName(calc.product().getName());
            orderItem.setQuantity(calc.cartItem().getQuantity());
            orderItem.setUnitPrice(calc.unitPrice());
            orderItem.setSubtotal(calc.itemSubtotal());
            orderItemRepository.save(orderItem);
        }

        // 10. Create Payment
        String paymentCode = generateUniquePaymentCode();
        LocalDateTime expiredAt = null;
        if (request.getPaymentMethod() != PaymentMethod.COD) {
            int timeoutMinutes = systemSettingService.getSettingValueAsInt(SettingKey.PAYMENT_TIMEOUT_MINUTES, 15);
            expiredAt = LocalDateTime.now().plusMinutes(timeoutMinutes);
        }

        Payment payment = Payment.builder()
                .order(order)
                .paymentCode(paymentCode)
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .amount(totalAmount)
                .expiredAt(expiredAt)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        payment = paymentRepository.save(payment);

        // 11. Remove selected CartItems from Cart
        cartItemRepository.deleteAll(selectedItems);

        log.info("Checkout successful: orderId={}, orderCode={}, paymentCode={}, buyer={}, totalAmount={}",
                order.getId(), order.getOrderCode(), payment.getPaymentCode(), buyer.getUsername(), totalAmount);

        // 12. Build and return Response
        return CheckoutResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getStatus())
                .orderStatus(order.getStatus())
                .subtotal(order.getSubtotal())
                .totalAmount(order.getTotalAmount())
                .paymentId(payment.getId())
                .paymentCode(payment.getPaymentCode())
                .paymentExpiredAt(payment.getExpiredAt())
                .paymentUrl(null) // For ZaloPay, frontend should call POST /api/v1/checkout/zalopay/create-payment
                .createdAt(order.getCreatedAt())
                .build();
    }

    private BigDecimal calculateUnitPrice(List<ProductPrice> tiers, Integer quantity) {
        if (tiers == null || tiers.isEmpty() || quantity == null || quantity <= 0) {
            return null;
        }

        // Strict tier matching: minQuantity <= quantity <= maxQuantity (maxQuantity can be null for open upper bound)
        return tiers.stream()
                .filter(t -> t.getMinQuantity() != null && quantity >= t.getMinQuantity()
                        && (t.getMaxQuantity() == null || quantity <= t.getMaxQuantity()))
                .findFirst()
                .map(ProductPrice::getUnitPrice)
                .orElse(null);
    }

    private String generateUniqueOrderCode() {
        String code;
        do {
            code = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }

    private String generateUniquePaymentCode() {
        String code;
        do {
            code = "PAY-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } while (paymentRepository.existsByPaymentCode(code));
        return code;
    }

}

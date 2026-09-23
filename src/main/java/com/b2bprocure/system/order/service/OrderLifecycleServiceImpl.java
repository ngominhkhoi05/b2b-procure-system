package com.b2bprocure.system.order.service;

import com.b2bprocure.system.common.enums.ErrorCode;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.commission.entity.CommissionRate;
import com.b2bprocure.system.commission.service.CommissionRateQueryService;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.order.dto.CancelOrderRequest;
import com.b2bprocure.system.order.dto.OrderDetailResponse;
import com.b2bprocure.system.order.dto.OrderItemResponse;
import com.b2bprocure.system.order.dto.OrderResponse;
import com.b2bprocure.system.order.dto.OrderStatusHistoryResponse;
import com.b2bprocure.system.order.dto.PaymentSummaryResponse;
import com.b2bprocure.system.order.dto.RejectOrderRequest;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.mapper.OrderItemMapper;
import com.b2bprocure.system.order.mapper.OrderMapper;
import com.b2bprocure.system.order.mapper.OrderStatusHistoryMapper;
import com.b2bprocure.system.order.mapper.PaymentSummaryMapper;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLifecycleServiceImpl implements OrderLifecycleService {

    /**
     * Maximum page size allowed for Order History / Query list endpoint.
     * Requests with larger size are clamped to this value (Step 7 spec §4.1).
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final PaymentSummaryMapper paymentSummaryMapper;
    private final CommissionRateQueryService commissionRateQueryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse confirmOrder(Long orderId) {
        // 1. Authenticate & Authorize Supplier
        User supplierUser = getAuthenticatedSupplierUser();
        Company supplierCompany = supplierUser.getCompany();

        // 2. Lock Order row using Pessimistic Write Lock
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // 3. Ownership Validation
        if (!order.getSupplierCompany().getId().equals(supplierCompany.getId())) {
            throw new AccessDeniedException("Access denied: Order does not belong to the current supplier company");
        }

        // 4. Validate Payment & State Transitions
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));

        PaymentMethod paymentMethod = payment.getPaymentMethod();

        if (paymentMethod == PaymentMethod.COD) {
            // COD must be PENDING_CONFIRMATION and payment PENDING
            if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION) {
                throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                        "COD order cannot be confirmed from status " + order.getStatus() + ". Expected status: PENDING_CONFIRMATION");
            }
            if (payment.getStatus() != PaymentStatus.PENDING) {
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE,
                        "Payment status for COD order must be PENDING, got: " + payment.getStatus());
            }
        } else {
            // Online orders must be PAID and payment SUCCESS before confirmation
            if (order.getStatus() == OrderStatus.PENDING_CONFIRMATION) {
                throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                        "Online order cannot be confirmed until payment is successful (current status: PENDING_CONFIRMATION, expected: PAID)");
            }
            if (order.getStatus() != OrderStatus.PAID) {
                throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                        "Online order cannot be confirmed from status " + order.getStatus() + ". Expected status: PAID");
            }
            if (payment.getStatus() != PaymentStatus.SUCCESS) {
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE,
                        "Online order cannot be confirmed because payment status is not SUCCESS: " + payment.getStatus());
            }
        }

        if (!order.getStatus().canTransitionTo(OrderStatus.CONFIRMED, paymentMethod)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                    "Invalid transition from " + order.getStatus() + " to CONFIRMED for payment method " + paymentMethod);
        }

        // 5. Atomic Stock Deduction & Reservation Decrement
        List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(order.getId());
        Map<Long, Integer> qtyByProductId = items.stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getId(), Collectors.summingInt(OrderItem::getQuantity)));

        List<Long> productIds = qtyByProductId.keySet().stream().sorted().toList();
        List<Product> products = productRepository.findByIdInWithLock(productIds);

        for (Product product : products) {
            int orderedQty = qtyByProductId.get(product.getId());
            int currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            int currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;

            if (orderedQty > currentReserved) {
                throw new IllegalStateException(String.format(
                        "Data inconsistency: ordered quantity (%d) exceeds reserved quantity (%d) for product '%s'",
                        orderedQty, currentReserved, product.getName()));
            }
            if (orderedQty > currentStock) {
                throw new IllegalStateException(String.format(
                        "Data inconsistency: ordered quantity (%d) exceeds stock quantity (%d) for product '%s'",
                        orderedQty, currentStock, product.getName()));
            }

            // Both columns updated simultaneously in memory; Hibernate issues single SQL UPDATE per row
            product.setStockQuantity(currentStock - orderedQty);
            product.setReservedQuantity(currentReserved - orderedQty);
            product.validateInvariants();
        }
        productRepository.saveAll(products);

        // 6. Update Order Status
        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // 7. Write History
        recordStatusHistory(order, supplierUser, OrderStatus.CONFIRMED, "Order confirmed by supplier");

        log.info("Order confirmed: orderId={}, orderCode={}, supplier={}",
                order.getId(), order.getOrderCode(), supplierUser.getUsername());

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse rejectOrder(Long orderId, RejectOrderRequest request) {
        // 1. Authenticate & Authorize Supplier
        User supplierUser = getAuthenticatedSupplierUser();
        Company supplierCompany = supplierUser.getCompany();

        // 2. Validate Reject Reason
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.REJECT_REASON_REQUIRED, "Reject reason is required and cannot be blank");
        }

        // 3. Lock Order
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // 4. Ownership Validation
        if (!order.getSupplierCompany().getId().equals(supplierCompany.getId())) {
            throw new AccessDeniedException("Access denied: Order does not belong to the current supplier company");
        }

        // 5. State Validation: Can only reject unconfirmed orders (PENDING_CONFIRMATION or PAID)
        if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                    "Order in status " + order.getStatus() + " cannot be rejected. Rejection is only allowed prior to confirmation.");
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                    "Cannot transition order from " + order.getStatus() + " to REJECTED");
        }

        // 6. Payment & Reservation Handling
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));

        if (payment.getPaymentMethod() != PaymentMethod.COD && payment.getStatus() == PaymentStatus.SUCCESS) {
            // Online Paid: transition payment to REFUND_PENDING, DO NOT release reservation
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            log.info("Order rejected with online paid payment: orderId={}, paymentId={}, status changed to REFUND_PENDING (reservation held)",
                    order.getId(), payment.getId());
        } else {
            // COD or Unpaid Online: release reservation immediately
            releaseOrderReservation(order);
        }

        // 7. Update Order
        order.setStatus(OrderStatus.REJECTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // 8. Write History with mandatory reject reason
        recordStatusHistory(order, supplierUser, OrderStatus.REJECTED, request.getReason().trim());

        log.info("Order rejected: orderId={}, orderCode={}, supplier={}, reason='{}'",
                order.getId(), order.getOrderCode(), supplierUser.getUsername(), request.getReason().trim());

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancelOrder(Long orderId, CancelOrderRequest request) {
        // 1. Authenticate & Authorize Buyer
        User buyerUser = getAuthenticatedBuyerUser();

        // 2. Lock Order
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // 3. Ownership Validation
        boolean isCreator = order.getCreatedBy().getId().equals(buyerUser.getId());
        boolean isSameCompany = buyerUser.getCompany() != null && order.getBuyerCompany().getId().equals(buyerUser.getCompany().getId());
        if (!isCreator && !isSameCompany) {
            throw new AccessDeniedException("Access denied: Order does not belong to the current buyer");
        }

        // 4. State Validation: Can only cancel unconfirmed orders (PENDING_CONFIRMATION or PAID)
        if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                    "Order in status " + order.getStatus() + " cannot be cancelled. Cancellation is only allowed prior to confirmation.");
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                    "Cannot transition order from " + order.getStatus() + " to CANCELLED");
        }

        // 5. Payment & Reservation Handling
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));

        if (payment.getPaymentMethod() != PaymentMethod.COD && payment.getStatus() == PaymentStatus.SUCCESS) {
            // Online Paid: transition payment to REFUND_PENDING, DO NOT release reservation
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            log.info("Order cancelled with online paid payment: orderId={}, paymentId={}, status changed to REFUND_PENDING (reservation held)",
                    order.getId(), payment.getId());
        } else {
            // COD or Unpaid Online: release reservation immediately
            releaseOrderReservation(order);
        }

        // 6. Update Order
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // 7. Write History
        String cancelNote = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim()
                : "Order cancelled by buyer";
        recordStatusHistory(order, buyerUser, OrderStatus.CANCELLED, cancelNote);

        log.info("Order cancelled: orderId={}, orderCode={}, buyer={}, reason='{}'",
                order.getId(), order.getOrderCode(), buyerUser.getUsername(), cancelNote);

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateToPreparing(Long orderId) {
        User supplierUser = getAuthenticatedSupplierUser();
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        validateSupplierOwnership(order, supplierUser.getCompany());

        if (!order.getStatus().canTransitionTo(OrderStatus.PREPARING)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                    "Cannot transition order from status " + order.getStatus() + " to PREPARING. Order must be CONFIRMED.");
        }

        order.setStatus(OrderStatus.PREPARING);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        recordStatusHistory(order, supplierUser, OrderStatus.PREPARING, "Order is being prepared");

        log.info("Order preparing: orderId={}, orderCode={}", order.getId(), order.getOrderCode());
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateToShipping(Long orderId) {
        User supplierUser = getAuthenticatedSupplierUser();
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        validateSupplierOwnership(order, supplierUser.getCompany());

        if (!order.getStatus().canTransitionTo(OrderStatus.SHIPPING)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                    "Cannot transition order from status " + order.getStatus() + " to SHIPPING. Order must be PREPARING.");
        }

        order.setStatus(OrderStatus.SHIPPING);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        recordStatusHistory(order, supplierUser, OrderStatus.SHIPPING, "Order is being shipped");

        log.info("Order shipping: orderId={}, orderCode={}", order.getId(), order.getOrderCode());
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateToCompleted(Long orderId) {
        User supplierUser = getAuthenticatedSupplierUser();
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        validateSupplierOwnership(order, supplierUser.getCompany());

        if (!order.getStatus().canTransitionTo(OrderStatus.COMPLETED)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE_TRANSITION,
                    "Cannot transition order from status " + order.getStatus() + " to COMPLETED. Order must be SHIPPING.");
        }

        // Update Payment status if COD
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));

        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            log.info("COD payment updated to SUCCESS on order completion: paymentId={}, orderId={}",
                    payment.getId(), order.getId());
        }
        // Online payments were already SUCCESS; retain status without duplicate records

        // Commission snapshot: calculate and store BEFORE final save.
        // Idempotency: skip if already snapshotted from a previous attempt.
        if (order.getCommissionRate() == null || order.getCommissionAmount() == null) {
            LocalDateTime completedAt = LocalDateTime.now();
            CommissionRate activeRate = commissionRateQueryService
                    .findActiveRateAt(completedAt)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.COMMISSION_RATE_NOT_FOUND,
                            "Cannot complete order " + order.getId()
                                    + ": no active commission rate at " + completedAt));

            // Rate is stored as raw percentage (e.g., 5 means 5%), not fraction (0.05)
            BigDecimal percentageFraction = activeRate.getRate()
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal commissionAmount = order.getSubtotal()
                    .multiply(percentageFraction)
                    .setScale(2, RoundingMode.HALF_UP);

            order.setCommissionRate(activeRate.getRate());
            order.setCommissionAmount(commissionAmount);

            log.info("Commission snapshot for order {}: rate={}, subtotal={}, commission={}",
                    order.getId(), activeRate.getRate(), order.getSubtotal(), commissionAmount);
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        recordStatusHistory(order, supplierUser, OrderStatus.COMPLETED, "Order completed");

        log.info("Order completed: orderId={}, orderCode={}", order.getId(), order.getOrderCode());
        return orderMapper.toResponse(order);
    }

    /**
     * Step 7 — Order Detail with pay-attention-to-404 behavior.
     *
     * Per spec §7.2: do NOT leak the existence of an Order a user cannot access.
     * If {@code validateOrderAccess} throws {@link AccessDeniedException}, we
     * translate it into {@link ResourceNotFoundException} (which {@code GlobalExceptionHandler}
     * renders as HTTP 404). Note: this differs from Step 6 lifecycle APIs which return 403 for
     * unauthorized action attempts on accessible orders — those are write operations and DO
     * benefit from returning 403 to discriminate "exists but you cannot do this"; here we
     * are doing a read, and we deliberately hide existence.
     */
    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        try {
            validateOrderAccess(order);
        } catch (AccessDeniedException ex) {
            // Translate to 404 to avoid leaking order existence (spec §7.2).
            log.info("Order detail access denied for orderId={} → 404 to avoid existence leak", orderId);
            throw new ResourceNotFoundException("Order", "id", orderId);
        }

        List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(order.getId());
        List<OrderItemResponse> itemResponses = orderItemMapper.toResponseList(items);

        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        List<OrderStatusHistoryResponse> historyResponses = orderStatusHistoryMapper.toResponseList(histories);

        // Payment summary (read once, joined). May be missing — checked is acceptable.
        PaymentSummaryResponse paymentSummary = paymentRepository.findByOrderId(order.getId())
                .map(paymentSummaryMapper::toResponse)
                .orElse(null);

        // Company display names: filled from the fetch-joined entities (zero extra queries).
        String buyerCompanyName = order.getBuyerCompany() != null ? order.getBuyerCompany().getName() : null;
        String supplierCompanyName = order.getSupplierCompany() != null ? order.getSupplierCompany().getName() : null;

        return orderMapper.toDetailResponse(
                order,
                itemResponses,
                historyResponses,
                paymentSummary,
                buyerCompanyName,
                supplierCompanyName
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderStatusHistory(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        validateOrderAccess(order);

        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        return orderStatusHistoryMapper.toResponseList(histories);
    }

    // =========================================================================
    // STEP 7 — Order History / Order Query (LIST)
    // =========================================================================

    /**
     * Step 7 — paginated, filtered list of Orders visible to the currently authenticated principal.
     *
     * <h3>Visibility strategy</h3>
     * <ul>
     *   <li>BUYER  → Service passes {@code buyerUserId} and {@code buyerCompanyId} derived from
     *                   {@code SecurityUtil} + user lookup. SQL filters by
     *                   {@code createdBy.id = buyerUserId OR buyerCompany.id = buyerCompanyId}.</li>
     *   <li>SUPPLIER → Service passes {@code supplierCompanyId} derived from the user lookup. SQL
     *                     filters by {@code EXISTS (OrderItem → Product.supplier_company_id = :supplierCompanyId)}.</li>
     *   <li>ADMIN → All visibility parameters null → no SQL visibility filter.</li>
     * </ul>
     *
     * <h3>Pagination</h3>
     * {@link Pageable} clamped to {@code MAX_PAGE_SIZE=100} (spec §4.1). Default sort applied at
     * controller layer: {@code createdAt DESC}.
     *
     * <h3>N+1 avoidance</h3>
     * {@code searchOrders} uses {@code JOIN FETCH buyerCompany / supplierCompany} and
     * {@code LEFT JOIN Payment}. Each page is exactly 2 SQL queries (data + count). After the
     * page is loaded, only the {@link Order.buyerCompany} association is touched (to fetch
     * display name) — and only when needed (for Admin / for detail). For list responses we
     * deliberately do not dereference lazy associations, so there is no N+1.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(
            String status,
            String paymentMethod,
            String paymentStatus,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {

        // 1. Validate + normalize enum filters at the boundary; throw 400 on bad input.
        OrderStatus statusEnum = parseEnum(OrderStatus.class, status, "status");
        PaymentMethod paymentMethodEnum = parseEnum(PaymentMethod.class, paymentMethod, "paymentMethod");
        PaymentStatus paymentStatusEnum = parseEnum(PaymentStatus.class, paymentStatus, "paymentStatus");

        // 2. Validate date range.
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException("fromDate must be on or before toDate", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // 3. Date semantics: fromDate inclusive start-of-day, toDate extended to next-day start (exclusive).
        LocalDateTime fromDateTime = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateExclusive = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

        // 4. Resolve role-aware visibility parameters from the authenticated principal.
        //    These are NEVER taken from request — only from SecurityContext + DB lookup.
        Long buyerUserId = null;
        Long buyerCompanyId = null;
        Long supplierCompanyId = null;

        if (SecurityUtil.isBuyer()) {
            Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
            User user = userRepository.findByIdWithRoleAndCompany(currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));
            buyerUserId = user.getId();
            if (user.getCompany() != null) {
                buyerCompanyId = user.getCompany().getId();
            }
        } else if (SecurityUtil.isSupplier()) {
            Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
            User user = userRepository.findByIdWithRoleAndCompany(currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));
            if (user.getCompany() != null) {
                supplierCompanyId = user.getCompany().getId();
            } else {
                throw new AccessDeniedException("Access denied: Supplier user does not belong to any supplier company");
            }
        } else if (!SecurityUtil.isAdmin()) {
            // Defensive — controller @PreAuthorize already restricts roles.
            throw new AccessDeniedException("Access denied: Caller has no Order History role");
        }

        // 5. Clamp page size per spec §4.1.
        int requestedSize = pageable.getPageSize();
        int effectiveSize = requestedSize > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : Math.max(requestedSize, 1);
        Pageable effectivePageable = (effectiveSize == requestedSize)
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), effectiveSize, pageable.getSort());

        // 6. Execute role-aware query.
        Page<Order> page = orderRepository.searchOrders(
                statusEnum,
                paymentMethodEnum,
                paymentStatusEnum,
                fromDateTime,
                toDateExclusive,
                buyerUserId,
                buyerCompanyId,
                supplierCompanyId,
                effectivePageable
        );

        // 7. Map Page<Order> → Page<OrderResponse>. Composer sets paymentMethod + paymentStatus
        //    via a single batched Payment lookup (1 SQL, not N). We deliberately do NOT touch
        //    buyerCompany / supplierCompany lazy associations inside the loop.
        List<Long> orderIds = page.getContent().stream().map(Order::getId).toList();
        Map<Long, Payment> paymentByOrderId = orderIds.isEmpty()
                ? Map.of()
                : paymentRepository.findAllByOrderIdIn(orderIds).stream()
                        .collect(Collectors.toMap(p -> p.getOrder().getId(), p -> p, (a, b) -> a));

        List<OrderResponse> content = page.getContent().stream().map(o -> {
            OrderResponse r = orderMapper.toResponse(o);
            Payment p = paymentByOrderId.get(o.getId());
            if (p != null) {
                r.setPaymentMethod(p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null);
                r.setPaymentStatus(p.getStatus() != null ? p.getStatus().name() : null);
            }
            return r;
        }).toList();

        return PageResponse.of(page, content);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Parse a String filter into an enum value, or return {@code null} when the filter is
     * not provided. Throws {@link BusinessException} with HTTP 400 for invalid values.
     */
    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String filterName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            StringBuilder allowed = new StringBuilder();
            for (Object constant : type.getEnumConstants()) {
                if (allowed.length() > 0) allowed.append(", ");
                allowed.append(((Enum<?>) constant).name());
            }
            throw new BusinessException(
                    "Invalid value for filter '" + filterName + "': " + raw + ". Allowed values: " + allowed,
                    org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }
    }

    private void releaseOrderReservation(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(order.getId());
        Map<Long, Integer> qtyByProductId = items.stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getId(), Collectors.summingInt(OrderItem::getQuantity)));

        List<Long> productIds = qtyByProductId.keySet().stream().sorted().toList();
        List<Product> products = productRepository.findByIdInWithLock(productIds);

        for (Product product : products) {
            int qty = qtyByProductId.get(product.getId());
            int currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
            product.setReservedQuantity(Math.max(0, currentReserved - qty));
            product.validateInvariants();
        }
        productRepository.saveAll(products);
    }

    private void recordStatusHistory(Order order, User actor, OrderStatus status, String note) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .changedBy(actor)
                .status(status)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(history);
    }

    private User getAuthenticatedSupplierUser() {
        Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
        if (!SecurityUtil.isSupplier()) {
            throw new AccessDeniedException("Access denied: Only suppliers can perform this action");
        }
        User user = userRepository.findByIdWithRoleAndCompany(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        if (user.getCompany() == null || !"SUPPLIER".equalsIgnoreCase(user.getCompany().getCompanyType())) {
            throw new AccessDeniedException("Access denied: User does not belong to an active supplier company");
        }
        return user;
    }

    private User getAuthenticatedBuyerUser() {
        Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
        if (!SecurityUtil.isBuyer()) {
            throw new AccessDeniedException("Access denied: Only buyers can perform this action");
        }
        return userRepository.findByIdWithRoleAndCompany(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));
    }

    private void validateSupplierOwnership(Order order, Company supplierCompany) {
        if (supplierCompany == null || !order.getSupplierCompany().getId().equals(supplierCompany.getId())) {
            throw new AccessDeniedException("Access denied: Order does not belong to the current supplier company");
        }
    }

    private void validateOrderAccess(Order order) {
        Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
        if (SecurityUtil.isAdmin()) {
            return; // Admin can access any order
        }
        User user = userRepository.findByIdWithRoleAndCompany(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        if (SecurityUtil.isSupplier()) {
            if (user.getCompany() == null || !order.getSupplierCompany().getId().equals(user.getCompany().getId())) {
                throw new AccessDeniedException("Access denied: Supplier does not own this order");
            }
        } else if (SecurityUtil.isBuyer()) {
            boolean isCreator = order.getCreatedBy().getId().equals(user.getId());
            boolean isSameCompany = user.getCompany() != null && order.getBuyerCompany().getId().equals(user.getCompany().getId());
            if (!isCreator && !isSameCompany) {
                throw new AccessDeniedException("Access denied: Buyer does not own this order");
            }
        } else {
            throw new AccessDeniedException("Access denied");
        }
    }
}

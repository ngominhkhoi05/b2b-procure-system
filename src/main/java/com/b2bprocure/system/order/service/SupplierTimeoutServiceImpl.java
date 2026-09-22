package com.b2bprocure.system.order.service;

import com.b2bprocure.system.common.enums.ErrorCode;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.common.enums.SettingKey;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.setting.service.SystemSettingService;
import com.b2bprocure.system.zalopay.client.ZaloPayClient;
import com.b2bprocure.system.zalopay.client.ZaloPayException;
import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import com.b2bprocure.system.zalopay.service.ZaloPaySignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for handling supplier confirmation timeout and automatic order rejection.
 * Auto-rejects orders when supplier does not confirm/reject within the configured timeout period.
 * For ZaloPay paid orders, also initiates refund via ZaloPay API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierTimeoutServiceImpl implements SupplierTimeoutService {

    private static final int DEFAULT_TIMEOUT_HOURS = 24;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final SystemSettingService systemSettingService;
    private final ZaloPayConfig zaloPayConfig;
    private final ZaloPayClient zaloPayClient;
    private final ZaloPaySignatureService signatureService;

    /**
     * Process all orders that have exceeded their supplier confirmation deadline.
     * Scheduled to run every 5 minutes.
     *
     * Deadline calculation:
     * - COD: order.createdAt + SUPPLIER_CONFIRM_TIMEOUT_HOURS
     * - ZaloPay: payment.paidAt + SUPPLIER_CONFIRM_TIMEOUT_HOURS
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Override
    public void processSupplierTimeouts() {
        log.info("Starting supplier timeout processing");

        try {
            int timeoutHours = systemSettingService.getSettingValueAsInt(
                    SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS, DEFAULT_TIMEOUT_HOURS);

            LocalDateTime now = LocalDateTime.now();
            // COD deadline: expired when now >= createdAt + timeout
            // Which means createdAt <= now - timeout
            LocalDateTime codDeadline = now.minusHours(timeoutHours);
            // ZaloPay deadline: expired when now >= paidAt + timeout
            // Which means paidAt <= now - timeout
            LocalDateTime paidDeadline = now.minusHours(timeoutHours);

            // Find COD orders eligible for timeout (using Order.createdAt)
            List<Order> expiredCodOrders = orderRepository.findExpiredCodOrders(
                    OrderStatus.PENDING_CONFIRMATION,
                    codDeadline
            );

            // Find ZaloPay paid payments eligible for timeout (using Payment.paidAt)
            List<Payment> expiredPaidPayments = paymentRepository.findPaidZaloPayPaymentsForTimeout(
                    PaymentStatus.SUCCESS,
                    paidDeadline
            );

            int total = expiredCodOrders.size() + expiredPaidPayments.size();
            log.info("Found {} orders for supplier timeout processing (COD: {}, ZaloPay: {})",
                    total, expiredCodOrders.size(), expiredPaidPayments.size());

            // Process COD orders
            for (Order order : expiredCodOrders) {
                processOrderTimeout(order);
            }

            // Process ZaloPay paid orders
            for (Payment payment : expiredPaidPayments) {
                Order order = payment.getOrder();
                if (order != null) {
                    processOrderTimeout(order);
                }
            }

        } catch (Exception e) {
            log.error("Error in supplier timeout scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Process timeout for a single order.
     */
    @Transactional(rollbackFor = Exception.class)
    public void processOrderTimeout(Order order) {
        try {
            // Re-acquire lock and re-check status
            Order currentOrder = orderRepository.findByIdWithLock(order.getId()).orElse(null);
            if (currentOrder == null) {
                log.warn("Order not found during timeout processing: orderId={}", order.getId());
                return;
            }

            // Skip if already processed
            if (currentOrder.getStatus() != OrderStatus.PENDING_CONFIRMATION
                    && currentOrder.getStatus() != OrderStatus.PAID) {
                log.debug("Order already processed, skipping: orderId={}, status={}",
                        currentOrder.getId(), currentOrder.getStatus());
                return;
            }

            Payment payment = paymentRepository.findByOrderId(currentOrder.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment not found for order: " + currentOrder.getId()));

            PaymentMethod paymentMethod = payment.getPaymentMethod();

            if (paymentMethod == PaymentMethod.COD) {
                processCodTimeout(currentOrder, payment);
            } else {
                // Online payment (ZaloPay)
                processOnlineTimeout(currentOrder, payment);
            }

        } catch (Exception e) {
            log.error("Error processing timeout for order: orderId={}, error={}",
                    order.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Process COD order timeout.
     * COD: PENDING_CONFIRMATION -> REJECTED -> release reservation
     */
    private void processCodTimeout(Order order, Payment payment) {
        // Verify COD payment status allows rejection
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("COD payment not PENDING, skipping timeout: paymentId={}, status={}",
                    payment.getId(), payment.getStatus());
            return;
        }

        // Update order status
        order.setStatus(OrderStatus.REJECTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Release reservation
        releaseReservation(order);

        // Record history
        recordHistory(order, OrderStatus.REJECTED,
                "SUPPLIER_CONFIRM_TIMEOUT: COD order auto-rejected due to supplier confirmation timeout");

        log.info("COD order auto-rejected: orderId={}, orderCode={}",
                order.getId(), order.getOrderCode());
    }

    /**
     * Process online (ZaloPay) order timeout.
     * For paid orders: PAID -> REJECTED -> REFUND_PENDING -> ZaloPay refund -> REFUNDED -> release reservation
     * For unpaid orders: do nothing (payment timeout handles this separately)
     */
    private void processOnlineTimeout(Order order, Payment payment) {
        // Only process if payment is SUCCESS
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            log.warn("Online payment not SUCCESS, skipping timeout: paymentId={}, status={}",
                    payment.getId(), payment.getStatus());
            return;
        }

        // Update order status
        order.setStatus(OrderStatus.REJECTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Set payment to REFUND_PENDING (DO NOT release reservation yet)
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Record history
        recordHistory(order, OrderStatus.REJECTED,
                "SUPPLIER_CONFIRM_TIMEOUT: Online paid order auto-rejected due to supplier confirmation timeout");

        log.info("Online paid order auto-rejected, initiating refund: orderId={}, orderCode={}",
                order.getId(), order.getOrderCode());

        // Process refund
        processRefund(payment);
    }

    /**
     * Process refund for a payment in REFUND_PENDING state.
     * Idempotent: only processes if payment is still REFUND_PENDING.
     */
    @Transactional(rollbackFor = Exception.class)
    public void processRefund(Payment payment) {
        // Re-acquire lock and re-check status
        Payment current = paymentRepository.findByIdWithLock(payment.getId()).orElse(null);
        if (current == null) {
            log.warn("Payment not found during refund processing: paymentId={}", payment.getId());
            return;
        }

        // Idempotency: only process if still REFUND_PENDING
        if (current.getStatus() != PaymentStatus.REFUND_PENDING) {
            log.debug("Payment no longer REFUND_PENDING, skipping refund: paymentId={}, status={}",
                    current.getId(), current.getStatus());
            return;
        }

        // Verify we have zp_trans_id for refund
        String zpTransId = verifyZpTransId(current);
        if (zpTransId == null) {
            log.error("Cannot process refund - no zp_trans_id: paymentId={}", current.getId());
            return;
        }

        // Build refund request
        long amount = current.getAmount().longValue();
        long timestamp = System.currentTimeMillis();
        String description = "B2B Procure - Order auto-rejected due to supplier timeout";
        long appId = Long.parseLong(zaloPayConfig.getAppId());

        String mac = signatureService.createRefundMac(
                String.valueOf(appId), zpTransId, amount, timestamp, description);

        // Call ZaloPay refund API
        try {
            Map<String, Object> response = zaloPayClient.refund(
                    appId, zpTransId, amount, description, timestamp, mac);

            int returnCode = parseReturnCode(response);

            if (returnCode == 1) {
                // Refund successful
                current.setStatus(PaymentStatus.REFUNDED);
                current.setRefundedAt(LocalDateTime.now());
                current.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(current);

                // Release reservation after successful refund
                Order order = current.getOrder();
                releaseReservation(order);

                log.info("ZaloPay refund SUCCESS: paymentId={}, zpTransId={}, orderId={}",
                        current.getId(), zpTransId, order.getId());
            } else {
                // Refund failed - keep REFUND_PENDING for retry
                String subReturnMessage = parseSubReturnMessage(response);
                log.warn("ZaloPay refund failed, will retry: paymentId={}, returnCode={}, message={}",
                        current.getId(), returnCode, subReturnMessage);
            }

        } catch (ZaloPayException e) {
            log.error("ZaloPay refund API error: paymentId={}, error={}",
                    current.getId(), e.getMessage());
            // Keep REFUND_PENDING, scheduler will retry next run
        }
    }

    /**
     * Verify and extract zp_trans_id from payment.
     * Returns null if not available.
     */
    private String verifyZpTransId(Payment payment) {
        String providerTransId = payment.getProviderTransactionId();

        // providerTransactionId should contain zp_trans_id from callback
        // It may be from create order response (zp_trans_token) or callback (zp_trans_id)
        // ZaloPay refund requires zp_trans_id which is the actual transaction ID
        if (providerTransId == null || providerTransId.isBlank()) {
            log.warn("No provider_transaction_id found: paymentId={}", payment.getId());
            return null;
        }

        // Check if it's a numeric ZaloPay transaction ID
        // ZaloPay transaction IDs are typically numeric
        try {
            Long.parseLong(providerTransId);
            return providerTransId;
        } catch (NumberFormatException e) {
            // It's a token, not a transaction ID
            log.warn("provider_transaction_id is not a valid zp_trans_id: paymentId={}, value={}",
                    payment.getId(), providerTransId);
            return null;
        }
    }

    /**
     * Parse return_code from ZaloPay response.
     */
    private int parseReturnCode(Map<String, Object> response) {
        if (response == null) {
            return -1;
        }
        Object returnCode = response.get("return_code");
        if (returnCode instanceof Number) {
            return ((Number) returnCode).intValue();
        }
        return -1;
    }

    /**
     * Parse sub_return_message from ZaloPay response.
     */
    private String parseSubReturnMessage(Map<String, Object> response) {
        if (response == null) {
            return "Unknown error";
        }
        Object message = response.get("sub_return_message");
        return message != null ? message.toString() : "Unknown error";
    }

    /**
     * Release reservation for order items.
     * Reduces reserved_quantity, NOT stock_quantity.
     */
    private void releaseReservation(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(order.getId());
        if (items.isEmpty()) {
            log.warn("No items found for order: orderId={}", order.getId());
            return;
        }

        Map<Long, Integer> qtyByProductId = items.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getProduct().getId(),
                        Collectors.summingInt(OrderItem::getQuantity)));

        List<Long> productIds = qtyByProductId.keySet().stream().sorted().toList();
        List<Product> products = productRepository.findByIdInWithLock(productIds);

        for (Product product : products) {
            int qty = qtyByProductId.get(product.getId());
            int currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
            product.setReservedQuantity(Math.max(0, currentReserved - qty));
            product.validateInvariants();
        }
        productRepository.saveAll(products);

        log.info("Released reservation for order: orderId={}, items={}", order.getId(), items.size());
    }

    /**
     * Record order status history.
     * For system actions, changedBy is null.
     */
    private void recordHistory(Order order, OrderStatus status, String note) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .changedBy(null) // System action
                .status(status)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(history);
    }
}

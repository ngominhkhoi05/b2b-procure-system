package com.b2bprocure.system.order.service;

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
import com.b2bprocure.system.payment.service.RefundService;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for handling supplier confirmation timeout and automatic order rejection.
 * Auto-rejects orders when supplier does not confirm/reject within the configured timeout period.
 * For ZaloPay paid orders, delegates refund to {@link RefundService}.
 *
 * <p>Refund-specific logic (provider call, REFUND_PENDING -> REFUNDED transition, reservation
 * release after success) lives in {@link RefundService}. This service only handles the
 * timeout detection + state transition to REJECTED, plus the COD reservation release.
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
    private final RefundService refundService;

    /**
     * Process all orders that have exceeded their supplier confirmation deadline.
     * Scheduled to run every 5 minutes.
     *
     * Deadline calculation:
     * - COD: order.createdAt + SUPPLIER_CONFIRM_TIMEOUT_HOURS
     * - ZaloPay: payment.paidAt + SUPPLIER_CONFIRM_TIMEOUT_HOURS
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    @Override
    public void processSupplierTimeouts() {
        log.info("Starting supplier timeout processing");

        try {
            int timeoutHours = systemSettingService.getSettingValueAsInt(
                    SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS, DEFAULT_TIMEOUT_HOURS);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime codDeadline = now.minusHours(timeoutHours);
            LocalDateTime paidDeadline = now.minusHours(timeoutHours);

            List<Order> expiredCodOrders = orderRepository.findExpiredCodOrders(
                    OrderStatus.PENDING_CONFIRMATION,
                    codDeadline
            );

            List<Payment> expiredPaidPayments = paymentRepository.findPaidZaloPayPaymentsForTimeout(
                    PaymentStatus.SUCCESS,
                    paidDeadline
            );

            int total = expiredCodOrders.size() + expiredPaidPayments.size();
            log.info("Found {} orders for supplier timeout processing (COD: {}, ZaloPay: {})",
                    total, expiredCodOrders.size(), expiredPaidPayments.size());

            for (Order order : expiredCodOrders) {
                processOrderTimeout(order);
            }

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
            Order currentOrder = orderRepository.findByIdWithLock(order.getId()).orElse(null);
            if (currentOrder == null) {
                log.warn("Order not found during timeout processing: orderId={}", order.getId());
                return;
            }

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
                processOnlineTimeout(currentOrder, payment);
            }

        } catch (Exception e) {
            log.error("Error processing timeout for order: orderId={}, error={}",
                    order.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Process COD order timeout: REJECT + release reservation immediately.
     */
    private void processCodTimeout(Order order, Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("COD payment not PENDING, skipping timeout: paymentId={}, status={}",
                    payment.getId(), payment.getStatus());
            return;
        }

        order.setStatus(OrderStatus.REJECTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        releaseReservation(order);

        recordHistory(order, OrderStatus.REJECTED,
                "SUPPLIER_CONFIRM_TIMEOUT: COD order auto-rejected due to supplier confirmation timeout");

        log.info("COD order auto-rejected: orderId={}, orderCode={}",
                order.getId(), order.getOrderCode());
    }

    /**
     * Process online (ZaloPay) order timeout:
     * PAID -> REJECTED -> REFUND_PENDING -> ZaloPay refund -> REFUNDED -> release reservation.
     */
    private void processOnlineTimeout(Order order, Payment payment) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            log.warn("Online payment not SUCCESS, skipping timeout: paymentId={}, status={}",
                    payment.getId(), payment.getStatus());
            return;
        }

        order.setStatus(OrderStatus.REJECTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        payment.setStatus(PaymentStatus.REFUND_PENDING);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        recordHistory(order, OrderStatus.REJECTED,
                "SUPPLIER_CONFIRM_TIMEOUT: Online paid order auto-rejected due to supplier confirmation timeout");

        log.info("Online paid order auto-rejected, initiating refund: orderId={}, orderCode={}",
                order.getId(), order.getOrderCode());

        // Delegate refund to RefundService - it owns the API call + reservation release.
        refundService.processRefund(payment);
    }

    /**
     * Release reserved quantity (NOT stock_quantity) for all items in the order.
     * Used for COD timeout path; online refund path delegates to RefundService.
     */
    private void releaseReservation(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(order.getId());
        if (items.isEmpty()) {
            log.warn("No items found for order: orderId={}", order.getId());
            return;
        }

        java.util.Map<Long, Integer> qtyByProductId = items.stream()
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

        log.info("Released reservation for order: orderId={}, items={}",
                order.getId(), items.size());
    }

    /**
     * Record order status history. For system actions, changedBy is null.
     */
    private void recordHistory(Order order, OrderStatus status, String note) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .changedBy(null)
                .status(status)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(history);
    }
}

package com.b2bprocure.system.payment.service;

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

/**
 * Service for handling payment timeout and ZaloPay query operations.
 * This service processes expired ZaloPay payments by querying ZaloPay's API
 * to determine the actual transaction status before updating payment/order state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTimeoutService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ProductRepository productRepository;
    private final ZaloPayConfig zaloPayConfig;
    private final ZaloPayClient zaloPayClient;
    private final ZaloPaySignatureService signatureService;
    private final SystemSettingService systemSettingService;

    /**
     * Scheduled job to process expired ZaloPay payments.
     * Runs every 5 minutes to avoid spamming ZaloPay API.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void processExpiredPayments() {
        log.info("Payment timeout scheduler started");

        List<Payment> expiredPayments = paymentRepository.findExpiredPaymentsWithLock(
                PaymentStatus.PENDING,
                LocalDateTime.now()
        );

        if (expiredPayments.isEmpty()) {
            log.debug("No expired payments found to process");
            return;
        }

        log.info("Found {} expired payments to process", expiredPayments.size());

        for (Payment payment : expiredPayments) {
            try {
                processExpiredPayment(payment);
            } catch (Exception e) {
                log.error("Failed to process expired payment: paymentId={}, error={}",
                        payment.getId(), e.getMessage(), e);
            }
        }

        log.info("Payment timeout scheduler completed");
    }

    /**
     * Process a single expired payment by querying ZaloPay.
     * This method is transactional to ensure atomicity of each payment processing.
     */
    @Transactional(rollbackFor = Exception.class)
    public void processExpiredPayment(Payment payment) {
        log.info("Processing expired payment: paymentId={}, appTransId={}, expiredAt={}",
                payment.getId(), payment.getAppTransId(), payment.getExpiredAt());

        // Re-check status inside transaction (idempotency check)
        Payment currentPayment = paymentRepository.findByIdWithLock(payment.getId())
                .orElse(null);

        if (currentPayment == null) {
            log.warn("Payment not found during processing: paymentId={}", payment.getId());
            return;
        }

        // Skip if no longer PENDING (callback might have processed it)
        if (currentPayment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment no longer PENDING, skipping: paymentId={}, currentStatus={}",
                    currentPayment.getId(), currentPayment.getStatus());
            return;
        }

        // Skip COD payments
        if (currentPayment.getPaymentMethod() == PaymentMethod.COD) {
            log.debug("Skipping COD payment: paymentId={}", currentPayment.getId());
            return;
        }

        // Skip if no appTransId (ZaloPay not initiated)
        if (currentPayment.getAppTransId() == null || currentPayment.getAppTransId().isBlank()) {
            log.warn("Payment has no appTransId, marking as EXPIRED: paymentId={}", currentPayment.getId());
            expirePayment(currentPayment);
            return;
        }

        // Query ZaloPay for actual status
        ZaloPayQueryResult result = queryZaloPayStatus(currentPayment);

        switch (result) {
            case SUCCESS -> handleZaloPaySuccess(currentPayment);
            case FAIL -> handleZaloPayFail(currentPayment);
            case PROCESSING -> {
                // Do nothing - payment still processing, will be checked again next scheduler run
                log.debug("ZaloPay still processing payment: paymentId={}, appTransId={}",
                        currentPayment.getId(), currentPayment.getAppTransId());
            }
            case ERROR -> {
                // Log error but don't change state - will retry next scheduler run
                log.warn("ZaloPay query returned error for payment: paymentId={}, will retry later",
                        currentPayment.getId());
            }
        }
    }

    /**
     * Query ZaloPay API for order status.
     */
    private ZaloPayQueryResult queryZaloPayStatus(Payment payment) {
        try {
            long appId = Long.parseLong(zaloPayConfig.getAppId());
            String appTransId = payment.getAppTransId();
            String mac = signatureService.createQueryOrderMac(
                    zaloPayConfig.getAppId(),
                    appTransId
            );

            Map<String, Object> response = zaloPayClient.queryOrderStatus(appId, appTransId, mac);

            log.info("ZaloPay query response: paymentId={}, appTransId={}, returnCode={}, returnMessage={}",
                    payment.getId(), appTransId,
                    response.get("return_code"), response.get("return_message"));

            // Parse return_code
            // 1 = SUCCESS, 2 = FAIL, 3 = PROCESSING
            Object returnCodeObj = response.get("return_code");
            if (returnCodeObj == null) {
                log.error("ZaloPay query response missing return_code: paymentId={}", payment.getId());
                return ZaloPayQueryResult.ERROR;
            }

            int returnCode;
            if (returnCodeObj instanceof Number) {
                returnCode = ((Number) returnCodeObj).intValue();
            } else {
                try {
                    returnCode = Integer.parseInt(returnCodeObj.toString());
                } catch (NumberFormatException e) {
                    log.error("ZaloPay query return_code is not a number: {} for paymentId={}",
                            returnCodeObj, payment.getId());
                    return ZaloPayQueryResult.ERROR;
                }
            }

            return switch (returnCode) {
                case 1 -> ZaloPayQueryResult.SUCCESS;
                case 2 -> ZaloPayQueryResult.FAIL;
                case 3 -> ZaloPayQueryResult.PROCESSING;
                default -> {
                    log.warn("ZaloPay query unknown return_code: {} for paymentId={}",
                            returnCode, payment.getId());
                    yield ZaloPayQueryResult.ERROR;
                }
            };

        } catch (ZaloPayException e) {
            log.error("ZaloPay query API error: paymentId={}, error={}",
                    payment.getId(), e.getMessage());
            return ZaloPayQueryResult.ERROR;
        } catch (Exception e) {
            log.error("Unexpected error querying ZaloPay: paymentId={}, error={}",
                    payment.getId(), e.getMessage(), e);
            return ZaloPayQueryResult.ERROR;
        }
    }

    /**
     * Handle successful payment from ZaloPay.
     * Same logic as callback: update payment to SUCCESS and order to PAID.
     */
    private void handleZaloPaySuccess(Payment payment) {
        log.info("ZaloPay query returned SUCCESS: paymentId={}, appTransId={}",
                payment.getId(), payment.getAppTransId());

        // Idempotency: check again inside this method
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment already processed, skipping SUCCESS update: paymentId={}, status={}",
                    payment.getId(), payment.getStatus());
            return;
        }

        // Update Payment
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Update Order
        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING_CONFIRMATION) {
            order.setStatus(OrderStatus.PAID);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            // Record history
            OrderStatusHistory history = OrderStatusHistory.builder()
                    .order(order)
                    .changedBy(null)
                    .status(OrderStatus.PAID)
                    .note("Payment confirmed via ZaloPay query (scheduler). Transaction ID: " + payment.getAppTransId())
                    .createdAt(LocalDateTime.now())
                    .build();
            orderStatusHistoryRepository.save(history);

            log.info("Payment SUCCESS via query: paymentId={}, orderId={}", payment.getId(), order.getId());
        } else {
            log.warn("Order not PENDING_CONFIRMATION during SUCCESS update: orderId={}, status={}",
                    order.getId(), order.getStatus());
        }
    }

    /**
     * Handle failed payment from ZaloPay.
     * Expire payment, cancel order, and release reservation.
     */
    private void handleZaloPayFail(Payment payment) {
        log.info("ZaloPay query returned FAIL: paymentId={}, appTransId={}",
                payment.getId(), payment.getAppTransId());

        expirePayment(payment);
    }

    /**
     * Mark payment as EXPIRED, cancel order, and release reservation.
     */
    private void expirePayment(Payment payment) {
        // Idempotency: check again
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment already processed, skipping expire: paymentId={}, status={}",
                    payment.getId(), payment.getStatus());
            return;
        }

        // Update Payment to EXPIRED
        payment.setStatus(PaymentStatus.EXPIRED);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Cancel Order if still PENDING_CONFIRMATION
        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING_CONFIRMATION) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            // Record history
            OrderStatusHistory history = OrderStatusHistory.builder()
                    .order(order)
                    .changedBy(null)
                    .status(OrderStatus.CANCELLED)
                    .note("Payment expired (ZaloPay timeout). Order automatically cancelled.")
                    .createdAt(LocalDateTime.now())
                    .build();
            orderStatusHistoryRepository.save(history);

            // Release reservation
            releaseReservation(order);

            log.info("Payment EXPIRED and Order CANCELLED: paymentId={}, orderId={}",
                    payment.getId(), order.getId());
        } else {
            log.warn("Order not PENDING_CONFIRMATION during expire: orderId={}, status={}",
                    order.getId(), order.getStatus());
        }
    }

    /**
     * Release reserved quantity for all items in the order.
     */
    private void releaseReservation(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            if (product != null) {
                int reservedToRelease = item.getQuantity();
                int currentReserved = product.getReservedQuantity() != null
                        ? product.getReservedQuantity() : 0;
                int newReserved = Math.max(0, currentReserved - reservedToRelease);

                product.setReservedQuantity(newReserved);
                product.validateInvariants();
                productRepository.save(product);

                log.debug("Released reservation: productId={}, releasedQty={}, newReserved={}",
                        product.getId(), reservedToRelease, newReserved);
            }
        }
    }

    /**
     * Result of ZaloPay query order status.
     */
    private enum ZaloPayQueryResult {
        SUCCESS,
        FAIL,
        PROCESSING,
        ERROR
    }
}

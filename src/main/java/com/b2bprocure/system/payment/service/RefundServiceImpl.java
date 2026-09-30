package com.b2bprocure.system.payment.service;

import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.zalopay.client.ZaloPayClient;
import com.b2bprocure.system.zalopay.client.ZaloPayException;
import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import com.b2bprocure.system.zalopay.service.ZaloPaySignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ZaloPay refund implementation. See {@link RefundService} for contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ZaloPayConfig zaloPayConfig;
    private final ZaloPayClient zaloPayClient;
    private final ZaloPaySignatureService signatureService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processRefund(Payment payment) {
        if (payment == null) {
            return;
        }

        // Re-acquire pessimistic lock and re-check status (idempotency guard).
        Payment current = paymentRepository.findByIdWithLock(payment.getId()).orElse(null);
        if (current == null) {
            log.warn("Refund skipped - payment not found: paymentId={}", payment.getId());
            return;
        }

        if (current.getStatus() != PaymentStatus.REFUND_PENDING) {
            log.debug("Refund skipped - payment not in REFUND_PENDING: paymentId={}, status={}",
                    current.getId(), current.getStatus());
            return;
        }

        if (current.getPaymentMethod() == PaymentMethod.COD) {
            log.warn("Refund skipped - COD payment has no online refund: paymentId={}",
                    current.getId());
            return;
        }

        String zpTransId = verifyZpTransId(current);
        if (zpTransId == null) {
            log.error("Refund skipped - no valid zp_trans_id: paymentId={}", current.getId());
            return;
        }

        long amount = current.getAmount().longValue();
        long timestamp = System.currentTimeMillis();
        String description = "B2B Procure - Refund for order " +
                (current.getOrder() != null ? current.getOrder().getOrderCode() : current.getId());
        long appId = Long.parseLong(zaloPayConfig.getAppId());

        String mac = signatureService.createRefundMac(
                String.valueOf(appId), zpTransId, amount, timestamp, description);

        try {
            Map<String, Object> response = zaloPayClient.refund(
                    appId, zpTransId, amount, description, timestamp, mac);

            int returnCode = parseReturnCode(response);

            if (returnCode == 1) {
                current.setStatus(PaymentStatus.REFUNDED);
                current.setRefundedAt(LocalDateTime.now());
                current.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(current);

                Order order = current.getOrder();
                if (order != null) {
                    releaseReservation(order);
                }

                log.info("ZaloPay refund SUCCESS: paymentId={}, zpTransId={}, orderId={}",
                        current.getId(), zpTransId,
                        order != null ? order.getId() : null);
            } else {
                String subReturnMessage = parseSubReturnMessage(response);
                log.warn("ZaloPay refund failed, will retry: paymentId={}, returnCode={}, message={}",
                        current.getId(), returnCode, subReturnMessage);
                // Keep REFUND_PENDING; scheduler will retry.
            }

        } catch (ZaloPayException e) {
            log.error("ZaloPay refund API error: paymentId={}, error={}",
                    current.getId(), e.getMessage());
            // Keep REFUND_PENDING, scheduler will retry next run.
        }
    }

    /**
     * Verify and extract zp_trans_id from payment.
     * Returns null if not available or not numeric.
     */
    private String verifyZpTransId(Payment payment) {
        String providerTransId = payment.getProviderTransactionId();

        if (providerTransId == null || providerTransId.isBlank()) {
            log.warn("No provider_transaction_id found: paymentId={}", payment.getId());
            return null;
        }

        // ZaloPay transaction IDs are numeric strings (e.g. "240930000123456").
        // providerTransactionId may contain zp_trans_token from create-order response;
        // only a numeric value is acceptable for the refund endpoint.
        try {
            Long.parseLong(providerTransId);
            return providerTransId;
        } catch (NumberFormatException e) {
            log.warn("provider_transaction_id is not a valid zp_trans_id: paymentId={}, value={}",
                    payment.getId(), providerTransId);
            return null;
        }
    }

    /**
     * Release reserved quantity (NOT stock_quantity) for all items in the order.
     * Invariants are validated on each product to prevent negative reserved_quantity.
     */
    private void releaseReservation(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdWithProduct(order.getId());
        if (items.isEmpty()) {
            log.warn("No items found for order: orderId={}", order.getId());
            return;
        }

        for (OrderItem item : items) {
            Product product = item.getProduct();
            if (product == null) {
                continue;
            }
            int qty = item.getQuantity();
            int currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
            product.setReservedQuantity(Math.max(0, currentReserved - qty));
            product.validateInvariants();
        }
        productRepository.saveAll(items.stream()
                .map(OrderItem::getProduct)
                .filter(p -> p != null)
                .toList());

        log.info("Released reservation for order: orderId={}, items={}",
                order.getId(), items.size());
    }

    /**
     * Parse return_code from ZaloPay response. Defaults to -1 if missing or non-numeric.
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
}

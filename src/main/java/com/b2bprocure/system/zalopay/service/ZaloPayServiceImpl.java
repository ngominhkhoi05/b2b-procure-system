package com.b2bprocure.system.zalopay.service;

import com.b2bprocure.system.cart.entity.CartItem;
import com.b2bprocure.system.cart.repository.CartItemRepository;
import com.b2bprocure.system.common.enums.ErrorCode;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.b2bprocure.system.zalopay.client.ZaloPayClient;
import com.b2bprocure.system.zalopay.client.ZaloPayException;
import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import com.b2bprocure.system.zalopay.dto.ZaloPayCallbackData;
import com.b2bprocure.system.zalopay.dto.ZaloPayCallbackRequest;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreateOrderResponse;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreatePaymentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZaloPayServiceImpl implements ZaloPayService {

    private final ZaloPayConfig zaloPayConfig;
    private final ZaloPayClient zaloPayClient;
    private final ZaloPaySignatureService signatureService;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ZaloPayCreatePaymentResponse initiatePayment(Long paymentId, Long orderId) {
        // 1. Authenticate Buyer
        Long currentUserId = SecurityUtil.getCurrentUserIdOrThrow();
        if (!SecurityUtil.isBuyer()) {
            throw new AccessDeniedException("Access denied: Only buyers can initiate ZaloPay payment");
        }

        User buyer = userRepository.findByIdWithRoleAndCompany(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        // 2. Load and validate Payment + Order ownership
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        Order order = payment.getOrder();

        if (order == null) {
            throw new ResourceNotFoundException("Order", "paymentId", paymentId);
        }

        if (!order.getId().equals(orderId)) {
            throw new BusinessException("Payment does not belong to the specified order", HttpStatus.BAD_REQUEST);
        }

        // 3. Validate ownership: buyer must own this order
        boolean isCreator = order.getCreatedBy().getId().equals(buyer.getId());
        boolean isSameCompany = buyer.getCompany() != null
                && order.getBuyerCompany().getId().equals(buyer.getCompany().getId());
        if (!isCreator && !isSameCompany) {
            throw new AccessDeniedException("Access denied: Order does not belong to the current buyer");
        }

        // 4. Validate payment status
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException("Payment is not in PENDING status. Current status: " + payment.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        if (payment.getAppTransId() != null) {
            // Already initiated - return existing URL if available
            String existingUrl = getExistingPaymentUrl(payment);
            if (existingUrl != null) {
                log.info("ZaloPay payment already initiated for paymentId={}, returning existing URL", paymentId);
                return ZaloPayCreatePaymentResponse.builder()
                        .paymentId(paymentId)
                        .orderCode(order.getOrderCode())
                        .paymentUrl(existingUrl)
                        .build();
            }
        }

        // 5. Build ZaloPay order params
        String appTransId = generateAppTransId(order);
        String appUser = buyer.getUsername();
        long amount = payment.getAmount().longValue();
        String description = "B2B Procure - Order #" + order.getOrderCode();
        String itemJson = buildItemJson(order);
        String embedData = "{}";

        // 6. Call ZaloPay API
        ZaloPayCreateOrderResponse zpResponse;
        try {
            zpResponse = zaloPayClient.createOrder(appTransId, appUser, amount, itemJson, embedData, description);
        } catch (ZaloPayException e) {
            log.error("ZaloPay create order failed for paymentId={}: {}", paymentId, e.getMessage());
            throw new BusinessException(ErrorCode.ZALOPAY_CREATE_ORDER_FAILED,
                    "Failed to create ZaloPay order: " + e.getMessage());
        }

        // 7. Handle ZaloPay response
        if (!zpResponse.isSuccess()) {
            log.error("ZaloPay returned error for paymentId={}: returnCode={}, subReturnCode={}, message={}",
                    paymentId, zpResponse.getReturnCode(), zpResponse.getSubReturnCode(),
                    zpResponse.getSubReturnMessage());
            throw new BusinessException(ErrorCode.ZALOPAY_CREATE_ORDER_FAILED,
                    "ZaloPay order creation failed: " + zpResponse.getSubReturnMessage());
        }

        if (zpResponse.getOrderUrl() == null || zpResponse.getOrderUrl().isBlank()) {
            log.error("ZaloPay returned success but no orderUrl for paymentId={}", paymentId);
            throw new BusinessException(ErrorCode.ZALOPAY_INVALID_RESPONSE,
                    "ZaloPay API returned invalid response: missing order_url");
        }

        // 8. Update Payment with ZaloPay info
        payment.setAppTransId(appTransId);
        payment.setProviderTransactionId(
                zpResponse.getZpTransToken() != null ? zpResponse.getZpTransToken() : zpResponse.getOrderToken()
        );
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("ZaloPay payment initiated: paymentId={}, appTransId={}, orderUrl={}",
                paymentId, appTransId, zpResponse.getOrderUrl());

        return ZaloPayCreatePaymentResponse.builder()
                .paymentId(paymentId)
                .orderCode(order.getOrderCode())
                .paymentUrl(zpResponse.getOrderUrl())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleCallback(ZaloPayCallbackRequest callbackRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Validate required fields
            if (callbackRequest.getData() == null || callbackRequest.getData().isBlank()) {
                log.warn("ZaloPay callback received with empty data");
                response.put("return_code", -1);
                response.put("return_message", "Empty callback data");
                return response;
            }

            if (callbackRequest.getMac() == null || callbackRequest.getMac().isBlank()) {
                log.warn("ZaloPay callback received with empty MAC");
                response.put("return_code", -1);
                response.put("return_message", "Empty MAC");
                return response;
            }

            // 2. Verify MAC using key2
            boolean isValidMac = signatureService
                    .verifyCallbackMac(callbackRequest.getData(), callbackRequest.getMac());

            if (!isValidMac) {
                log.warn("ZaloPay callback MAC verification failed");
                response.put("return_code", -1);
                response.put("return_message", "mac not equal");
                return response;
            }

            // 3. Parse callback data
            ZaloPayCallbackData callbackData;
            try {
                callbackData = objectMapper.readValue(callbackRequest.getData(), ZaloPayCallbackData.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse ZaloPay callback data: {}", callbackRequest.getData(), e);
                response.put("return_code", 0);
                response.put("return_message", "Invalid callback data format");
                return response;
            }

            log.info("ZaloPay callback received: appTransId={}, zpTransId={}, amount={}",
                    callbackData.getAppTransId(), callbackData.getZpTransId(), callbackData.getAmount());

            // 4. Find Payment by app_trans_id with pessimistic lock
            Payment payment = paymentRepository.findByAppTransIdWithLock(callbackData.getAppTransId())
                    .orElseThrow(() -> {
                        log.warn("ZaloPay callback: payment not found for appTransId={}",
                                callbackData.getAppTransId());
                        return new BusinessException(ErrorCode.ZALOPAY_PAYMENT_NOT_FOUND,
                                "Payment not found for app_trans_id: " + callbackData.getAppTransId());
                    });

            // 5. Idempotency check
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.info("ZaloPay callback idempotent: payment already SUCCESS, paymentId={}, appTransId={}",
                        payment.getId(), callbackData.getAppTransId());
                response.put("return_code", 1);
                response.put("return_message", "success");
                return response;
            }

            // 6. Validate payment status allows transition
            if (payment.getStatus() != PaymentStatus.PENDING) {
                log.warn("ZaloPay callback: payment not PENDING, paymentId={}, status={}",
                        payment.getId(), payment.getStatus());
                response.put("return_code", 0);
                response.put("return_message", "Payment status not PENDING");
                return response;
            }

            // 7. Amount validation
            BigDecimal callbackAmount = BigDecimal.valueOf(callbackData.getAmount());
            if (callbackAmount.compareTo(payment.getAmount()) != 0) {
                log.error("ZaloPay callback amount mismatch: callbackAmount={}, paymentAmount={}, paymentId={}",
                        callbackAmount, payment.getAmount(), payment.getId());
                response.put("return_code", 0);
                response.put("return_message", "Amount mismatch");
                return response;
            }

            // 8. Update Payment to SUCCESS
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());
            if (payment.getProviderTransactionId() == null) {
                payment.setProviderTransactionId(String.valueOf(callbackData.getZpTransId()));
            }
            paymentRepository.save(payment);

            // 9. Update Order to PAID
            Order order = payment.getOrder();
            log.info("ZaloPay callback - checking order: orderId={}, currentStatus={}, expectedStatus={}",
                    order.getId(), order.getStatus(), OrderStatus.PENDING_CONFIRMATION);
            if (order.getStatus() == OrderStatus.PENDING_CONFIRMATION) {
                order.setStatus(OrderStatus.PAID);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);

                // 10. Record OrderStatusHistory (system action, changedBy = null)
                OrderStatusHistory history = OrderStatusHistory.builder()
                        .order(order)
                        .changedBy(null)
                        .status(OrderStatus.PAID)
                        .note("Payment confirmed via ZaloPay. Transaction ID: " + callbackData.getZpTransId())
                        .createdAt(LocalDateTime.now())
                        .build();
                orderStatusHistoryRepository.save(history);

                log.info("ZaloPay payment SUCCESS: orderId={}, paymentId={}, zpTransId={}",
                        order.getId(), payment.getId(), callbackData.getZpTransId());
            } else {
                log.warn("ZaloPay callback: order not PENDING_CONFIRMATION, orderId={}, status={}",
                        order.getId(), order.getStatus());
            }

            response.put("return_code", 1);
            response.put("return_message", "success");
            return response;

        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.ZALOPAY_PAYMENT_NOT_FOUND) {
                response.put("return_code", -1);
                response.put("return_message", "Payment not found");
                return response;
            }
            log.error("ZaloPay callback processing error: {}", e.getMessage(), e);
            response.put("return_code", 0);
            response.put("return_message", e.getMessage());
            return response;
        } catch (Exception e) {
            log.error("ZaloPay callback unexpected error: {}", e.getMessage(), e);
            response.put("return_code", 0);
            response.put("return_message", "Internal error: " + e.getMessage());
            return response;
        }
    }

    /**
     * Generate app_trans_id for ZaloPay.
     * Format: yyMMdd_orderId_random (max 40 chars).
     */
    private String generateAppTransId(Order order) {
        String datePrefix = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        // Format: yyMMdd_orderId_uniquePart
        // Max length: 40 chars
        String transId = String.format("%s_%d_%s",
                datePrefix,
                order.getId(),
                java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        if (transId.length() > 40) {
            transId = transId.substring(0, 40);
        }
        return transId;
    }

    /**
     * Build item JSON for ZaloPay from order items.
     */
    private String buildItemJson(Order order) {
        try {
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            if (items.isEmpty()) {
                return "[]";
            }
            java.util.ArrayList<Map<String, Object>> itemList = new java.util.ArrayList<>();
            for (OrderItem item : items) {
                Map<String, Object> itemMap = new java.util.LinkedHashMap<>();
                itemMap.put("itemid", item.getProduct() != null ? item.getProduct().getId().toString() : "N/A");
                itemMap.put("itemname", item.getProductName());
                itemMap.put("itemprice", item.getUnitPrice().multiply(BigDecimal.valueOf(100)).intValue()); // ZaloPay uses cents
                itemMap.put("itemquantity", item.getQuantity());
                itemList.add(itemMap);
            }
            return objectMapper.writeValueAsString(itemList);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize order items to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Get existing payment URL if payment was already initiated.
     */
    private String getExistingPaymentUrl(Payment payment) {
        if (payment.getProviderTransactionId() != null) {
            // If we have the token, we could reconstruct, but for safety return null
            // and let frontend re-initiate
            return null;
        }
        return null;
    }
}

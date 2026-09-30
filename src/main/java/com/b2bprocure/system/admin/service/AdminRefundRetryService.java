package com.b2bprocure.system.admin.service;

import com.b2bprocure.system.admin.dto.AdminRefundRetryResponse;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.payment.service.RefundService;
import com.b2bprocure.system.zalopay.client.ZaloPayClient;
import com.b2bprocure.system.zalopay.client.ZaloPayException;
import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import com.b2bprocure.system.zalopay.service.ZaloPaySignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Admin-only recovery path for ZaloPay payments stuck in REFUND_PENDING because
 * the legacy bug stored {@code zp_trans_token} in {@code provider_transaction_id}.
 *
 * <p>The flow is:
 * <ol>
 *   <li>Look up the Payment row (must be in REFUND_PENDING, payment method ZALOPAY).</li>
 *   <li>Call ZaloPay {@code /v2/query} with the stored {@code app_trans_id} to fetch
 *       the authoritative {@code zp_trans_id}.</li>
 *   <li>Persist the real id back to {@code provider_transaction_id} so that
 *       {@link RefundServiceImpl#verifyZpTransId} stops rejecting it.</li>
 *   <li>Hand the row to {@link RefundService#processRefund} which calls
 *       ZaloPay {@code /v2/refund} and updates the state on success.</li>
 * </ol>
 *
 * <p>This endpoint exists for one-time cleanup of the 3 payments stuck at the
 * time of the fix; once production data has been backfilled it is no longer
 * needed but is left in place as an operational safety net.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRefundRetryService {

    private static final String OUTCOME_REFUNDED = "REFUNDED";
    private static final String OUTCOME_REFUND_PENDING = "REFUND_PENDING";
    private static final String OUTCOME_INVALID_STATE = "INVALID_STATE";
    private static final String OUTCOME_QUERY_FAILED = "ZALOPAY_QUERY_FAILED";

    private final PaymentRepository paymentRepository;
    private final ZaloPayClient zaloPayClient;
    private final ZaloPayConfig zaloPayConfig;
    private final ZaloPaySignatureService signatureService;
    private final RefundService refundService;

    /**
     * Retry refund for a single payment. Idempotent — calling twice on the same
     * id is safe; the second call returns {@code INVALID_STATE} once the row
     * is already REFUNDED.
     *
     * @param paymentId the payment to retry
     * @return outcome + current Payment state snapshot
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminRefundRetryResponse retryRefund(Long paymentId) {
        log.info("Admin refund retry requested: paymentId={}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElse(null);
        if (payment == null) {
            return AdminRefundRetryResponse.builder()
                    .paymentId(paymentId)
                    .outcome("PAYMENT_NOT_FOUND")
                    .message("Payment not found")
                    .build();
        }

        // Snapshot what we already know before mutation, so the response can show
        // the original app_trans_id even after the row is updated.
        String appTransId = payment.getAppTransId();
        Long orderId = payment.getOrder() != null ? payment.getOrder().getId() : null;

        if (payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            return AdminRefundRetryResponse.builder()
                    .paymentId(paymentId)
                    .orderId(orderId)
                    .status(payment.getStatus())
                    .amount(payment.getAmount())
                    .providerTransactionId(payment.getProviderTransactionId())
                    .outcome(OUTCOME_INVALID_STATE)
                    .message("Payment is not in REFUND_PENDING; current status: " + payment.getStatus())
                    .build();
        }

        if (appTransId == null || appTransId.isBlank()) {
            return AdminRefundRetryResponse.builder()
                    .paymentId(paymentId)
                    .orderId(orderId)
                    .status(payment.getStatus())
                    .amount(payment.getAmount())
                    .providerTransactionId(payment.getProviderTransactionId())
                    .outcome(OUTCOME_QUERY_FAILED)
                    .message("Payment has no app_trans_id; cannot query ZaloPay for the real zp_trans_id")
                    .build();
        }

        // 1. Ask ZaloPay /v2/query for the authoritative transaction id.
        String realZpTransId = fetchRealZpTransId(appTransId);
        if (realZpTransId == null) {
            return AdminRefundRetryResponse.builder()
                    .paymentId(paymentId)
                    .orderId(orderId)
                    .status(payment.getStatus())
                    .amount(payment.getAmount())
                    .providerTransactionId(payment.getProviderTransactionId())
                    .outcome(OUTCOME_QUERY_FAILED)
                    .message("ZaloPay /v2/query did not return a zp_trans_id for app_trans_id=" + appTransId)
                    .build();
        }

        // 2. Persist the backfilled id. From here on RefundService will treat
        //    the row as having a valid zp_trans_id.
        log.info("Backfilling provider_transaction_id from legacy token to real zp_trans_id: " +
                        "paymentId={}, oldValue={}, newValue={}",
                paymentId, payment.getProviderTransactionId(), realZpTransId);
        payment.setProviderTransactionId(realZpTransId);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // 3. Delegate to the standard refund flow (pessimistic lock, idempotent).
        refundService.processRefund(payment);

        // 4. Reload to capture status/refundedAt after processRefund.
        Payment after = paymentRepository.findById(paymentId).orElse(payment);
        Order order = after.getOrder();
        return AdminRefundRetryResponse.builder()
                .paymentId(paymentId)
                .orderId(order != null ? order.getId() : null)
                .status(after.getStatus())
                .amount(after.getAmount())
                .providerTransactionId(after.getProviderTransactionId())
                .refundedAt(after.getRefundedAt())
                .outcome(after.getStatus() == PaymentStatus.REFUNDED
                        ? OUTCOME_REFUNDED
                        : OUTCOME_REFUND_PENDING)
                .message(after.getStatus() == PaymentStatus.REFUNDED
                        ? "Refund completed successfully"
                        : "Refund attempt finished; payment still in " + after.getStatus())
                .build();
    }

    /**
     * Call ZaloPay /v2/query and return the {@code zp_trans_id} if available.
     * Returns null on any error or if the response is missing the field.
     */
    private String fetchRealZpTransId(String appTransId) {
        try {
            long appId = Long.parseLong(zaloPayConfig.getAppId());
            String mac = signatureService.createQueryOrderMac(
                    zaloPayConfig.getAppId(), appTransId);
            Map<String, Object> response = zaloPayClient.queryOrderStatus(appId, appTransId, mac);
            log.info("ZaloPay /v2/query response for app_trans_id={}: {}", appTransId, response);

            Object returnCode = response.get("return_code");
            int code = returnCode instanceof Number
                    ? ((Number) returnCode).intValue()
                    : -1;
            if (code != 1) {
                log.warn("ZaloPay /v2/query did not return SUCCESS: app_trans_id={}, return_code={}",
                        appTransId, code);
                return null;
            }

            Object zpTransId = response.get("zp_trans_id");
            if (zpTransId == null) {
                return null;
            }
            String id = String.valueOf(zpTransId);
            // Sanity-check that it really is numeric before persisting, otherwise
            // we'd just re-introduce the same bug on the backfilled row.
            try {
                Long.parseLong(id);
                return id;
            } catch (NumberFormatException e) {
                log.warn("ZaloPay /v2/query zp_trans_id is not numeric: {}", id);
                return null;
            }
        } catch (ZaloPayException e) {
            log.error("ZaloPay /v2/query failed for app_trans_id={}: {}", appTransId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error querying ZaloPay for app_trans_id={}: {}",
                    appTransId, e.getMessage(), e);
            return null;
        }
    }
}
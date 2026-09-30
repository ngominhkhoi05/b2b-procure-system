package com.b2bprocure.system.admin.dto;

import com.b2bprocure.system.common.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of an admin-triggered refund retry.
 * Shows the operation outcome plus the current Payment state so the caller
 * can verify the backfill took effect.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRefundRetryResponse {

    private Long paymentId;
    private Long orderId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String providerTransactionId;
    private LocalDateTime refundedAt;

    /**
     * One of:
     * <ul>
     *   <li>{@code REFUNDED} — ZaloPay /v2/refund accepted the request and the
     *       Payment row was moved to REFUNDED.</li>
     *   <li>{@code REFUND_PENDING} — backfill ran but ZaloPay returned a
     *       non-success code (e.g. already-refunded or amount mismatch); the
     *       payment remains REFUND_PENDING for the scheduler.</li>
     *   <li>{@code INVALID_STATE} — payment was not in REFUND_PENDING; no
     *       side-effect, caller should look at the order/payment state instead.</li>
     *   <li>{@code ZALOPAY_QUERY_FAILED} — could not obtain the real zp_trans_id
     *       from ZaloPay /v2/query; manual intervention required.</li>
     * </ul>
     */
    private String outcome;

    private String message;
}
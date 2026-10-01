package com.b2bprocure.system.payment.service;

import com.b2bprocure.system.payment.entity.Payment;

/**
 * Centralized refund processing for online payments (currently ZaloPay).
 *
 * <p>This service owns the lifecycle of a refund:
 * <ul>
 *   <li>Call the provider's refund API.</li>
 *   <li>Update Payment status from {@code REFUND_PENDING} to {@code REFUNDED} on success.</li>
 *   <li>Release product reservations after a successful refund only.</li>
 *   <li>Keep status at {@code REFUND_PENDING} on failure so the scheduler can retry.</li>
 * </ul>
 *
 * <p>Idempotency: {@link #processRefund(Payment)} checks the current Payment status
 * and re-acquires a pessimistic write lock before any external API call, so multiple
 * concurrent invocations (e.g. inline call from rejectOrder/cancelOrder and a retry
 * from the scheduler) will not trigger duplicate refunds.
 */
public interface RefundService {

    /**
     * Process a refund for the given Payment.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If Payment status is not {@code REFUND_PENDING}, the call is a no-op.</li>
     *   <li>If Payment method is not online (e.g. COD), the call is a no-op.</li>
     *   <li>If {@code providerTransactionId} is missing or invalid, logs error and returns.</li>
     *   <li>On successful provider response: sets Payment status to {@code REFUNDED},
     *       sets {@code refundedAt}, then releases product reservations.</li>
     *   <li>On failure: keeps Payment status at {@code REFUND_PENDING} for the scheduler
     *       to retry. Never throws to the caller.</li>
     * </ul>
     *
     * @param payment the Payment in {@code REFUND_PENDING} state
     */
    void processRefund(Payment payment);
}

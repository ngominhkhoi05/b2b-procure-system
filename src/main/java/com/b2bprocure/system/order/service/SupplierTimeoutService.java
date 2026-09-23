package com.b2bprocure.system.order.service;

/**
 * Service for handling supplier confirmation timeout.
 * Auto-rejects orders when supplier does not confirm/reject within the configured timeout period.
 */
public interface SupplierTimeoutService {

    /**
     * Process all orders that have exceeded their supplier confirmation deadline.
     * This method is called by the scheduler.
     *
     * For COD orders: rejects if now >= createdAt + SUPPLIER_CONFIRM_TIMEOUT_HOURS
     * For ZaloPay paid orders: rejects and initiates refund if now >= paidAt + SUPPLIER_CONFIRM_TIMEOUT_HOURS
     */
    void processSupplierTimeouts();
}

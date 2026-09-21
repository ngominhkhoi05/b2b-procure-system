package com.b2bprocure.system.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    PENDING("Chờ thanh toán"),
    SUCCESS("Thành công"),
    FAILED("Thất bại"),
    EXPIRED("Hết hạn"),
    REFUND_PENDING("Chờ hoàn tiền"),
    REFUNDED("Đã hoàn tiền");

    private final String description;

    /**
     * Check if transitioning from current payment status to target status is valid.
     * Transitions:
     * PENDING -> SUCCESS, FAILED, EXPIRED
     * SUCCESS -> REFUND_PENDING
     * REFUND_PENDING -> REFUNDED
     */
    public boolean canTransitionTo(PaymentStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == SUCCESS || target == FAILED || target == EXPIRED;
            case SUCCESS -> target == REFUND_PENDING;
            case REFUND_PENDING -> target == REFUNDED;
            case FAILED, EXPIRED, REFUNDED -> false;
        };
    }
}

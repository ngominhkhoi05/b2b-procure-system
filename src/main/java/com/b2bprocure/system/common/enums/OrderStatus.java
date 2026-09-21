package com.b2bprocure.system.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING_CONFIRMATION("Chờ xác nhận"),
    PAID("Đã thanh toán"),
    CONFIRMED("Đã xác nhận"),
    PREPARING("Đang chuẩn bị hàng"),
    SHIPPING("Đang giao hàng"),
    COMPLETED("Hoàn thành"),
    REJECTED("Bị từ chối"),
    CANCELLED("Đã hủy");

    private final String description;

    /**
     * Check if transitioning from current status to target status is valid in general.
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING_CONFIRMATION -> target == PAID || target == CONFIRMED || target == REJECTED || target == CANCELLED;
            case PAID -> target == CONFIRMED || target == REJECTED || target == CANCELLED;
            case CONFIRMED -> target == PREPARING;
            case PREPARING -> target == SHIPPING;
            case SHIPPING -> target == COMPLETED;
            case COMPLETED, REJECTED, CANCELLED -> false;
        };
    }

    /**
     * Check if transitioning from current status to target status is valid considering payment method.
     * Online Payment: PENDING_CONFIRMATION -> PAID -> CONFIRMED -> PREPARING -> SHIPPING -> COMPLETED
     * COD: PENDING_CONFIRMATION -> CONFIRMED -> PREPARING -> SHIPPING -> COMPLETED
     */
    public boolean canTransitionTo(OrderStatus target, PaymentMethod paymentMethod) {
        if (!canTransitionTo(target)) {
            return false;
        }
        if (paymentMethod == PaymentMethod.COD) {
            // COD cannot transition to PAID
            if (this == PENDING_CONFIRMATION && target == PAID) {
                return false;
            }
        } else if (paymentMethod != null) {
            // Online payments must be PAID before CONFIRMED (Supplier cannot confirm unpaid online order)
            if (this == PENDING_CONFIRMATION && target == CONFIRMED) {
                return false;
            }
        }
        return true;
    }
}

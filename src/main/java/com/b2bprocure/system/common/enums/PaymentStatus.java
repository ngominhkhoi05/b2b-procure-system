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
}

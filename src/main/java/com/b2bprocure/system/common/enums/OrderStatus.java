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
}

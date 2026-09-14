package com.b2bprocure.system.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {

    ACTIVE("Đang kinh doanh"),
    INACTIVE("Ngừng kinh doanh"),
    OUT_OF_STOCK("Hết hàng"),
    ARCHIVED("Đã lưu trữ");

    private final String description;
}

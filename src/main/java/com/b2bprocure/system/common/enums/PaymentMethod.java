package com.b2bprocure.system.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {

    ZALOPAY("ZaloPay"),
    MOMO("MoMo"),
    COD("Thanh toán khi nhận hàng");

    private final String description;
}

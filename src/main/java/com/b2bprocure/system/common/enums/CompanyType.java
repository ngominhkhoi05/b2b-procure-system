package com.b2bprocure.system.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CompanyType {

    BUYER("Doanh nghiệp mua hàng"),
    SUPPLIER("Nhà cung cấp");
    private final String description;
}

package com.b2bprocure.system.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSearchRequest {

    private String keyword;
    private Long categoryId;
    private Long supplierCompanyId;
    private String status;
    private Integer page;
    private Integer size;

}

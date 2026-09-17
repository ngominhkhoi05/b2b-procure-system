package com.b2bprocure.system.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private Long supplierCompanyId;
    private String supplierCompanyName;
    private String sku;
    private String name;
    private String description;
    private String imageUrl;
    private Integer stockQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private String status;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

package com.b2bprocure.system.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private String orderCode;
    private Long buyerCompanyId;
    private Long supplierCompanyId;
    private Long createdBy;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal totalAmount;
    private String shippingCompanyName;
    private String shippingPhone;
    private String shippingAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

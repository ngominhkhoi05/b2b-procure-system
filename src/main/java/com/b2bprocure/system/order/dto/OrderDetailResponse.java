package com.b2bprocure.system.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailResponse {

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
    private List<OrderItemResponse> items;
    private List<OrderStatusHistoryResponse> statusHistory;

    /**
     * Payment summary for this order. Populated by service from
     * {@code PaymentRepository.findByOrderId(...)}.
     */
    private PaymentSummaryResponse payment;

    /**
     * Buyer company display name (snapshot from {@code Order.buyerCompany}).
     * Set explicitly in service to avoid touching lazy associations inside mapper.
     */
    private String buyerCompanyName;

    /**
     * Supplier company display name (snapshot from {@code Order.supplierCompany}).
     * Set explicitly in service to avoid touching lazy associations inside mapper.
     */
    private String supplierCompanyName;

}

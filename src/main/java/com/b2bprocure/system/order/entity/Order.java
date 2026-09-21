package com.b2bprocure.system.order.entity;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_company_id", nullable = false)
    private Company buyerCompany;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_company_id", nullable = false)
    private Company supplierCompany;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "subtotal", precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", precision = 15, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "shipping_company_name")
    private String shippingCompanyName;

    @Column(name = "shipping_phone")
    private String shippingPhone;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}

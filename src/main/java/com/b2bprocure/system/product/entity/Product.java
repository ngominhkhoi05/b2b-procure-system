package com.b2bprocure.system.product.entity;

import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.company.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_company_id", nullable = false)
    private Company supplierCompany;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_public_id", length = 255)
    private String imagePublicId;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @Column(name = "status")
    private String status;

    /**
     * Denormalized visibility flag: true when status='ACTIVE' AND at least
     * one product_prices row exists. Maintained by DB triggers
     * (V20__add_is_listable_to_products.sql). Read-only from JPA's
     * perspective — Hibernate's ddl-auto=validate only checks the column
     * exists; we never write into this field from application code because
     * the DB trigger always overrides it on INSERT/UPDATE.
     *
     * Used as the predicate for the partial composite index
     * {@code idx_products_listable_browse} to make BUYER browse fast on
     * 1M+ rows.
     */
    @Column(name = "is_listable", nullable = false)
    private Boolean isListable = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Integer getAvailableQuantity() {
        int stock = stockQuantity != null ? stockQuantity : 0;
        int reserved = reservedQuantity != null ? reservedQuantity : 0;
        return Math.max(0, stock - reserved);
    }

    public void validateInvariants() {
        int stock = stockQuantity != null ? stockQuantity : 0;
        int reserved = reservedQuantity != null ? reservedQuantity : 0;
        if (stock < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
        if (reserved < 0) {
            throw new IllegalArgumentException("Reserved quantity cannot be negative");
        }
        if (reserved > stock) {
            throw new IllegalArgumentException(
                    String.format("Reserved quantity (%d) cannot exceed stock quantity (%d)", reserved, stock)
            );
        }
    }

    @PrePersist
    @PreUpdate
    protected void onPersistOrUpdate() {
        if (stockQuantity == null) {
            stockQuantity = 0;
        }
        if (reservedQuantity == null) {
            reservedQuantity = 0;
        }
        validateInvariants();
    }

}


package com.b2bprocure.system.product.repository;

import com.b2bprocure.system.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    @Query("SELECT p FROM Product p JOIN FETCH p.supplierCompany JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    @Query(value = "SELECT p FROM Product p " +
            "JOIN FETCH p.supplierCompany " +
            "JOIN FETCH p.category " +
            "WHERE (:supplierCompanyId IS NULL OR p.supplierCompany.id = :supplierCompanyId) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (cast(:status as string) IS NULL OR UPPER(p.status) = :status) " +
            "AND (cast(:categoryStatus as string) IS NULL OR UPPER(p.category.status) = :categoryStatus) " +
            "AND (cast(:pattern as string) IS NULL OR LOWER(p.name) LIKE :pattern OR LOWER(p.sku) LIKE :pattern OR LOWER(p.description) LIKE :pattern)",
           countQuery = "SELECT count(p) FROM Product p " +
            "WHERE (:supplierCompanyId IS NULL OR p.supplierCompany.id = :supplierCompanyId) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (cast(:status as string) IS NULL OR UPPER(p.status) = :status) " +
            "AND (cast(:categoryStatus as string) IS NULL OR UPPER(p.category.status) = :categoryStatus) " +
            "AND (cast(:pattern as string) IS NULL OR LOWER(p.name) LIKE :pattern OR LOWER(p.sku) LIKE :pattern OR LOWER(p.description) LIKE :pattern)")
    Page<Product> searchProducts(
            @Param("supplierCompanyId") Long supplierCompanyId,
            @Param("categoryId") Long categoryId,
            @Param("status") String status,
            @Param("categoryStatus") String categoryStatus,
            @Param("pattern") String pattern,
            Pageable pageable
    );

}

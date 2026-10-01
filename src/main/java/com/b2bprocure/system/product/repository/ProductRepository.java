package com.b2bprocure.system.product.repository;

import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    @Query("SELECT p FROM Product p JOIN FETCH p.supplierCompany JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Product> findByIdInWithLock(@Param("ids") List<Long> ids);

    @Query(value = "SELECT p FROM Product p " +
            "JOIN FETCH p.supplierCompany " +
            "JOIN FETCH p.category " +
            "WHERE (:supplierCompanyId IS NULL OR p.supplierCompany.id = :supplierCompanyId) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (cast(:status as string) IS NULL OR UPPER(p.status) = :status) " +
            "AND (cast(:categoryStatus as string) IS NULL OR UPPER(p.category.status) = :categoryStatus) " +
            "AND (cast(:pattern as string) IS NULL OR LOWER(p.name) LIKE :pattern OR LOWER(p.sku) LIKE :pattern OR LOWER(p.description) LIKE :pattern) " +
            "AND (:requireHasPrices = false OR EXISTS (SELECT 1 FROM ProductPrice pp WHERE pp.product.id = p.id))",
           countQuery = "SELECT count(p) FROM Product p " +
            "WHERE (:supplierCompanyId IS NULL OR p.supplierCompany.id = :supplierCompanyId) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (cast(:status as string) IS NULL OR UPPER(p.status) = :status) " +
            "AND (cast(:categoryStatus as string) IS NULL OR UPPER(p.category.status) = :categoryStatus) " +
            "AND (cast(:pattern as string) IS NULL OR LOWER(p.name) LIKE :pattern OR LOWER(p.sku) LIKE :pattern OR LOWER(p.description) LIKE :pattern) " +
            "AND (:requireHasPrices = false OR EXISTS (SELECT 1 FROM ProductPrice pp WHERE pp.product.id = p.id))")
    Page<Product> searchProducts(
            @Param("supplierCompanyId") Long supplierCompanyId,
            @Param("categoryId") Long categoryId,
            @Param("status") String status,
            @Param("categoryStatus") String categoryStatus,
            @Param("pattern") String pattern,
            @Param("requireHasPrices") boolean requireHasPrices,
            Pageable pageable
    );

    // ========================================================================
    // V16 Full-Text Search (Postgres FTS with GIN index on search_vector)
    // ========================================================================

    /**
     * FTS search returning product ids ordered by ts_rank DESC.
     *
     * <p>Why two-step (ids -> batch fetch)?
     * Native queries with ORDER BY ts_rank + LIMIT cannot easily combine with
     * JPA's JOIN FETCH without exploding the result set (a product that ranks
     * twice gets returned twice). Instead we page over ids, then a JPQL query
     * batches the Product + 2 relationships into 1 round-trip.
     *
     * <p>Why a count query as well?
     * Spring Data {@code Page<Long>} requires total elements for the page
     * metadata. We repeat the WHERE clause (without ORDER BY) for the count.
     * For very large result sets this count can be slow; if it ever becomes a
     * problem, replace {@code Page<Long>} with {@code Slice<Long>} in the
     * service layer.
     *
     * <p>Why {@code websearch_to_tsquery} instead of {@code to_tsquery}?
     * websearch_to_tsquery uses Google-style syntax ("phrase", -exclude, or),
     * automatically escapes user input, and tolerates malformed queries by
     * returning an empty tsquery rather than raising. Safer default for a
     * user-facing search field.
     */
    @Query(value = """
            SELECT p.id FROM products p
            JOIN categories c ON c.id = p.category_id
            WHERE (:supplierCompanyId IS NULL OR p.supplier_company_id = :supplierCompanyId)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (cast(:status as text) IS NULL OR upper(p.status) = :status)
              AND (cast(:categoryStatus as text) IS NULL OR upper(c.status) = :categoryStatus)
              AND (cast(:keyword as text) IS NULL
                   OR p.search_vector @@ websearch_to_tsquery('vn_simple', :keyword))
              AND (:requireHasPrices = false OR EXISTS (
                    SELECT 1 FROM product_prices pp WHERE pp.product_id = p.id))
            ORDER BY ts_rank(p.search_vector, websearch_to_tsquery('vn_simple', :keyword)) DESC, p.id ASC
            """,
           countQuery = """
            SELECT count(p.id) FROM products p
            JOIN categories c ON c.id = p.category_id
            WHERE (:supplierCompanyId IS NULL OR p.supplier_company_id = :supplierCompanyId)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (cast(:status as text) IS NULL OR upper(p.status) = :status)
              AND (cast(:categoryStatus as text) IS NULL OR upper(c.status) = :categoryStatus)
              AND (cast(:keyword as text) IS NULL
                   OR p.search_vector @@ websearch_to_tsquery('vn_simple', :keyword))
              AND (:requireHasPrices = false OR EXISTS (
                    SELECT 1 FROM product_prices pp WHERE pp.product_id = p.id))
            """,
           nativeQuery = true)
    Page<Long> searchProductIdsByFts(
            @Param("supplierCompanyId") Long supplierCompanyId,
            @Param("categoryId") Long categoryId,
            @Param("status") String status,
            @Param("categoryStatus") String categoryStatus,
            @Param("keyword") String keyword,
            @Param("requireHasPrices") boolean requireHasPrices,
            Pageable pageable
    );

    /**
     * Batch fetch Product + supplierCompany + category in a single SQL query
     * for the given list of ids. Used by the FTS path after
     * {@link #searchProductIdsByFts} returns the page of ids.
     *
     * <p>Why IN ({@code}) instead of the inherited findAllById?
     * findAllById does NOT support JOIN FETCH. Without JOIN FETCH we get
     * N+1 (one query per Product for each of the 2 lazy relationships).
     * With JOIN FETCH we issue exactly 1 query that returns Products already
     * hydrated, no matter the page size.
     */
    @Query("SELECT p FROM Product p " +
            "JOIN FETCH p.supplierCompany " +
            "JOIN FETCH p.category " +
            "WHERE p.id IN :ids")
    List<Product> findAllByIdInWithDetails(@Param("ids") Collection<Long> ids);

    /**
     * Step 8 — Admin Company Detail: count products belonging to a supplier company.
     */
    long countBySupplierCompanyId(Long supplierCompanyId);

    /**
     * Step 8 — Admin Category Delete: check if any product references this category.
     */
    boolean existsByCategoryId(Long categoryId);
}

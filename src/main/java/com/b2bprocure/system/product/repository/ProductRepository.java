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
            "JOIN FETCH p.supplierCompany sc " +
            "JOIN FETCH p.category " +
            "WHERE (:supplierCompanyId IS NULL OR sc.id = :supplierCompanyId) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (cast(:status as string) IS NULL OR UPPER(p.status) = :status) " +
            "AND (cast(:categoryStatus as string) IS NULL OR UPPER(p.category.status) = :categoryStatus) " +
            "AND (:listableOnly = false OR p.isListable = true) " +
            "AND (:supplierCompanyMustBeActive = false OR UPPER(sc.status) = 'ACTIVE') " +
            "AND (cast(:pattern as string) IS NULL OR LOWER(p.name) LIKE :pattern OR LOWER(p.sku) LIKE :pattern OR LOWER(p.description) LIKE :pattern)",
           countQuery = "SELECT count(*) FROM Product p " +
            "JOIN p.supplierCompany sc " +
            "WHERE (:supplierCompanyId IS NULL OR sc.id = :supplierCompanyId) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (cast(:status as string) IS NULL OR UPPER(p.status) = :status) " +
            "AND (cast(:categoryStatus as string) IS NULL OR UPPER(p.category.status) = :categoryStatus) " +
            "AND (:listableOnly = false OR p.isListable = true) " +
            "AND (:supplierCompanyMustBeActive = false OR UPPER(sc.status) = 'ACTIVE') " +
            "AND (cast(:pattern as string) IS NULL OR LOWER(p.name) LIKE :pattern OR LOWER(p.sku) LIKE :pattern OR LOWER(p.description) LIKE :pattern)")
    Page<Product> searchProducts(
            @Param("supplierCompanyId") Long supplierCompanyId,
            @Param("categoryId") Long categoryId,
            @Param("status") String status,
            @Param("categoryStatus") String categoryStatus,
            @Param("pattern") String pattern,
            @Param("listableOnly") boolean listableOnly,
            @Param("supplierCompanyMustBeActive") boolean supplierCompanyMustBeActive,
            Pageable pageable
    );

    // ========================================================================
    // Slice (no count query) variants for BUYER browse - Tier 2.
    //
    // Why fetch pageSize + 1 rows?
    //   Spring Data's Slice<T> requires `hasNext` which needs to know whether
    //   there is at least one more row after the current page. The cheapest
    //   way is to ask the DB for pageSize + 1 rows and check whether we got
    //   exactly pageSize + 1 back. If yes, trim to pageSize and set hasNext=true.
    //   If we got fewer (or equal to pageSize), hasNext=false.
    //
    //   This avoids the count(p) query, which on 1M rows took 3-8s even with
    //   a partial index, while keeping the same UX semantics.
    // ========================================================================

    /**
     * Slice variant of searchProducts for BUYER (isListable=true enforced).
     * Returns up to pageSize + 1 Products; service trims and computes hasNext.
     */
    @Query(value = "SELECT p FROM Product p " +
            "JOIN FETCH p.supplierCompany sc " +
            "JOIN FETCH p.category " +
            "WHERE p.isListable = true " +
            "AND (:supplierCompanyId IS NULL OR sc.id = :supplierCompanyId) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:supplierCompanyMustBeActive = false OR UPPER(sc.status) = 'ACTIVE') " +
            "AND (cast(:pattern as string) IS NULL OR LOWER(p.name) LIKE :pattern OR LOWER(p.sku) LIKE :pattern OR LOWER(p.description) LIKE :pattern)",
           countQuery = "SELECT 1")
    List<Product> searchProductSlice(
            @Param("supplierCompanyId") Long supplierCompanyId,
            @Param("categoryId") Long categoryId,
            @Param("pattern") String pattern,
            @Param("supplierCompanyMustBeActive") boolean supplierCompanyMustBeActive,
            Pageable pageable
    );

    /**
     * Slice variant of FTS path for BUYER. Returns up to pageSize + 1 ids;
     * service trims and computes hasNext.
     *
     * The countQuery must be present for Spring Data even though we ignore
     * the value; SELECT 1 makes it a constant-time no-op.
     */
    @Query(value = """
            SELECT p.id FROM products p
            JOIN categories c ON c.id = p.category_id
            JOIN companies sc ON sc.id = p.supplier_company_id
            WHERE p.is_listable = true
              AND (:supplierCompanyId IS NULL OR p.supplier_company_id = :supplierCompanyId)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:supplierCompanyMustBeActive = false OR upper(sc.status) = 'ACTIVE')
              AND (cast(:keyword as text) IS NULL
                   OR p.search_vector @@ websearch_to_tsquery('vn_simple', :keyword))
            ORDER BY ts_rank(p.search_vector, websearch_to_tsquery('vn_simple', :keyword)) DESC, p.id ASC
            """,
           countQuery = "SELECT 1",
           nativeQuery = true)
    List<Long> searchProductIdsSliceByFts(
            @Param("supplierCompanyId") Long supplierCompanyId,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("supplierCompanyMustBeActive") boolean supplierCompanyMustBeActive,
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
            JOIN companies sc ON sc.id = p.supplier_company_id
            WHERE (:supplierCompanyId IS NULL OR p.supplier_company_id = :supplierCompanyId)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (cast(:status as text) IS NULL OR upper(p.status) = :status)
              AND (cast(:categoryStatus as text) IS NULL OR upper(c.status) = :categoryStatus)
              AND (:supplierCompanyMustBeActive = false OR upper(sc.status) = 'ACTIVE')
              AND (cast(:keyword as text) IS NULL
                   OR p.search_vector @@ websearch_to_tsquery('vn_simple', :keyword))
              AND (:requireHasPrices = false OR p.is_listable = true)
            ORDER BY ts_rank(p.search_vector, websearch_to_tsquery('vn_simple', :keyword)) DESC, p.id ASC
            """,
           countQuery = """
            SELECT count(*) FROM products p
            JOIN categories c ON c.id = p.category_id
            JOIN companies sc ON sc.id = p.supplier_company_id
            WHERE (:supplierCompanyId IS NULL OR p.supplier_company_id = :supplierCompanyId)
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (cast(:status as text) IS NULL OR upper(p.status) = :status)
              AND (cast(:categoryStatus as text) IS NULL OR upper(c.status) = :categoryStatus)
              AND (:supplierCompanyMustBeActive = false OR upper(sc.status) = 'ACTIVE')
              AND (cast(:keyword as text) IS NULL
                   OR p.search_vector @@ websearch_to_tsquery('vn_simple', :keyword))
              AND (:requireHasPrices = false OR p.is_listable = true)
            """,
           nativeQuery = true)
    Page<Long> searchProductIdsByFts(
            @Param("supplierCompanyId") Long supplierCompanyId,
            @Param("categoryId") Long categoryId,
            @Param("status") String status,
            @Param("categoryStatus") String categoryStatus,
            @Param("keyword") String keyword,
            @Param("requireHasPrices") boolean requireHasPrices,
            @Param("supplierCompanyMustBeActive") boolean supplierCompanyMustBeActive,
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

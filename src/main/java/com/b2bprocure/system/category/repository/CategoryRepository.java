package com.b2bprocure.system.category.repository;

import com.b2bprocure.system.category.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Page<Category> findByStatus(String status, Pageable pageable);

    Optional<Category> findByIdAndStatus(Long id, String status);

    @Query("SELECT c FROM Category c WHERE " +
           "(cast(:status as string) IS NULL OR c.status = :status) AND " +
           "(cast(:pattern as string) IS NULL OR LOWER(c.name) LIKE :pattern OR LOWER(c.description) LIKE :pattern)")
    Page<Category> searchCategories(
            @Param("status") String status,
            @Param("pattern") String pattern,
            Pageable pageable
    );

}

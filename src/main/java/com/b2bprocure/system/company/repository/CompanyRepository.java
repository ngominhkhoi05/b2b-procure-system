package com.b2bprocure.system.company.repository;

import com.b2bprocure.system.company.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByTaxCode(String taxCode);

    boolean existsByTaxCodeAndIdNot(String taxCode, Long id);

    Optional<Company> findByTaxCode(String taxCode);

    @Query("SELECT c FROM Company c WHERE " +
            "(cast(:companyType as string) IS NULL OR c.companyType = :companyType) AND " +
            "(cast(:status as string) IS NULL OR c.status = :status) AND " +
            "(cast(:pattern as string) IS NULL OR LOWER(c.name) LIKE :pattern OR LOWER(c.taxCode) LIKE :pattern)")
    Page<Company> searchCompanies(
            @Param("companyType") String companyType,
            @Param("status") String status,
            @Param("pattern") String pattern,
            Pageable pageable
    );

}

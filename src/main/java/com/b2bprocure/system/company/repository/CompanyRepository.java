package com.b2bprocure.system.company.repository;

import com.b2bprocure.system.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByTaxCode(String taxCode);

    Optional<Company> findByTaxCode(String taxCode);

}

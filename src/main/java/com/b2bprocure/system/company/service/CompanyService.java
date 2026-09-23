package com.b2bprocure.system.company.service;

import com.b2bprocure.system.admin.dto.AdminCompanyResponse;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.company.dto.CompanyResponse;
import com.b2bprocure.system.company.dto.CompanyStatusUpdateRequest;
import com.b2bprocure.system.company.dto.CreateCompanyRequest;
import com.b2bprocure.system.company.dto.UpdateCompanyRequest;
import com.b2bprocure.system.company.entity.Company;
import org.springframework.data.domain.Pageable;

public interface CompanyService {

    CompanyResponse getCurrentCompany();

    CompanyResponse getCompanyById(Long id);

    PageResponse<CompanyResponse> getCompanies(String companyType, String status, String keyword, Pageable pageable);

    /**
     * Step 8 — Admin Company Detail: returns extended response with user and product counts.
     */
    AdminCompanyResponse getCompanyByIdForAdmin(Long id);

    CompanyResponse updateCurrentCompany(UpdateCompanyRequest request);

    CompanyResponse updateCompanyByAdmin(Long id, UpdateCompanyRequest request);

    CompanyResponse updateCompanyStatus(Long id, CompanyStatusUpdateRequest request);

    Company createCompany(CreateCompanyRequest request, String companyType);
}

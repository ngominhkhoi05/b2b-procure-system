package com.b2bprocure.system.company.service;

import com.b2bprocure.system.admin.dto.AdminCompanyResponse;
import com.b2bprocure.system.common.constant.SecurityConstants;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.common.util.SecurityUtil;
import com.b2bprocure.system.company.dto.CompanyResponse;
import com.b2bprocure.system.company.dto.CompanyStatusUpdateRequest;
import com.b2bprocure.system.company.dto.CreateCompanyRequest;
import com.b2bprocure.system.company.dto.UpdateCompanyRequest;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.mapper.CompanyMapper;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CompanyMapper companyMapper;

    private User getCurrentAuthenticatedUser() {
        Optional<Long> userIdOpt = SecurityUtil.getCurrentUserId();
        if (userIdOpt.isPresent()) {
            return userRepository.findByIdWithRoleAndCompany(userIdOpt.get())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userIdOpt.get()));
        }
        String username = SecurityUtil.getCurrentUsernameOrThrow();
        return userRepository.findByUsernameWithRoleAndCompany(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getCurrentCompany() {
        User currentUser = getCurrentAuthenticatedUser();
        if (currentUser.getCompany() == null) {
            throw new BusinessException("Current user does not belong to any company", HttpStatus.BAD_REQUEST);
        }
        return companyMapper.toResponse(currentUser.getCompany());
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(Long id) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName);

        if (!isAdmin) {
            if (currentUser.getCompany() == null || !currentUser.getCompany().getId().equals(id)) {
                throw new AccessDeniedException("Access denied: You do not have permission to access this company");
            }
        }

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));

        return companyMapper.toResponse(company);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCompanyResponse getCompanyByIdForAdmin(Long id) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        if (!SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Access denied: Only administrators can access this endpoint");
        }

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));

        CompanyResponse base = companyMapper.toResponse(company);
        long userCount = userRepository.countByCompanyId(id);
        long productCount = productRepository.countBySupplierCompanyId(id);

        return AdminCompanyResponse.builder()
                .id(base.getId())
                .name(base.getName())
                .taxCode(base.getTaxCode())
                .email(base.getEmail())
                .phone(base.getPhone())
                .address(base.getAddress())
                .companyType(base.getCompanyType())
                .status(base.getStatus())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .userCount(userCount)
                .productCount(productCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> getCompanies(String companyType, String status, String keyword, Pageable pageable) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        if (!SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Access denied: Only administrators can list companies");
        }

        String normalizedType = (companyType != null && !companyType.isBlank()) ? companyType.trim().toUpperCase() : null;
        String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String pattern = (keyword != null && !keyword.isBlank()) ? "%" + keyword.trim().toLowerCase() + "%" : null;

        Page<Company> companyPage;
        if (normalizedType == null && normalizedStatus == null && pattern == null) {
            companyPage = companyRepository.findAll(pageable);
        } else {
            companyPage = companyRepository.searchCompanies(normalizedType, normalizedStatus, pattern, pageable);
        }

        List<CompanyResponse> content = companyPage.getContent().stream()
                .map(companyMapper::toResponse)
                .toList();

        return PageResponse.of(companyPage, content);
    }

    @Override
    @Transactional
    public CompanyResponse updateCurrentCompany(UpdateCompanyRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        if (currentUser.getCompany() == null) {
            throw new BusinessException("Current user does not belong to any company", HttpStatus.BAD_REQUEST);
        }

        Long companyId = currentUser.getCompany().getId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));

        if (request.getTaxCode() != null) {
            String trimmedTaxCode = request.getTaxCode().trim();
            if (trimmedTaxCode.isEmpty()) {
                throw new BusinessException("Tax code cannot be blank", HttpStatus.BAD_REQUEST);
            }
            if (!trimmedTaxCode.equalsIgnoreCase(company.getTaxCode())) {
                if (companyRepository.existsByTaxCodeAndIdNot(trimmedTaxCode, company.getId())) {
                    throw new BusinessException("Tax code already exists: " + trimmedTaxCode, HttpStatus.CONFLICT);
                }
                company.setTaxCode(trimmedTaxCode);
            }
        }

        companyMapper.updateEntity(request, company);
        company.setUpdatedAt(LocalDateTime.now());

        Company savedCompany = companyRepository.save(company);
        return companyMapper.toResponse(savedCompany);
    }

    @Override
    @Transactional
    public CompanyResponse updateCompanyByAdmin(Long id, UpdateCompanyRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        if (!SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Access denied: Only administrators can update companies via this endpoint");
        }

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));

        if (request.getTaxCode() != null) {
            String trimmedTaxCode = request.getTaxCode().trim();
            if (trimmedTaxCode.isEmpty()) {
                throw new BusinessException("Tax code cannot be blank", HttpStatus.BAD_REQUEST);
            }
            if (!trimmedTaxCode.equalsIgnoreCase(company.getTaxCode())) {
                if (companyRepository.existsByTaxCodeAndIdNot(trimmedTaxCode, company.getId())) {
                    throw new BusinessException("Tax code already exists: " + trimmedTaxCode, HttpStatus.CONFLICT);
                }
                company.setTaxCode(trimmedTaxCode);
            }
        }

        companyMapper.updateEntity(request, company);
        company.setUpdatedAt(LocalDateTime.now());

        Company savedCompany = companyRepository.save(company);
        return companyMapper.toResponse(savedCompany);
    }

    @Override
    @Transactional
    public CompanyResponse updateCompanyStatus(Long id, CompanyStatusUpdateRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        if (!SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Access denied: Only administrators can update company status");
        }

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));

        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new BusinessException("Status is required", HttpStatus.BAD_REQUEST);
        }

        String normalizedStatus = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus) && !"BLOCKED".equals(normalizedStatus)) {
            throw new BusinessException("Invalid company status: " + request.getStatus() + ". Allowed statuses: ACTIVE, INACTIVE, BLOCKED", HttpStatus.BAD_REQUEST);
        }

        company.setStatus(normalizedStatus);
        company.setUpdatedAt(LocalDateTime.now());

        Company savedCompany = companyRepository.save(company);
        return companyMapper.toResponse(savedCompany);
    }

    @Override
    @Transactional
    public Company createCompany(CreateCompanyRequest request, String companyType) {
        if (companyType == null || companyType.isBlank()) {
            throw new BusinessException("Company type is required", HttpStatus.BAD_REQUEST);
        }
        String normalizedCompanyType = companyType.trim().toUpperCase();
        if (!"BUYER".equals(normalizedCompanyType) && !"SUPPLIER".equals(normalizedCompanyType)) {
            throw new BusinessException("Invalid company type: " + companyType + ". Allowed types: BUYER, SUPPLIER", HttpStatus.BAD_REQUEST);
        }

        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("Company name is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getTaxCode() == null || request.getTaxCode().isBlank()) {
            throw new BusinessException("Tax code is required", HttpStatus.BAD_REQUEST);
        }
        if (companyRepository.existsByTaxCode(request.getTaxCode().trim())) {
            throw new BusinessException("Tax code already exists: " + request.getTaxCode(), HttpStatus.CONFLICT);
        }

        Company company = new Company();
        company.setName(request.getName().trim());
        company.setTaxCode(request.getTaxCode().trim());
        company.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        company.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        company.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        company.setCompanyType(normalizedCompanyType);
        company.setStatus("ACTIVE");
        company.setCreatedAt(LocalDateTime.now());
        company.setUpdatedAt(LocalDateTime.now());

        return companyRepository.save(company);
    }

}

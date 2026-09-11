package com.b2bprocure.system.auth.service;

import com.b2bprocure.system.auth.dto.LoginRequest;
import com.b2bprocure.system.auth.dto.LoginResponse;
import com.b2bprocure.system.auth.dto.OAuth2RegisterRequest;
import com.b2bprocure.system.authaccount.entity.AuthAccount;
import com.b2bprocure.system.authaccount.repository.AuthAccountRepository;
import com.b2bprocure.system.common.enums.AuthProvider;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.common.exception.ResourceNotFoundException;
import com.b2bprocure.system.company.dto.CreateCompanyRequest;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.config.JwtConfig;
import com.b2bprocure.system.role.entity.Role;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final CompanyRepository companyRepository;
    private final EntityManager entityManager;

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Attempting authentication for user: {}", request.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.generateToken(userPrincipal);

        log.info("User {} successfully authenticated with role {}", userPrincipal.getUsername(), userPrincipal.getRole());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .userId(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .role(userPrincipal.getRole())
                .avatarUrl(userPrincipal.getAvatarUrl())
                .expiresIn(jwtConfig.getExpirationMs())
                .build();
    }

    @Override
    @Transactional
    public LoginResponse registerOAuth2(OAuth2RegisterRequest request, String registrationToken) {
        log.info("Processing OAuth2 first-time registration for companyType: {}", request.getCompanyType());

        // 1. Validate registration token and extract claims
        Claims claims = jwtTokenProvider.validateAndGetRegistrationClaims(registrationToken);
        String providerStr = claims.get(JwtTokenProvider.CLAIM_PROVIDER, String.class);
        String providerUserId = claims.get(JwtTokenProvider.CLAIM_PROVIDER_USER_ID, String.class);
        String email = claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class);
        String name = claims.get(JwtTokenProvider.CLAIM_NAME, String.class);

        // 2. Validate provider is GOOGLE
        if (providerStr == null || !AuthProvider.GOOGLE.name().equalsIgnoreCase(providerStr)) {
            throw new BusinessException("Unsupported OAuth2 provider: " + providerStr, HttpStatus.BAD_REQUEST);
        }
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new BusinessException("Missing providerUserId in registration token", HttpStatus.BAD_REQUEST);
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException("Missing email in registration token", HttpStatus.BAD_REQUEST);
        }

        // 3. Check if account already exists
        if (authAccountRepository.existsByProviderAndProviderUserId(AuthProvider.GOOGLE, providerUserId)) {
            throw new BusinessException("Google account is already registered", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email is already registered: " + email, HttpStatus.CONFLICT);
        }

        // 4. Validate companyType (Allowed: BUYER, SUPPLIER; NOT: ADMIN)
        if (request.getCompanyType() == null || request.getCompanyType().isBlank()) {
            throw new BusinessException("Company type is required", HttpStatus.BAD_REQUEST);
        }
        String normalizedCompanyType = request.getCompanyType().trim().toUpperCase();
        if (!"BUYER".equals(normalizedCompanyType) && !"SUPPLIER".equals(normalizedCompanyType)) {
            throw new BusinessException("Invalid company type: " + request.getCompanyType() + ". Allowed types: BUYER, SUPPLIER", HttpStatus.BAD_REQUEST);
        }

        // 5. Handle company (either companyId or company details)
        if (request.getCompanyId() == null && request.getCompany() == null) {
            throw new BusinessException("Either companyId or company details must be provided", HttpStatus.BAD_REQUEST);
        }
        if (request.getCompanyId() != null && request.getCompany() != null) {
            throw new BusinessException("Provide either companyId or company details, not both", HttpStatus.BAD_REQUEST);
        }

        Company company;
        if (request.getCompanyId() != null) {
            company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Company", "id", request.getCompanyId()));

            if (company.getStatus() == null || !"ACTIVE".equalsIgnoreCase(company.getStatus())) {
                throw new BusinessException("Company is inactive", HttpStatus.BAD_REQUEST);
            }

            if (company.getCompanyType() == null || !company.getCompanyType().equalsIgnoreCase(normalizedCompanyType)) {
                throw new BusinessException(String.format("Company type mismatch: selected company is %s but requested type is %s",
                        company.getCompanyType(), normalizedCompanyType), HttpStatus.BAD_REQUEST);
            }
        } else {
            CreateCompanyRequest companyDto = request.getCompany();
            if (companyDto.getName() == null || companyDto.getName().isBlank()) {
                throw new BusinessException("Company name is required", HttpStatus.BAD_REQUEST);
            }
            if (companyDto.getTaxCode() == null || companyDto.getTaxCode().isBlank()) {
                throw new BusinessException("Tax code is required", HttpStatus.BAD_REQUEST);
            }
            if (companyRepository.existsByTaxCode(companyDto.getTaxCode())) {
                throw new BusinessException("Tax code already exists: " + companyDto.getTaxCode(), HttpStatus.CONFLICT);
            }

            company = new Company();
            company.setName(companyDto.getName().trim());
            company.setTaxCode(companyDto.getTaxCode().trim());
            company.setEmail(companyDto.getEmail());
            company.setPhone(companyDto.getPhone());
            company.setAddress(companyDto.getAddress());
            company.setCompanyType(normalizedCompanyType);
            company.setStatus("ACTIVE");
            company.setCreatedAt(LocalDateTime.now());
            company.setUpdatedAt(LocalDateTime.now());
            company = companyRepository.save(company);
        }

        // 6. Look up Role (Rule 6 compliant: using EntityManager to query master data without RoleRepository)
        Role role = findRoleByName(normalizedCompanyType);

        // 7. Generate clean and unique username based on Google email prefix
        String baseUsername = email.contains("@")
                ? email.substring(0, email.indexOf('@')).toLowerCase().replaceAll("[^a-zA-Z0-9._-]", "")
                : email.toLowerCase();
        if (baseUsername.isBlank()) {
            baseUsername = "google_user";
        }

        String uniqueUsername = baseUsername;
        int attempts = 0;
        while (userRepository.existsByUsername(uniqueUsername)) {
            attempts++;
            uniqueUsername = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 6);
            if (attempts > 10) {
                throw new BusinessException("Unable to generate unique username", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        // 8. Create User (password = NULL for Google-only user)
        User user = new User();
        user.setRole(role);
        user.setCompany(company);
        user.setUsername(uniqueUsername);
        user.setPassword(null);
        user.setFullName(name != null && !name.isBlank() ? name : uniqueUsername);
        user.setEmail(email);
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        // 9. Create AuthAccount
        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        authAccountRepository.save(authAccount);

        log.info("Successfully registered Google user id: {}, username: {}, role: {}",
                user.getId(), user.getUsername(), role.getName());

        // 10. Generate application JWT and return LoginResponse
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String accessToken = jwtTokenProvider.generateToken(userPrincipal);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .role(userPrincipal.getRole())
                .avatarUrl(user.getAvatarUrl())
                .expiresIn(jwtConfig.getExpirationMs())
                .build();
    }

    private Role findRoleByName(String roleName) {
        List<Role> roles = entityManager.createQuery(
                "SELECT r FROM Role r WHERE r.name = :name", Role.class)
                .setParameter("name", roleName)
                .getResultList();
        if (roles.isEmpty()) {
            throw new ResourceNotFoundException("Role", "name", roleName);
        }
        return roles.get(0);
    }

}

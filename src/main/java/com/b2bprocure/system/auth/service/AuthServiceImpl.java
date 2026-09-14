package com.b2bprocure.system.auth.service;

import com.b2bprocure.system.auth.dto.LoginRequest;
import com.b2bprocure.system.auth.dto.LoginResponse;
import com.b2bprocure.system.auth.dto.OAuth2LinkRequest;
import com.b2bprocure.system.auth.dto.OAuth2RegisterRequest;
import com.b2bprocure.system.auth.dto.RegisterRequest;
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
import com.b2bprocure.system.security.OAuth2LinkStateStore;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final CompanyRepository companyRepository;
    private final EntityManager entityManager;
    private final OAuth2LinkStateStore oauth2LinkStateStore;

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        log.info("Processing traditional user registration for username: {}", request.getUsername());

        // 1. Check if username is already taken
        if (userRepository.existsByUsername(request.getUsername().trim())) {
            throw new BusinessException("Username is already taken: " + request.getUsername(), HttpStatus.CONFLICT);
        }

        // 2. Check if email is already registered
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new BusinessException("Email is already registered: " + request.getEmail(), HttpStatus.CONFLICT);
        }

        // 3. Validate company configuration (either companyId or company details must be provided)
        if (request.getCompanyId() == null && request.getCompany() == null) {
            throw new BusinessException("Either companyId or company details must be provided", HttpStatus.BAD_REQUEST);
        }
        if (request.getCompanyId() != null && request.getCompany() != null) {
            throw new BusinessException("Provide either companyId or company details, not both", HttpStatus.BAD_REQUEST);
        }

        Company company;
        String roleName;

        if (request.getCompanyId() != null) {
            company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Company", "id", request.getCompanyId()));

            if (company.getStatus() == null || !"ACTIVE".equalsIgnoreCase(company.getStatus())) {
                throw new BusinessException("Company is inactive", HttpStatus.BAD_REQUEST);
            }

            // Company type must be BUYER or SUPPLIER
            String companyType = company.getCompanyType() != null ? company.getCompanyType().trim().toUpperCase() : "";
            if (!"BUYER".equals(companyType) && !"SUPPLIER".equals(companyType)) {
                throw new BusinessException("Invalid company type: " + company.getCompanyType(), HttpStatus.BAD_REQUEST);
            }

            // If user explicitly provided companyType, it MUST match the company's companyType
            if (request.getCompanyType() != null && !request.getCompanyType().isBlank()) {
                String requestedType = request.getCompanyType().trim().toUpperCase();
                if (!companyType.equalsIgnoreCase(requestedType)) {
                    throw new BusinessException(String.format("Company type mismatch: selected company is %s but requested type is %s",
                            company.getCompanyType(), request.getCompanyType()), HttpStatus.BAD_REQUEST);
                }
            }

            roleName = companyType;
        } else {
            // Creating new company
            if (request.getCompanyType() == null || request.getCompanyType().isBlank()) {
                throw new BusinessException("Company type is required when registering a new company", HttpStatus.BAD_REQUEST);
            }
            String normalizedCompanyType = request.getCompanyType().trim().toUpperCase();
            if (!"BUYER".equals(normalizedCompanyType) && !"SUPPLIER".equals(normalizedCompanyType)) {
                throw new BusinessException("Invalid company type: " + request.getCompanyType() + ". Allowed types: BUYER, SUPPLIER", HttpStatus.BAD_REQUEST);
            }

            CreateCompanyRequest companyDto = request.getCompany();
            if (companyDto.getName() == null || companyDto.getName().isBlank()) {
                throw new BusinessException("Company name is required", HttpStatus.BAD_REQUEST);
            }
            if (companyDto.getTaxCode() == null || companyDto.getTaxCode().isBlank()) {
                throw new BusinessException("Tax code is required", HttpStatus.BAD_REQUEST);
            }
            if (companyRepository.existsByTaxCode(companyDto.getTaxCode().trim())) {
                throw new BusinessException("Tax code already exists: " + companyDto.getTaxCode(), HttpStatus.CONFLICT);
            }

            company = new Company();
            company.setName(companyDto.getName().trim());
            company.setTaxCode(companyDto.getTaxCode().trim());
            company.setEmail(companyDto.getEmail() != null ? companyDto.getEmail().trim() : null);
            company.setPhone(companyDto.getPhone() != null ? companyDto.getPhone().trim() : null);
            company.setAddress(companyDto.getAddress() != null ? companyDto.getAddress().trim() : null);
            company.setCompanyType(normalizedCompanyType);
            company.setStatus("ACTIVE");
            company.setCreatedAt(LocalDateTime.now());
            company.setUpdatedAt(LocalDateTime.now());
            company = companyRepository.save(company);

            roleName = normalizedCompanyType;
        }

        // 4. Look up Role (Rule 6 compliant: master data via EntityManager, matching company.companyType)
        Role role = findRoleByName(roleName);

        // 5. Create and save User with hashed password
        User user = new User();
        user.setRole(role);
        user.setCompany(company);
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPhone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null);
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        log.info("Successfully registered user id: {}, username: {}, role: {}, companyId: {}",
                user.getId(), user.getUsername(), role.getName(), company.getId());

        // 6. Generate application JWT token and return LoginResponse
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

    @Override
    @Transactional
    public void linkOAuth2(Long currentUserId, OAuth2LinkRequest request) {
        if (currentUserId == null) {
            throw new BusinessException("User authentication required", HttpStatus.UNAUTHORIZED);
        }

        log.info("Processing OAuth2 link for user id: {}", currentUserId);

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        if (user.getStatus() == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException("User account is inactive", HttpStatus.BAD_REQUEST);
        }

        String providerUserId = null;
        String email = null;

        if (request != null && StringUtils.hasText(request.getLinkToken())) {
            Claims claims = jwtTokenProvider.validateAndGetLinkClaims(request.getLinkToken());
            String providerStr = claims.get(JwtTokenProvider.CLAIM_PROVIDER, String.class);
            if (providerStr == null || !AuthProvider.GOOGLE.name().equalsIgnoreCase(providerStr)) {
                throw new BusinessException("Unsupported OAuth2 provider: " + providerStr, HttpStatus.BAD_REQUEST);
            }
            Object claimUserIdObj = claims.get("userId");
            if (claimUserIdObj instanceof Number n && !currentUserId.equals(n.longValue())) {
                throw new BusinessException("Link token does not belong to the authenticated user", HttpStatus.FORBIDDEN);
            }
            providerUserId = claims.get(JwtTokenProvider.CLAIM_PROVIDER_USER_ID, String.class);
            email = claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class);
        } else {
            OAuth2LinkStateStore.VerifiedGoogleIdentity identity =
                    oauth2LinkStateStore.getAndRemoveVerifiedIdentity(currentUserId);
            if (identity != null) {
                if (identity.getProvider() != AuthProvider.GOOGLE) {
                    throw new BusinessException("Unsupported OAuth2 provider: " + identity.getProvider(), HttpStatus.BAD_REQUEST);
                }
                providerUserId = identity.getProviderUserId();
                email = identity.getEmail();
            }
        }

        if (!StringUtils.hasText(providerUserId)) {
            throw new BusinessException("Missing verified Google identity. Please authenticate with Google first.", HttpStatus.BAD_REQUEST);
        }

        // 1. Check if this Google account is already linked to ANY user
        Optional<AuthAccount> existingAccountOpt = authAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, providerUserId);

        if (existingAccountOpt.isPresent()) {
            User linkedUser = existingAccountOpt.get().getUser();
            if (linkedUser != null && linkedUser.getId().equals(currentUserId)) {
                log.info("Google account {} is already linked to current user id: {}", providerUserId, currentUserId);
                return; // Idempotent success
            } else {
                throw new BusinessException("This Google account is already linked to another user", HttpStatus.CONFLICT);
            }
        }

        // 2. Check if current user is already linked to a Google account
        Optional<AuthAccount> userGoogleAccountOpt = authAccountRepository
                .findByUserIdAndProvider(currentUserId, AuthProvider.GOOGLE);

        if (userGoogleAccountOpt.isPresent()) {
            throw new BusinessException("Your account is already linked to a different Google account", HttpStatus.CONFLICT);
        }

        // 3. Create and save AuthAccount (DO NOT alter User, Company, Role, Password)
        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerUserId(providerUserId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        authAccountRepository.save(authAccount);

        log.info("Successfully linked Google account (sub: {}) to user id: {} ({})",
                providerUserId, currentUserId, user.getUsername());
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

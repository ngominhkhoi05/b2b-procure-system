package com.b2bprocure.system.auth;

import com.b2bprocure.system.auth.dto.LoginRequest;
import com.b2bprocure.system.role.entity.Role;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.b2bprocure.system.auth.dto.OAuth2LinkRequest;
import com.b2bprocure.system.auth.dto.OAuth2RegisterRequest;
import com.b2bprocure.system.auth.dto.RegisterRequest;
import com.b2bprocure.system.authaccount.entity.AuthAccount;
import com.b2bprocure.system.authaccount.repository.AuthAccountRepository;
import com.b2bprocure.system.common.enums.AuthProvider;
import com.b2bprocure.system.company.dto.CreateCompanyRequest;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.OAuth2LinkStateStore;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(AuthIntegrationTest.TestProtectedControllerConfig.class)
public class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private OAuth2LinkStateStore oauth2LinkStateStore;

    @Autowired(required = false)
    private com.b2bprocure.system.cart.repository.CartItemRepository cartItemRepository;

    @Autowired(required = false)
    private com.b2bprocure.system.cart.repository.CartRepository cartRepository;

    @Autowired(required = false)
    private com.b2bprocure.system.product.repository.ProductRepository productRepository;

    @Autowired(required = false)
    private com.b2bprocure.system.product.repository.ProductPriceRepository productPriceRepository;

    @Autowired(required = false)
    private com.b2bprocure.system.order.repository.OrderRepository orderRepository;

    @Autowired(required = false)
    private com.b2bprocure.system.order.repository.OrderItemRepository orderItemRepository;

    @Autowired(required = false)
    private com.b2bprocure.system.order.repository.OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired(required = false)
    private com.b2bprocure.system.payment.repository.PaymentRepository paymentRepository;

    private static boolean initialized = false;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        if (!initialized) {
            cleanNonSeedTestData();
            initialized = true;
        }
    }

    private void cleanNonSeedTestData() {
        // Delete in FK-safe order: children first, parents last.
        // payments -> order status history -> order items -> orders -> products/users/companies
        if (paymentRepository != null) {
            paymentRepository.deleteAll();
        }
        if (orderStatusHistoryRepository != null) {
            orderStatusHistoryRepository.deleteAll();
        }
        if (orderItemRepository != null) {
            orderItemRepository.deleteAll();
        }
        if (orderRepository != null) {
            orderRepository.deleteAll();
        }
        authAccountRepository.deleteAll();
        oauth2LinkStateStore.clearAll();
        if (cartItemRepository != null) {
            cartItemRepository.deleteAll();
        }
        if (cartRepository != null) {
            cartRepository.deleteAll();
        }
        if (productPriceRepository != null) {
            productPriceRepository.deleteAll();
        }
        if (productRepository != null) {
            productRepository.deleteAll();
        }
        for (User user : userRepository.findAll()) {
            if (!"admin".equals(user.getUsername()) && !"buyer".equals(user.getUsername()) && !"supplier".equals(user.getUsername())) {
                userRepository.delete(user);
            } else {
                user.setPassword(passwordEncoder.encode("password123"));
                user.setStatus("ACTIVE");
                userRepository.save(user);
            }
        }
        for (Company company : companyRepository.findAll()) {
            if (!"0101234567".equals(company.getTaxCode()) && !"0107654321".equals(company.getTaxCode())) {
                companyRepository.delete(company);
            }
        }
    }

    @TestConfiguration
    static class TestProtectedControllerConfig {
        @RestController
        @RequestMapping("/api/test-protected")
        static class TestProtectedController {
            @GetMapping
            public ResponseEntity<Map<String, Object>> getProtectedResource(
                    @AuthenticationPrincipal UserPrincipal userPrincipal
            ) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "username", userPrincipal.getUsername(),
                        "role", userPrincipal.getRole()
                ));
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("CASE 1: Login success with valid credentials (username: admin)")
    void testLoginSuccess_Admin() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("admin")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Login successful")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.userId", notNullValue()))
                .andExpect(jsonPath("$.data.username", is("admin")))
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andExpect(jsonPath("$.data.expiresIn", is(86400000)));
    }

    @Test
    @Order(2)
    @DisplayName("CASE 1 (b): Login success with email identifier (buyer@gmail.com)")
    void testLoginSuccess_WithEmail() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("buyer@gmail.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("buyer")))
                .andExpect(jsonPath("$.data.role", is("BUYER")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));
    }

    @Test
    @Order(3)
    @DisplayName("CASE 2: Login failure with wrong password")
    void testLoginFailure_WrongPassword() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("admin")
                .password("wrongpassword999")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    @Order(4)
    @DisplayName("CASE 3: Login failure with non-existent username")
    void testLoginFailure_UserNotFound() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("non_existent_user_xyz")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    @Order(5)
    @DisplayName("CASE 4: Validation error on empty username")
    void testLoginValidation_EmptyUsername() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(6)
    @DisplayName("CASE 5: Validation error on empty password")
    void testLoginValidation_EmptyPassword() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("admin")
                .password("")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(7)
    @DisplayName("CASE 6: Login failure for inactive/disabled user")
    void testLoginFailure_DisabledUser() throws Exception {
        String inactiveUsername = "inactive_test_user";
        if (!userRepository.existsByUsername(inactiveUsername)) {
            User adminUser = userRepository.findByUsername("admin").orElseThrow();
            Role adminRole = adminUser.getRole();

            User inactiveUser = new User();
            inactiveUser.setUsername(inactiveUsername);
            inactiveUser.setEmail("inactive@test.com");
            inactiveUser.setPassword(passwordEncoder.encode("password123"));
            inactiveUser.setRole(adminRole);
            inactiveUser.setStatus("INACTIVE");
            inactiveUser.setCreatedAt(LocalDateTime.now());
            inactiveUser.setUpdatedAt(LocalDateTime.now());
            userRepository.save(inactiveUser);
        }

        LoginRequest request = LoginRequest.builder()
                .username(inactiveUsername)
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("User account is disabled or inactive")));
    }

    @Test
    @Order(8)
    @DisplayName("CASE 7: Valid JWT authenticates protected API endpoint")
    void testProtectedEndpoint_WithValidToken() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("admin")
                .password("password123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = responseJson.get("data").get("accessToken").asText();

        mockMvc.perform(get("/api/test-protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    @Order(9)
    @DisplayName("CASE 8: Invalid JWT returns 401 Unauthorized on protected endpoint")
    void testProtectedEndpoint_WithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/test-protected")
                        .header("Authorization", "Bearer invalid.jwt.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("authentication is required")));
    }

    @Test
    @Order(10)
    @DisplayName("CASE 9: Missing JWT returns 401 Unauthorized on protected endpoint")
    void testProtectedEndpoint_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/test-protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("authentication is required")));
    }

    @Test
    @Order(11)
    @DisplayName("Verify /api/v1/auth/login works identically to /api/v1/auth/login")
    void testLogin_V1Endpoint() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("supplier@gmail.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("supplier")))
                .andExpect(jsonPath("$.data.role", is("SUPPLIER")));
    }

    // =========================================================================
    // GOOGLE OAUTH2 FIRST-TIME REGISTRATION TESTS
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("CASE 12: Google registration success with existing BUYER company")
    void testOAuth2Register_Success_ExistingBuyerCompany() throws Exception {
        String googleSub = "google_sub_buyer_100";
        String email = "new_google_buyer_100@gmail.com";
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", googleSub, email, "Google Buyer User"
        );

        Company buyerCompany = companyRepository.findByTaxCode("0101234567")
                .orElseThrow(() -> new IllegalStateException("Seed buyer company not found"));

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .companyId(buyerCompany.getId())
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Registration successful")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.role", is("BUYER")))
                .andReturn();

        // Verify Database state
        User createdUser = userRepository.findByEmailWithRoleAndCompany(email).orElse(null);
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getPassword()).isNull(); // password = NULL for Google user
        assertThat(createdUser.getRole().getName()).isEqualTo("BUYER");
        assertThat(createdUser.getCompany().getId()).isEqualTo(buyerCompany.getId());
        assertThat(createdUser.getStatus()).isEqualTo("ACTIVE");

        Optional<AuthAccount> authAccount = authAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub);
        assertThat(authAccount).isPresent();
        assertThat(authAccount.get().getUser().getId()).isEqualTo(createdUser.getId());

        // Verify generated token works to authenticate protected endpoint
        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = responseJson.get("data").get("accessToken").asText();

        mockMvc.perform(get("/api/test-protected")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(createdUser.getUsername())))
                .andExpect(jsonPath("$.role", is("BUYER")));

        // Verify Google user with NULL password CANNOT authenticate via username/password login
        LoginRequest loginRequest = LoginRequest.builder()
                .username(createdUser.getUsername())
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    @Order(13)
    @DisplayName("CASE 13: Google registration success with existing SUPPLIER company (token in body)")
    void testOAuth2Register_Success_ExistingSupplierCompany() throws Exception {
        String googleSub = "google_sub_supplier_200";
        String email = "new_google_supplier_200@gmail.com";
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", googleSub, email, "Google Supplier User"
        );

        Company supplierCompany = companyRepository.findByTaxCode("0107654321")
                .orElseThrow(() -> new IllegalStateException("Seed supplier company not found"));

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("SUPPLIER")
                .companyId(supplierCompany.getId())
                .registrationToken(registrationToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.role", is("SUPPLIER")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));

        User createdUser = userRepository.findByEmailWithRoleAndCompany(email).orElse(null);
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getPassword()).isNull();
        assertThat(createdUser.getRole().getName()).isEqualTo("SUPPLIER");
    }

    @Test
    @Order(14)
    @DisplayName("CASE 14: Google registration success with newly created company")
    void testOAuth2Register_Success_NewCompany() throws Exception {
        String googleSub = "google_sub_newco_300";
        String email = "newco_founder_300@gmail.com";
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", googleSub, email, "Founder User"
        );

        CreateCompanyRequest companyDto = CreateCompanyRequest.builder()
                .name("Alpha Innovations Ltd")
                .taxCode("9876543210")
                .email("info@alphainno.com")
                .phone("0988776655")
                .address("100 Innovation Park, Ha Noi")
                .build();

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .company(companyDto)
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.role", is("BUYER")));

        // Verify company created
        Optional<Company> createdCompany = companyRepository.findByTaxCode("9876543210");
        assertThat(createdCompany).isPresent();
        assertThat(createdCompany.get().getName()).isEqualTo("Alpha Innovations Ltd");
        assertThat(createdCompany.get().getCompanyType()).isEqualTo("BUYER");
        assertThat(createdCompany.get().getStatus()).isEqualTo("ACTIVE");

        // Verify user and auth account created
        Optional<User> createdUser = userRepository.findByEmailWithRoleAndCompany(email);
        assertThat(createdUser).isPresent();
        assertThat(createdUser.get().getPassword()).isNull();
        assertThat(createdUser.get().getCompany().getId()).isEqualTo(createdCompany.get().getId());
    }

    @Test
    @Order(15)
    @DisplayName("CASE 15: Google registration fails on company type mismatch")
    void testOAuth2Register_Failure_CompanyTypeMismatch() throws Exception {
        String googleSub = "google_sub_mismatch_400";
        String email = "mismatch_user_400@gmail.com";
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", googleSub, email, "Mismatch User"
        );

        Company supplierCompany = companyRepository.findByTaxCode("0107654321").orElseThrow();

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .companyId(supplierCompany.getId())
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("mismatch")));
    }

    @Test
    @Order(16)
    @DisplayName("CASE 16: Google registration fails when companyId not found")
    void testOAuth2Register_Failure_CompanyNotFound() throws Exception {
        String googleSub = "google_sub_notfound_500";
        String email = "notfound_user_500@gmail.com";
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", googleSub, email, "Not Found User"
        );

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .companyId(999999L)
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    @Order(17)
    @DisplayName("CASE 17: Google registration fails on ADMIN registration attempt")
    void testOAuth2Register_Failure_AdminRegistrationAttempt() throws Exception {
        String googleSub = "google_sub_admin_600";
        String email = "admin_attempt_600@gmail.com";
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", googleSub, email, "Admin Attempt User"
        );

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("ADMIN")
                .companyId(1L)
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Allowed types: BUYER, SUPPLIER")));
    }

    @Test
    @Order(18)
    @DisplayName("CASE 18: Google registration fails on duplicate Google AuthAccount")
    void testOAuth2Register_Failure_DuplicateGoogleAuthAccount() throws Exception {
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", "google_sub_buyer_100", "different_email_700@gmail.com", "Duplicate Sub User"
        );

        Company buyerCompany = companyRepository.findByTaxCode("0101234567").orElseThrow();

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .companyId(buyerCompany.getId())
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Google account is already registered")));
    }

    @Test
    @Order(19)
    @DisplayName("CASE 19: Google registration fails on duplicate User email")
    void testOAuth2Register_Failure_DuplicateEmail() throws Exception {
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", "google_sub_dupemail_800", "admin@gmail.com", "Duplicate Email User"
        );

        Company buyerCompany = companyRepository.findByTaxCode("0101234567").orElseThrow();

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .companyId(buyerCompany.getId())
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Email is already registered")));
    }

    @Test
    @Order(20)
    @DisplayName("CASE 20: Google registration fails on invalid or expired registration token")
    void testOAuth2Register_Failure_InvalidToken() throws Exception {
        Company buyerCompany = companyRepository.findByTaxCode("0101234567").orElseThrow();

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .companyId(buyerCompany.getId())
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer completely.invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Invalid registration token")));
    }

    @Test
    @Order(21)
    @DisplayName("CASE 21: Google registration fails on wrong token type (passing standard access token)")
    void testOAuth2Register_Failure_WrongTokenType() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("admin")
                .password("password123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        Company buyerCompany = companyRepository.findByTaxCode("0101234567").orElseThrow();

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .companyId(buyerCompany.getId())
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Invalid token type for registration")));
    }

    @Test
    @Order(22)
    @DisplayName("CASE 22: Registration token CANNOT authenticate protected application endpoints")
    void testRegistrationToken_CannotAuthenticateProtectedEndpoint() throws Exception {
        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", "google_sub_protected_check", "protected_check@gmail.com", "Protected Check"
        );

        mockMvc.perform(get("/api/test-protected")
                        .header("Authorization", "Bearer " + registrationToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("authentication is required")));
    }

    @Test
    @Order(23)
    @DisplayName("CASE 23: Google registration fails when selected company is inactive")
    void testOAuth2Register_Failure_InactiveCompany() throws Exception {
        Company inactiveCompany = companyRepository.findByTaxCode("1122334455").orElseGet(() -> {
            Company company = new Company();
            company.setName("Inactive Enterprise");
            company.setTaxCode("1122334455");
            company.setCompanyType("BUYER");
            company.setStatus("INACTIVE");
            company.setCreatedAt(LocalDateTime.now());
            company.setUpdatedAt(LocalDateTime.now());
            return companyRepository.save(company);
        });

        String registrationToken = jwtTokenProvider.generateRegistrationToken(
                "GOOGLE", "google_sub_inactive_check", "inactive_check@gmail.com", "Inactive Check"
        );

        OAuth2RegisterRequest request = OAuth2RegisterRequest.builder()
                .companyType("BUYER")
                .companyId(inactiveCompany.getId())
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/register")
                        .header("Authorization", "Bearer " + registrationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Company is inactive")));
    }

    // ==========================================
    // ACCOUNT LINKING TESTS (CASES 24 - 33)
    // ==========================================

    @Test
    @Order(24)
    @DisplayName("CASE 24: Unauthenticated request to /oauth2/link fails with 401 Unauthorized")
    void testOAuth2Link_Unauthenticated_Fails() throws Exception {
        OAuth2LinkRequest request = new OAuth2LinkRequest();

        mockMvc.perform(post("/api/v1/auth/oauth2/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("authentication is required")));
    }

    @Test
    @Order(25)
    @DisplayName("CASE 25: Authenticated user calling /oauth2/link with missing verified Google identity fails with 400")
    void testOAuth2Link_MissingVerifiedIdentity_Fails() throws Exception {
        User buyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        String accessToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyer));

        OAuth2LinkRequest request = new OAuth2LinkRequest();

        mockMvc.perform(post("/api/v1/auth/oauth2/link")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Missing verified Google identity")));
    }

    @Test
    @Order(26)
    @DisplayName("CASE 26: Authenticated user calling /oauth2/link with invalid linkToken fails with 401")
    void testOAuth2Link_InvalidLinkToken_Fails() throws Exception {
        User buyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        String accessToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyer));

        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .linkToken("invalid.malformed.linkToken")
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/link")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Invalid link token")));
    }

    @Test
    @Order(27)
    @DisplayName("CASE 27: Authenticated user calling /oauth2/link with linkToken belonging to a different user fails with 403")
    void testOAuth2Link_TokenUserMismatch_Fails() throws Exception {
        User buyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        String accessToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyer));

        // Generate linkToken for a different userId (9999L)
        String linkTokenForOtherUser = jwtTokenProvider.generateLinkToken(
                "GOOGLE", "sub_mismatch_test", "mismatch@gmail.com", 9999L
        );

        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .linkToken(linkTokenForOtherUser)
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/link")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Link token does not belong to the authenticated user")));
    }

    @Test
    @Order(28)
    @DisplayName("CASE 28: Authenticated user successfully links Google account via server-side OAuth2LinkStateStore")
    void testOAuth2Link_Success_ViaStateStore() throws Exception {
        User buyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        String originalPassword = buyer.getPassword();
        Long buyerId = buyer.getId();
        String accessToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyer));

        String googleSub = "google_buyer_sub_verified_101";
        oauth2LinkStateStore.recordVerifiedIdentity(buyerId, AuthProvider.GOOGLE, googleSub, "buyer_verified@gmail.com");

        OAuth2LinkRequest request = new OAuth2LinkRequest();

        mockMvc.perform(post("/api/v1/auth/oauth2/link")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Google account linked successfully")));

        // Verify AuthAccount created
        Optional<AuthAccount> accountOpt = authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub);
        assertThat(accountOpt).isPresent();
        assertThat(accountOpt.get().getUser().getId()).isEqualTo(buyerId);
        assertThat(accountOpt.get().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(accountOpt.get().getProviderUserId()).isEqualTo(googleSub);

        // Verify User Entity attributes NOT changed
        User refreshedBuyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        assertThat(refreshedBuyer.getPassword()).isEqualTo(originalPassword);
        assertThat(refreshedBuyer.getRole().getName()).isEqualTo("BUYER");
        assertThat(refreshedBuyer.getCompany()).isNotNull();
    }

    @Test
    @Order(29)
    @DisplayName("CASE 29: Linking already linked Google account to same user is idempotent and returns 200")
    void testOAuth2Link_AlreadyLinkedToSameUser_Idempotent() throws Exception {
        User buyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        String accessToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyer));
        String googleSub = "google_buyer_sub_verified_101";

        String linkToken = jwtTokenProvider.generateLinkToken("GOOGLE", googleSub, "buyer_verified@gmail.com", buyer.getId());

        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .linkToken(linkToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/link")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Google account linked successfully")));

        // Verify count in AuthAccount is still 1
        assertThat(authAccountRepository.findByUserId(buyer.getId())).hasSize(1);
    }

    @Test
    @Order(30)
    @DisplayName("CASE 30: Linking same Google account to a different user fails with 409 Conflict")
    void testOAuth2Link_AlreadyLinkedToAnotherUser_Fails() throws Exception {
        User supplier = userRepository.findByUsernameOrEmail("supplier").orElseThrow();
        String accessToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplier));
        String googleSub = "google_buyer_sub_verified_101"; // already linked to buyer!

        String linkToken = jwtTokenProvider.generateLinkToken("GOOGLE", googleSub, "buyer_verified@gmail.com", supplier.getId());

        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .linkToken(linkToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/link")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("already linked to another user")));
    }

    @Test
    @Order(31)
    @DisplayName("CASE 31: User already linked to Google cannot link another Google account (409 Conflict)")
    void testOAuth2Link_CurrentUserAlreadyLinked_Fails() throws Exception {
        User buyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        String accessToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyer));
        String newGoogleSub = "google_buyer_sub_brand_new_999";

        String linkToken = jwtTokenProvider.generateLinkToken("GOOGLE", newGoogleSub, "buyer_new@gmail.com", buyer.getId());

        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .linkToken(linkToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/oauth2/link")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("already linked to a different Google account")));
    }

    @Test
    @Order(32)
    @DisplayName("CASE 32: Dual login capability: user can login with password AND Google OAuth2 maps to same user")
    void testOAuth2Link_DualLoginVerification() throws Exception {
        // 1. Login with username/password
        LoginRequest loginRequest = LoginRequest.builder()
                .username("buyer")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("buyer")));

        // 2. Google OAuth2 identity resolves to same buyer user
        User buyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        AuthAccount linkedAccount = authAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google_buyer_sub_verified_101")
                .orElseThrow();

        assertThat(linkedAccount.getUser().getId()).isEqualTo(buyer.getId());
        assertThat(linkedAccount.getUser().getUsername()).isEqualTo("buyer");
    }

    @Test
    @Order(33)
    @DisplayName("CASE 33: Link token cannot be used to authenticate protected endpoints")
    void testLinkTokenCannotAccessProtectedEndpoints() throws Exception {
        User buyer = userRepository.findByUsernameOrEmail("buyer").orElseThrow();
        String linkToken = jwtTokenProvider.generateLinkToken("GOOGLE", "sub_dummy", "buyer@gmail.com", buyer.getId());

        mockMvc.perform(get("/api/test-protected")
                        .header("Authorization", "Bearer " + linkToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("authentication is required")));
    }

    @Test
    @Order(34)
    @DisplayName("CASE 34: Register successfully with existing BUYER company")
    void testRegister_Success_ExistingBuyerCompany() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("new_buyer_user")
                .password("securePassword123")
                .fullName("New Buyer Person")
                .email("newbuyer@example.com")
                .phone("0981112233")
                .companyId(1L)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("new_buyer_user")))
                .andExpect(jsonPath("$.data.role", is("BUYER")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));

        // Verify password is encrypted in database
        User createdUser = userRepository.findByUsernameOrEmail("new_buyer_user").orElseThrow();
        assertThat(createdUser.getPassword()).isNotEqualTo("securePassword123");
        assertThat(passwordEncoder.matches("securePassword123", createdUser.getPassword())).isTrue();
        assertThat(createdUser.getCompany().getId()).isEqualTo(1L);

        // Verify newly registered user can login immediately with /login
        LoginRequest loginRequest = LoginRequest.builder()
                .username("new_buyer_user")
                .password("securePassword123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("new_buyer_user")))
                .andExpect(jsonPath("$.data.role", is("BUYER")));
    }

    @Test
    @Order(35)
    @DisplayName("CASE 35: Register successfully creating new SUPPLIER company")
    void testRegister_Success_NewSupplierCompany() throws Exception {
        CreateCompanyRequest companyDto = CreateCompanyRequest.builder()
                .name("Global Logistics Supplier Corp")
                .taxCode("0998877665")
                .email("supplier@globallogistics.com")
                .phone("0909998888")
                .address("789 High Street, Da Nang")
                .build();

        RegisterRequest request = RegisterRequest.builder()
                .username("supplier_boss")
                .password("bossPassword123")
                .fullName("Boss Supplier")
                .email("boss@globallogistics.com")
                .phone("0909998888")
                .companyType("SUPPLIER")
                .company(companyDto)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("supplier_boss")))
                .andExpect(jsonPath("$.data.role", is("SUPPLIER")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));

        User createdUser = userRepository.findByEmailWithRoleAndCompany("boss@globallogistics.com").orElseThrow();
        assertThat(createdUser.getRole().getName()).isEqualTo("SUPPLIER");
        assertThat(createdUser.getCompany().getCompanyType()).isEqualTo("SUPPLIER");
        assertThat(createdUser.getCompany().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @Order(36)
    @DisplayName("CASE 36: Register fails with duplicate username (409 Conflict)")
    void testRegister_DuplicateUsername_Returns409() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("buyer")
                .password("anotherPassword123")
                .fullName("Duplicate Buyer")
                .email("distinct_buyer@example.com")
                .companyId(1L)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Username is already taken")));
    }

    @Test
    @Order(37)
    @DisplayName("CASE 37: Register fails with duplicate email (409 Conflict)")
    void testRegister_DuplicateEmail_Returns409() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("fresh_user_email_test")
                .password("anotherPassword123")
                .fullName("Duplicate Email User")
                .email("buyer@gmail.com")
                .companyId(1L)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Email is already registered")));
    }

    @Test
    @Order(38)
    @DisplayName("CASE 38: Register fails without companyId or company details (400 Bad Request)")
    void testRegister_MissingCompany_Returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("user_without_company")
                .password("password123")
                .fullName("No Company User")
                .email("nocompany@example.com")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Either companyId or company details must be provided")));
    }

    @Test
    @Order(39)
    @DisplayName("CASE 39: Register fails when requested companyType mismatches selected company (400 Bad Request)")
    void testRegister_CompanyTypeMismatch_Returns400() throws Exception {
        // Company 1 is BUYER, but request claims SUPPLIER
        RegisterRequest request = RegisterRequest.builder()
                .username("mismatch_user")
                .password("password123")
                .fullName("Mismatch User")
                .email("mismatch@example.com")
                .companyId(1L)
                .companyType("SUPPLIER")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Company type mismatch")));
    }

    @Test
    @Order(40)
    @DisplayName("CASE 40: Register fails when creating company with existing taxCode (409 Conflict)")
    void testRegister_DuplicateTaxCode_Returns409() throws Exception {
        // Tax code 0101234567 already belongs to Company 1
        CreateCompanyRequest companyDto = CreateCompanyRequest.builder()
                .name("Company With Stolen Tax Code")
                .taxCode("0101234567")
                .build();

        RegisterRequest request = RegisterRequest.builder()
                .username("stolen_tax_user")
                .password("password123")
                .fullName("Stolen Tax User")
                .email("stolentax@example.com")
                .companyType("BUYER")
                .company(companyDto)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Tax code already exists")));
    }

    @org.junit.jupiter.api.AfterAll
    static void cleanUpAfter(@Autowired AuthAccountRepository authAccountRepository,
                             @Autowired UserRepository userRepository,
                             @Autowired CompanyRepository companyRepository) {
        authAccountRepository.deleteAll();
        for (User user : userRepository.findAll()) {
            if (!"admin".equals(user.getUsername()) && !"buyer".equals(user.getUsername()) && !"supplier".equals(user.getUsername())) {
                userRepository.delete(user);
            }
        }
        for (Company company : companyRepository.findAll()) {
            if (!"0101234567".equals(company.getTaxCode()) && !"0107654321".equals(company.getTaxCode())) {
                companyRepository.delete(company);
            }
        }
    }

}

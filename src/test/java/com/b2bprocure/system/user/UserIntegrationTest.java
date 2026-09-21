package com.b2bprocure.system.user;

import com.b2bprocure.system.auth.dto.LoginRequest;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.role.entity.Role;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.dto.ChangePasswordRequest;
import com.b2bprocure.system.user.dto.UpdateUserRequest;
import com.b2bprocure.system.user.dto.UserStatusUpdateRequest;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private EntityManager entityManager;

    private String adminToken;
    private String buyerToken;
    private String supplierToken;

    private User adminUser;
    private User buyerUser;
    private User supplierUser;
    private Company buyerCompany;
    private Company supplierCompany;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        adminUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();
        buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        supplierUser = userRepository.findByUsernameWithRoleAndCompany("supplier").orElseThrow();

        adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));
        buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));
        supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplierUser));

        buyerCompany = companyRepository.findByTaxCode("0101234567").orElseThrow();
        supplierCompany = companyRepository.findByTaxCode("0107654321").orElseThrow();
    }

    private User createOrGetTestUser(String username, String email, Role role, Company company, String rawPassword, String status) {
        return userRepository.findByUsernameWithRoleAndCompany(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setEmail(email);
            u.setRole(role);
            u.setCompany(company);
            u.setPassword(rawPassword != null ? passwordEncoder.encode(rawPassword) : null);
            u.setFullName("Test User " + username);
            u.setStatus(status != null ? status : "ACTIVE");
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(u);
        });
    }

    // ========================================================================
    // GET /api/v1/users/me
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("1. GET /users/me: ADMIN -> success")
    void testGetCurrentUser_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(adminUser.getId().intValue())))
                .andExpect(jsonPath("$.data.username", is("admin")))
                .andExpect(jsonPath("$.data.roleName", is("ADMIN")))
                .andExpect(jsonPath("$.data.companyId", nullValue()))
                .andExpect(jsonPath("$.data.companyName", nullValue()))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @Order(2)
    @DisplayName("2. GET /users/me: BUYER -> success")
    void testGetCurrentUser_Buyer_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(buyerUser.getId().intValue())))
                .andExpect(jsonPath("$.data.username", is("buyer")))
                .andExpect(jsonPath("$.data.roleName", is("BUYER")))
                .andExpect(jsonPath("$.data.companyId", is(buyerCompany.getId().intValue())))
                .andExpect(jsonPath("$.data.companyName", is(buyerCompany.getName())))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @Order(3)
    @DisplayName("3. GET /users/me: SUPPLIER -> success")
    void testGetCurrentUser_Supplier_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(supplierUser.getId().intValue())))
                .andExpect(jsonPath("$.data.username", is("supplier")))
                .andExpect(jsonPath("$.data.roleName", is("SUPPLIER")))
                .andExpect(jsonPath("$.data.companyId", is(supplierCompany.getId().intValue())))
                .andExpect(jsonPath("$.data.companyName", is(supplierCompany.getName())))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @Order(4)
    @DisplayName("4. GET /users/me: unauthenticated -> 401")
    void testGetCurrentUser_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(5)
    @DisplayName("5. GET /users/me: response does not contain password")
    void testGetCurrentUser_ResponseDoesNotContainPassword() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // ========================================================================
    // PUT /api/v1/users/me
    // ========================================================================

    @Test
    @Order(6)
    @DisplayName("6. PUT /users/me: update profile -> success")
    void testUpdateCurrentUser_Success() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("Buyer Updated FullName")
                .phone("0912999888")
                .avatarUrl("https://example.com/buyer_avatar.png")
                .coverImageUrl("https://example.com/buyer_cover.png")
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.fullName", is("Buyer Updated FullName")))
                .andExpect(jsonPath("$.data.phone", is("0912999888")))
                .andExpect(jsonPath("$.data.avatarUrl", is("https://example.com/buyer_avatar.png")))
                .andExpect(jsonPath("$.data.coverImageUrl", is("https://example.com/buyer_cover.png")));
    }

    @Test
    @Order(7)
    @DisplayName("7. PUT /users/me: invalid request (blank fullName) -> 400")
    void testUpdateCurrentUser_InvalidRequest_Returns400() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("")
                .build();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(8)
    @DisplayName("8. PUT /users/me: Role remains unchanged")
    void testUpdateCurrentUser_RoleRemainsUnchanged() throws Exception {
        // Attempt to pass roleId or role in JSON
        String maliciousPayload = "{\"fullName\":\"Valid Name\",\"roleId\":1,\"role\":\"ADMIN\"}";

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isOk());

        User buyer = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        assertThat(buyer.getRole().getName()).isEqualTo("BUYER");
    }

    @Test
    @Order(9)
    @DisplayName("9. PUT /users/me: Company remains unchanged")
    void testUpdateCurrentUser_CompanyRemainsUnchanged() throws Exception {
        // Attempt to pass companyId in JSON
        String maliciousPayload = "{\"fullName\":\"Valid Name\",\"companyId\":" + supplierCompany.getId() + "}";

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isOk());

        User buyer = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        assertThat(buyer.getCompany().getId()).isEqualTo(buyerCompany.getId());
    }

    @Test
    @Order(10)
    @DisplayName("10. PUT /users/me: protected fields not overwritten")
    void testUpdateCurrentUser_ProtectedFieldsNotOverwritten() throws Exception {
        User before = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();

        String payload = "{\"fullName\":\"Tested Protected Fields\",\"username\":\"hacked_buyer\",\"email\":\"hacked@test.com\",\"id\":999}";

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username", is(before.getUsername())))
                .andExpect(jsonPath("$.data.email", is(before.getEmail())))
                .andExpect(jsonPath("$.data.id", is(before.getId().intValue())));

        User after = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        assertThat(after.getUsername()).isEqualTo(before.getUsername());
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
    }

    // ========================================================================
    // PATCH /api/v1/users/me/password
    // ========================================================================

    @Test
    @Order(11)
    @DisplayName("11. PATCH /users/me/password: correct current password -> success")
    void testChangePassword_CorrectCurrentPassword_Success() throws Exception {
        User testUser = createOrGetTestUser("pwd_change_user", "pwd_change@test.com", buyerUser.getRole(), buyerCompany, "initialPwd123", "ACTIVE");
        String token = jwtTokenProvider.generateToken(UserPrincipal.create(testUser));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("initialPwd123")
                .newPassword("updatedPwd456")
                .build();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Password changed successfully")));

        User updated = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
        assertThat(passwordEncoder.matches("updatedPwd456", updated.getPassword())).isTrue();
    }

    @Test
    @Order(12)
    @DisplayName("12. PATCH /users/me/password: wrong current password -> error 400")
    void testChangePassword_WrongCurrentPassword_ReturnsError() throws Exception {
        User testUser = createOrGetTestUser("pwd_wrong_user", "pwd_wrong@test.com", buyerUser.getRole(), buyerCompany, "correctPwd123", "ACTIVE");
        String token = jwtTokenProvider.generateToken(UserPrincipal.create(testUser));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("completelyWrongPwd")
                .newPassword("brandNewPassword123")
                .build();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Current password is incorrect")));
    }

    @Test
    @Order(13)
    @DisplayName("13. PATCH /users/me/password: invalid new password (< 6 chars) -> 400")
    void testChangePassword_InvalidNewPassword_Returns400() throws Exception {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("password123")
                .newPassword("123")
                .build();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(14)
    @DisplayName("14. PATCH /users/me/password: password is BCrypt encoded")
    void testChangePassword_PasswordIsBCryptEncoded() throws Exception {
        User testUser = createOrGetTestUser("pwd_encode_user", "pwd_encode@test.com", buyerUser.getRole(), buyerCompany, "oldPassword123", "ACTIVE");
        String token = jwtTokenProvider.generateToken(UserPrincipal.create(testUser));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPassword123")
                .newPassword("newSecurePassword789")
                .build();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User updated = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
        assertThat(updated.getPassword()).startsWith("$2a$");
    }

    @Test
    @Order(15)
    @DisplayName("15. PATCH /users/me/password: plaintext password is not saved")
    void testChangePassword_PlaintextPasswordNotSaved() throws Exception {
        User testUser = createOrGetTestUser("pwd_plain_user", "pwd_plain@test.com", buyerUser.getRole(), buyerCompany, "plainOld123", "ACTIVE");
        String token = jwtTokenProvider.generateToken(UserPrincipal.create(testUser));

        String newPassword = "plainNewPassword888";
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("plainOld123")
                .newPassword(newPassword)
                .build();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User updated = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
        assertThat(updated.getPassword()).isNotEqualTo(newPassword);
    }

    @Test
    @Order(16)
    @DisplayName("16. PATCH /users/me/password: Google-only user (password=NULL) -> 400 error")
    void testChangePassword_GoogleOnlyUser_Returns400() throws Exception {
        User googleUser = createOrGetTestUser("google_oauth2_user", "google_oauth2@gmail.com", buyerUser.getRole(), buyerCompany, null, "ACTIVE");
        String token = jwtTokenProvider.generateToken(UserPrincipal.create(googleUser));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("anyCurrentPwd")
                .newPassword("someNewPassword123")
                .build();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Account registered via OAuth2 does not have a password set")));
    }

    // ========================================================================
    // ADMIN GET USERS (/api/v1/users)
    // ========================================================================

    @Test
    @Order(17)
    @DisplayName("17. GET /users: ADMIN -> success")
    void testAdminGetUsers_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", not(empty())))
                .andExpect(jsonPath("$.data.pageSize", is(20)));
    }

    @Test
    @Order(18)
    @DisplayName("18. GET /users: pagination and filtering -> correct")
    void testAdminGetUsers_PaginationAndFilters_Correct() throws Exception {
        // Filter by role
        mockMvc.perform(get("/api/v1/users?role=BUYER&page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[0].roleName", is("BUYER")));

        // Filter by status
        mockMvc.perform(get("/api/v1/users?status=ACTIVE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[0].status", is("ACTIVE")));

        // Filter by keyword
        mockMvc.perform(get("/api/v1/users?keyword=admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[0].username", is("admin")));
    }

    @Test
    @Order(19)
    @DisplayName("19. GET /users: non-admin (BUYER & SUPPLIER) -> 403")
    void testAdminGetUsers_NonAdmin_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // ADMIN GET USER BY ID (/api/v1/users/{id})
    // ========================================================================

    @Test
    @Order(20)
    @DisplayName("20. GET /users/{id}: ADMIN -> success")
    void testAdminGetUserById_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + buyerUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(buyerUser.getId().intValue())))
                .andExpect(jsonPath("$.data.username", is("buyer")))
                .andExpect(jsonPath("$.data.roleName", is("BUYER")))
                .andExpect(jsonPath("$.data.companyName", is(buyerCompany.getName())));
    }

    @Test
    @Order(21)
    @DisplayName("21. GET /users/{id}: user not found -> 404")
    void testAdminGetUserById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(22)
    @DisplayName("22. GET /users/{id}: non-admin -> 403")
    void testAdminGetUserById_NonAdmin_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + supplierUser.getId())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(23)
    @DisplayName("23. GET /users/{id}: password not exposed")
    void testAdminGetUserById_PasswordNotExposed() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + buyerUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // ========================================================================
    // ADMIN UPDATE USER (/api/v1/users/{id})
    // ========================================================================

    @Test
    @Order(24)
    @DisplayName("24. PUT /users/{id}: ADMIN update profile -> success")
    void testAdminUpdateUser_Admin_Success() throws Exception {
        User target = createOrGetTestUser("admin_upd_target", "admin_upd_target@test.com", buyerUser.getRole(), buyerCompany, "password123", "ACTIVE");

        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("Admin Assigned FullName")
                .phone("0987654321")
                .avatarUrl("https://example.com/admin_set_avatar.png")
                .coverImageUrl("https://example.com/admin_set_cover.png")
                .build();

        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.fullName", is("Admin Assigned FullName")))
                .andExpect(jsonPath("$.data.phone", is("0987654321")));
    }

    @Test
    @Order(25)
    @DisplayName("25. PUT /users/{id}: role cannot be changed")
    void testAdminUpdateUser_RoleCannotBeChanged() throws Exception {
        User target = createOrGetTestUser("admin_role_target", "admin_role_target@test.com", buyerUser.getRole(), buyerCompany, "password123", "ACTIVE");

        String payload = "{\"fullName\":\"New Name\",\"roleId\":1,\"roleName\":\"ADMIN\"}";

        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        User checked = userRepository.findByIdWithRoleAndCompany(target.getId()).orElseThrow();
        assertThat(checked.getRole().getName()).isEqualTo("BUYER");
    }

    @Test
    @Order(26)
    @DisplayName("26. PUT /users/{id}: company cannot be changed")
    void testAdminUpdateUser_CompanyCannotBeChanged() throws Exception {
        User target = createOrGetTestUser("admin_comp_target", "admin_comp_target@test.com", buyerUser.getRole(), buyerCompany, "password123", "ACTIVE");

        String payload = "{\"fullName\":\"New Name\",\"companyId\":" + supplierCompany.getId() + "}";

        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        User checked = userRepository.findByIdWithRoleAndCompany(target.getId()).orElseThrow();
        assertThat(checked.getCompany().getId()).isEqualTo(buyerCompany.getId());
    }

    @Test
    @Order(27)
    @DisplayName("27. PUT /users/{id}: non-admin -> 403")
    void testAdminUpdateUser_NonAdmin_Returns403() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("Should Fail")
                .build();

        mockMvc.perform(put("/api/v1/users/" + buyerUser.getId())
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(28)
    @DisplayName("28. PUT /users/{id}: user not found -> 404")
    void testAdminUpdateUser_NotFound_Returns404() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("Non Existent User")
                .build();

        mockMvc.perform(put("/api/v1/users/999999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // ADMIN STATUS (/api/v1/users/{id}/status)
    // ========================================================================

    @Test
    @Order(29)
    @DisplayName("29. PATCH /users/{id}/status: ADMIN blocks user -> success")
    void testAdminUpdateStatus_BlockUser_Success() throws Exception {
        User target = createOrGetTestUser("status_block_user", "status_block@test.com", buyerUser.getRole(), buyerCompany, "password123", "ACTIVE");

        UserStatusUpdateRequest request = UserStatusUpdateRequest.builder()
                .status("BLOCKED")
                .build();

        mockMvc.perform(patch("/api/v1/users/" + target.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("BLOCKED")));

        User checked = userRepository.findById(target.getId()).orElseThrow();
        assertThat(checked.getStatus()).isEqualTo("BLOCKED");
    }

    @Test
    @Order(30)
    @DisplayName("30. PATCH status: blocked user login rejected -> 401")
    void testBlockedUser_LoginRejected_Returns401() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("status_block_user")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("User account is disabled or inactive")));
    }

    @Test
    @Order(31)
    @DisplayName("31. PATCH /users/{id}/status: ADMIN activates user -> success and login works")
    void testAdminUpdateStatus_ActivateUser_Success() throws Exception {
        User target = userRepository.findByUsername("status_block_user").orElseThrow();

        UserStatusUpdateRequest request = UserStatusUpdateRequest.builder()
                .status("ACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/users/" + target.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        // Verify login works again
        LoginRequest loginRequest = LoginRequest.builder()
                .username("status_block_user")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));
    }

    @Test
    @Order(32)
    @DisplayName("32. PATCH /users/{id}/status: non-admin -> 403")
    void testAdminUpdateStatus_NonAdmin_Returns403() throws Exception {
        UserStatusUpdateRequest request = UserStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/users/" + buyerUser.getId() + "/status")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(33)
    @DisplayName("33. PATCH /users/{id}/status: user not found -> 404")
    void testAdminUpdateStatus_NotFound_Returns404() throws Exception {
        UserStatusUpdateRequest request = UserStatusUpdateRequest.builder()
                .status("ACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/users/999999/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(34)
    @DisplayName("34. PATCH /users/{id}/status: invalid status value -> 400")
    void testAdminUpdateStatus_InvalidStatus_Returns400() throws Exception {
        UserStatusUpdateRequest request = UserStatusUpdateRequest.builder()
                .status("DORMANT")
                .build();

        mockMvc.perform(patch("/api/v1/users/" + buyerUser.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Invalid user status")));
    }

    @Test
    @Order(35)
    @DisplayName("35. PATCH /users/{id}/status: Admin cannot deactivate or block self -> 400")
    void testAdminUpdateStatus_AdminCannotSelfDeactivate_Returns400() throws Exception {
        UserStatusUpdateRequest request = UserStatusUpdateRequest.builder()
                .status("BLOCKED")
                .build();

        mockMvc.perform(patch("/api/v1/users/" + adminUser.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Admin cannot deactivate or block their own account")));
    }

    @AfterAll
    static void tearDown(
            @Autowired UserRepository userRepository,
            @Autowired PasswordEncoder passwordEncoder
    ) {
        userRepository.findByUsername("buyer").ifPresent(buyer -> {
            buyer.setPassword(passwordEncoder.encode("password123"));
            buyer.setStatus("ACTIVE");
            userRepository.save(buyer);
        });
    }

}

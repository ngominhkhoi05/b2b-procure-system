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

import java.time.LocalDateTime;
import java.util.Map;

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
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

}

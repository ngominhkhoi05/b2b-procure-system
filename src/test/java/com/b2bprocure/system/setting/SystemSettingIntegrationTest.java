package com.b2bprocure.system.setting;

import com.b2bprocure.system.common.enums.SettingKey;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.setting.dto.UpdateSystemSettingRequest;
import com.b2bprocure.system.setting.entity.SystemSetting;
import com.b2bprocure.system.setting.repository.SystemSettingRepository;
import com.b2bprocure.system.setting.service.SystemSettingService;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SystemSettingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Autowired
    private SystemSettingService systemSettingService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String buyerToken;
    private String supplierToken;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        User adminUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();
        User buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        User supplierUser = userRepository.findByUsernameWithRoleAndCompany("supplier").orElseThrow();

        adminUserId = adminUser.getId();
        adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));
        buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));
        supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplierUser));
    }

    // ========================================================================
    // 1. ADMIN GET ALL SETTINGS
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Admin: Get all settings -> 200 OK with predefined settings list")
    void testAdminGetAllSettings_Success() throws Exception {
        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].settingKey", is("PAYMENT_TIMEOUT_MINUTES")))
                .andExpect(jsonPath("$.data[1].settingKey", is("SUPPLIER_CONFIRM_TIMEOUT_HOURS")));
    }

    // ========================================================================
    // 2. ADMIN GET INDIVIDUAL SETTINGS
    // ========================================================================

    @Test
    @Order(2)
    @DisplayName("2. Admin: Get PAYMENT_TIMEOUT_MINUTES setting -> 200 OK")
    void testAdminGetPaymentTimeoutSetting_Success() throws Exception {
        mockMvc.perform(get("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.settingKey", is("PAYMENT_TIMEOUT_MINUTES")))
                .andExpect(jsonPath("$.data.settingValue", notNullValue()));
    }

    @Test
    @Order(3)
    @DisplayName("3. Admin: Get SUPPLIER_CONFIRM_TIMEOUT_HOURS setting -> 200 OK")
    void testAdminGetSupplierConfirmTimeoutSetting_Success() throws Exception {
        mockMvc.perform(get("/api/v1/settings/SUPPLIER_CONFIRM_TIMEOUT_HOURS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.settingKey", is("SUPPLIER_CONFIRM_TIMEOUT_HOURS")))
                .andExpect(jsonPath("$.data.settingValue", notNullValue()));
    }

    @Test
    @Order(4)
    @DisplayName("4. Admin: Get non-existent setting key -> 404 Not Found")
    void testAdminGetNonExistentSetting_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/settings/NON_EXISTENT_KEY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // 3. ADMIN UPDATE SETTINGS
    // ========================================================================

    @Test
    @Order(5)
    @DisplayName("5. Admin: Update PAYMENT_TIMEOUT_MINUTES from 15 to 30 -> 200 OK")
    void testAdminUpdatePaymentTimeout_Success() throws Exception {
        UpdateSystemSettingRequest request = UpdateSystemSettingRequest.builder()
                .settingValue("30")
                .build();

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.settingKey", is("PAYMENT_TIMEOUT_MINUTES")))
                .andExpect(jsonPath("$.data.settingValue", is("30")))
                .andExpect(jsonPath("$.data.updatedAt", notNullValue()))
                .andExpect(jsonPath("$.data.updatedBy", is(adminUserId.intValue())));
    }

    @Test
    @Order(6)
    @DisplayName("6. Verify after update: settingValue=30, updatedAt updated, updatedBy is current Admin")
    void testVerifyPaymentTimeoutAfterUpdate() {
        SystemSetting setting = systemSettingRepository.findBySettingKey("PAYMENT_TIMEOUT_MINUTES")
                .orElseThrow();
        assertEquals("30", setting.getSettingValue());
        assertNotNull(setting.getUpdatedAt());
        assertNotNull(setting.getUpdatedBy());
        assertEquals(adminUserId, setting.getUpdatedBy().getId());

        // Also test service helper method
        int timeoutMinutes = systemSettingService.getSettingValueAsInt(SettingKey.PAYMENT_TIMEOUT_MINUTES, 15);
        assertEquals(30, timeoutMinutes);
    }

    @Test
    @Order(7)
    @DisplayName("7. Admin: Update SUPPLIER_CONFIRM_TIMEOUT_HOURS from 24 to 48 -> 200 OK")
    void testAdminUpdateSupplierConfirmTimeout_Success() throws Exception {
        UpdateSystemSettingRequest request = UpdateSystemSettingRequest.builder()
                .settingValue("48")
                .build();

        mockMvc.perform(put("/api/v1/settings/SUPPLIER_CONFIRM_TIMEOUT_HOURS")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.settingKey", is("SUPPLIER_CONFIRM_TIMEOUT_HOURS")))
                .andExpect(jsonPath("$.data.settingValue", is("48")))
                .andExpect(jsonPath("$.data.updatedAt", notNullValue()))
                .andExpect(jsonPath("$.data.updatedBy", is(adminUserId.intValue())));

        SystemSetting setting = systemSettingRepository.findBySettingKey("SUPPLIER_CONFIRM_TIMEOUT_HOURS")
                .orElseThrow();
        assertEquals("48", setting.getSettingValue());
        assertEquals(adminUserId, setting.getUpdatedBy().getId());

        int timeoutHours = systemSettingService.getSettingValueAsInt(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS, 24);
        assertEquals(48, timeoutHours);
    }

    // ========================================================================
    // 4. VALUE VALIDATION FAILURES -> 400 BAD REQUEST
    // ========================================================================

    @Test
    @Order(8)
    @DisplayName("8. Update with settingValue = '0' -> 400 Bad Request")
    void testUpdateSetting_ZeroValue_BadRequest() throws Exception {
        UpdateSystemSettingRequest request = new UpdateSystemSettingRequest("0");

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(9)
    @DisplayName("9. Update with settingValue = '-1' -> 400 Bad Request")
    void testUpdateSetting_NegativeValue_BadRequest() throws Exception {
        UpdateSystemSettingRequest request = new UpdateSystemSettingRequest("-1");

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(10)
    @DisplayName("10. Update with settingValue = 'abc' -> 400 Bad Request")
    void testUpdateSetting_NonNumericValue_BadRequest() throws Exception {
        UpdateSystemSettingRequest request = new UpdateSystemSettingRequest("abc");

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(11)
    @DisplayName("11. Update with settingValue = '' -> 400 Bad Request")
    void testUpdateSetting_EmptyValue_BadRequest() throws Exception {
        UpdateSystemSettingRequest request = new UpdateSystemSettingRequest("");

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(12)
    @DisplayName("12. Update with settingValue = '   ' -> 400 Bad Request")
    void testUpdateSetting_BlankValue_BadRequest() throws Exception {
        UpdateSystemSettingRequest request = new UpdateSystemSettingRequest("   ");

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(13)
    @DisplayName("13. Update with settingValue = null -> 400 Bad Request")
    void testUpdateSetting_NullValue_BadRequest() throws Exception {
        String jsonWithNull = "{\"settingValue\": null}";

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithNull))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // 5. ROLE AUTHORIZATION TESTS
    // ========================================================================

    @Test
    @Order(14)
    @DisplayName("14. Buyer calling GET /api/v1/settings -> 403 Forbidden")
    void testBuyerGetSettings_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(15)
    @DisplayName("15. Supplier calling GET /api/v1/settings -> 403 Forbidden")
    void testSupplierGetSettings_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(16)
    @DisplayName("16. Buyer calling PUT /api/v1/settings/PAYMENT_TIMEOUT_MINUTES -> 403 Forbidden")
    void testBuyerUpdateSetting_Forbidden() throws Exception {
        UpdateSystemSettingRequest request = new UpdateSystemSettingRequest("20");

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(17)
    @DisplayName("17. Supplier calling PUT /api/v1/settings/PAYMENT_TIMEOUT_MINUTES -> 403 Forbidden")
    void testSupplierUpdateSetting_Forbidden() throws Exception {
        UpdateSystemSettingRequest request = new UpdateSystemSettingRequest("20");

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(18)
    @DisplayName("18. Unauthenticated request -> 401 Unauthorized")
    void testUnauthenticatedGetSettings_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settingValue\":\"20\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // 6. ENDPOINT INTEGRITY TESTS
    // ========================================================================

    @Test
    @Order(19)
    @DisplayName("19. No POST endpoint to create setting -> 405 Method Not Allowed")
    void testNoPostEndpoint_MethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settingKey\":\"NEW_KEY\",\"settingValue\":\"10\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @Order(20)
    @DisplayName("20. No DELETE endpoint to delete setting -> 405 Method Not Allowed")
    void testNoDeleteEndpoint_MethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @Order(21)
    @DisplayName("21. Update request cannot change setting key")
    void testUpdateSetting_CannotChangeKey() throws Exception {
        // Attempt to pass extra or alternate key in payload
        String body = "{\"settingKey\":\"TAMPERED_KEY\",\"settingValue\":\"35\"}";

        mockMvc.perform(put("/api/v1/settings/PAYMENT_TIMEOUT_MINUTES")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settingKey", is("PAYMENT_TIMEOUT_MINUTES")))
                .andExpect(jsonPath("$.data.settingValue", is("35")));

        // Verify TAMPERED_KEY was NOT created
        mockMvc.perform(get("/api/v1/settings/TAMPERED_KEY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Verify original key still exists with new value
        SystemSetting setting = systemSettingRepository.findBySettingKey("PAYMENT_TIMEOUT_MINUTES").orElseThrow();
        assertEquals("PAYMENT_TIMEOUT_MINUTES", setting.getSettingKey());
        assertEquals("35", setting.getSettingValue());
    }

    @Test
    @Order(22)
    @DisplayName("22. Cannot create arbitrary key through update -> 404 Not Found")
    void testCannotCreateArbitraryKeyThroughUpdate() throws Exception {
        UpdateSystemSettingRequest request = new UpdateSystemSettingRequest("100");

        mockMvc.perform(put("/api/v1/settings/ARBITRARY_NEW_KEY")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));

        assertFalse(systemSettingRepository.existsBySettingKey("ARBITRARY_NEW_KEY"));
    }

}

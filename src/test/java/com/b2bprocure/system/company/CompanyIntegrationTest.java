package com.b2bprocure.system.company;

import com.b2bprocure.system.company.dto.CompanyStatusUpdateRequest;
import com.b2bprocure.system.company.dto.UpdateCompanyRequest;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CompanyIntegrationTest {

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
    private JwtTokenProvider jwtTokenProvider;

    private String adminToken;
    private String buyerToken;
    private String supplierToken;

    private Company buyerCompany;
    private Company supplierCompany;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        User adminUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();
        User buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        User supplierUser = userRepository.findByUsernameWithRoleAndCompany("supplier").orElseThrow();

        adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));
        buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));
        supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplierUser));

        buyerCompany = companyRepository.findByTaxCode("0101234567").orElseThrow();
        supplierCompany = companyRepository.findByTaxCode("0107654321").orElseThrow();
    }

    // ========================================================================
    // GET /api/v1/companies/me
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("1. GET /companies/me: BUYER retrieves own company -> success")
    void testGetCurrentCompany_Buyer_Success() throws Exception {
        mockMvc.perform(get("/api/v1/companies/me")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(buyerCompany.getId().intValue())))
                .andExpect(jsonPath("$.data.name", is(buyerCompany.getName())))
                .andExpect(jsonPath("$.data.taxCode", is("0101234567")))
                .andExpect(jsonPath("$.data.companyType", is("BUYER")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(2)
    @DisplayName("2. GET /companies/me: SUPPLIER retrieves own company -> success")
    void testGetCurrentCompany_Supplier_Success() throws Exception {
        mockMvc.perform(get("/api/v1/companies/me")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(supplierCompany.getId().intValue())))
                .andExpect(jsonPath("$.data.name", is(supplierCompany.getName())))
                .andExpect(jsonPath("$.data.taxCode", is("0107654321")))
                .andExpect(jsonPath("$.data.companyType", is("SUPPLIER")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(3)
    @DisplayName("3. GET /companies/me: User without company -> error (400 Bad Request)")
    void testGetCurrentCompany_UserWithoutCompany_ReturnsError() throws Exception {
        mockMvc.perform(get("/api/v1/companies/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("does not belong to any company")));
    }

    @Test
    @Order(4)
    @DisplayName("4. GET /companies/me: unauthenticated -> unauthorized (401)")
    void testGetCurrentCompany_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/companies/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // GET /api/v1/companies/{id}
    // ========================================================================

    @Test
    @Order(5)
    @DisplayName("5. GET /companies/{id}: ADMIN views any company -> success")
    void testGetCompanyById_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + buyerCompany.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(buyerCompany.getId().intValue())))
                .andExpect(jsonPath("$.data.taxCode", is("0101234567")));

        mockMvc.perform(get("/api/v1/companies/" + supplierCompany.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(supplierCompany.getId().intValue())))
                .andExpect(jsonPath("$.data.taxCode", is("0107654321")));
    }

    @Test
    @Order(6)
    @DisplayName("6. GET /companies/{id}: BUYER views own company -> success")
    void testGetCompanyById_Buyer_OwnCompany_Success() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + buyerCompany.getId())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(buyerCompany.getId().intValue())));
    }

    @Test
    @Order(7)
    @DisplayName("7. GET /companies/{id}: SUPPLIER views own company -> success")
    void testGetCompanyById_Supplier_OwnCompany_Success() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + supplierCompany.getId())
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(supplierCompany.getId().intValue())));
    }

    @Test
    @Order(8)
    @DisplayName("8. GET /companies/{id}: BUYER views other company -> forbidden (403)")
    void testGetCompanyById_Buyer_OtherCompany_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + supplierCompany.getId())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(9)
    @DisplayName("9. GET /companies/{id}: SUPPLIER views other company -> forbidden (403)")
    void testGetCompanyById_Supplier_OtherCompany_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + buyerCompany.getId())
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(10)
    @DisplayName("10. GET /companies/{id}: Company does not exist -> not found (404)")
    void testGetCompanyById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/companies/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // GET /api/v1/companies
    // ========================================================================

    @Test
    @Order(11)
    @DisplayName("11. GET /companies: ADMIN list -> success")
    void testGetCompanies_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", not(empty())))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(2)));
    }

    @Test
    @Order(12)
    @DisplayName("12. GET /companies: pagination works properly")
    void testGetCompanies_Pagination_Success() throws Exception {
        mockMvc.perform(get("/api/v1/companies?page=0&size=1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.pageNo", is(0)))
                .andExpect(jsonPath("$.data.pageSize", is(1)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalPages", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.first", is(true)));
    }

    @Test
    @Order(13)
    @DisplayName("13. GET /companies: BUYER/SUPPLIER cannot list all companies -> forbidden (403)")
    void testGetCompanies_NonAdmin_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // PUT /api/v1/companies/me
    // ========================================================================

    @Test
    @Order(14)
    @DisplayName("14. PUT /companies/me: BUYER updates own company -> success")
    void testUpdateCurrentCompany_Buyer_Success() throws Exception {
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("B2B Retail Corporation Updated")
                .email("buyer-updated@b2bretail.com")
                .phone("0901234599")
                .address("789 Pho Hue, Ha Noi")
                .build();

        mockMvc.perform(put("/api/v1/companies/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("B2B Retail Corporation Updated")))
                .andExpect(jsonPath("$.data.email", is("buyer-updated@b2bretail.com")))
                .andExpect(jsonPath("$.data.phone", is("0901234599")))
                .andExpect(jsonPath("$.data.address", is("789 Pho Hue, Ha Noi")))
                .andExpect(jsonPath("$.data.companyType", is("BUYER")));
    }

    @Test
    @Order(15)
    @DisplayName("15. PUT /companies/me: SUPPLIER updates own company -> success")
    void testUpdateCurrentCompany_Supplier_Success() throws Exception {
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("B2B Wholesale Supplies Co. Updated")
                .email("supplier-updated@b2bwholesale.com")
                .phone("0907654399")
                .address("999 Tran Phu, Da Nang")
                .build();

        mockMvc.perform(put("/api/v1/companies/me")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("B2B Wholesale Supplies Co. Updated")))
                .andExpect(jsonPath("$.data.email", is("supplier-updated@b2bwholesale.com")))
                .andExpect(jsonPath("$.data.companyType", is("SUPPLIER")));
    }

    @Test
    @Order(16)
    @DisplayName("16. PUT /companies/me: invalid request (blank name) -> validation error (400)")
    void testUpdateCurrentCompany_InvalidRequest_ValidationError() throws Exception {
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("") // Blank name violates @NotBlank
                .email("invalid-email-format") // Violates @Email
                .build();

        mockMvc.perform(put("/api/v1/companies/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Validation failed")));
    }

    @Test
    @Order(17)
    @DisplayName("17. PUT /companies/me: update does NOT alter companyType")
    void testUpdateCurrentCompany_DoesNotAlterCompanyType() throws Exception {
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("B2B Retail Corporation Final")
                .email("contact@b2bretail.com")
                .phone("0901234567")
                .address("123 Nguyen Trai, Ha Noi")
                .build();

        mockMvc.perform(put("/api/v1/companies/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.companyType", is("BUYER")));

        // Verify in DB directly
        Company refreshed = companyRepository.findById(buyerCompany.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("BUYER", refreshed.getCompanyType());
    }

    // ========================================================================
    // PUT /api/v1/companies/{id}
    // ========================================================================

    @Test
    @Order(18)
    @DisplayName("18. PUT /companies/{id}: ADMIN update Company -> success")
    void testUpdateCompanyByAdmin_Success() throws Exception {
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("Admin Managed Supplier Co.")
                .email("admin-managed@wholesale.com")
                .phone("0988888888")
                .address("100 Nguyen Van Linh, Da Nang")
                .build();

        mockMvc.perform(put("/api/v1/companies/" + supplierCompany.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Admin Managed Supplier Co.")))
                .andExpect(jsonPath("$.data.email", is("admin-managed@wholesale.com")));
    }

    @Test
    @Order(19)
    @DisplayName("19. PUT /companies/{id}: ADMIN cannot alter companyType")
    void testUpdateCompanyByAdmin_CannotAlterCompanyType() throws Exception {
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("Admin Supplier Co. Checked")
                .build();

        mockMvc.perform(put("/api/v1/companies/" + supplierCompany.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.companyType", is("SUPPLIER")));

        // Verify in DB directly
        Company refreshed = companyRepository.findById(supplierCompany.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("SUPPLIER", refreshed.getCompanyType());
    }

    @Test
    @Order(20)
    @DisplayName("20. PUT /companies/{id}: Company does not exist -> not found (404)")
    void testUpdateCompanyByAdmin_NotFound() throws Exception {
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("Non-existent Company")
                .build();

        mockMvc.perform(put("/api/v1/companies/999999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(21)
    @DisplayName("21. PUT /companies/{id}: BUYER/SUPPLIER cannot use admin endpoint -> forbidden (403)")
    void testUpdateCompanyByAdmin_NonAdmin_Forbidden() throws Exception {
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("Malicious Update")
                .build();

        mockMvc.perform(put("/api/v1/companies/" + buyerCompany.getId())
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));

        mockMvc.perform(put("/api/v1/companies/" + supplierCompany.getId())
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // PATCH /api/v1/companies/{id}/status
    // ========================================================================

    @Test
    @Order(22)
    @DisplayName("22. PATCH status: ADMIN update status -> success")
    void testUpdateCompanyStatus_Admin_Success() throws Exception {
        CompanyStatusUpdateRequest request = CompanyStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/companies/" + buyerCompany.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));

        // Revert back to ACTIVE for consistency
        request.setStatus("ACTIVE");
        mockMvc.perform(patch("/api/v1/companies/" + buyerCompany.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(23)
    @DisplayName("23. PATCH status: non-admin -> forbidden (403)")
    void testUpdateCompanyStatus_NonAdmin_Forbidden() throws Exception {
        CompanyStatusUpdateRequest request = CompanyStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/companies/" + buyerCompany.getId() + "/status")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(24)
    @DisplayName("24. PATCH status: Company does not exist -> not found (404)")
    void testUpdateCompanyStatus_NotFound() throws Exception {
        CompanyStatusUpdateRequest request = CompanyStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/companies/999999/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // EXTRA TESTS: Filter & Conflict
    // ========================================================================

    @Test
    @Order(25)
    @DisplayName("25. GET /companies: filter by companyType and status")
    void testGetCompanies_WithFilters() throws Exception {
        mockMvc.perform(get("/api/v1/companies?companyType=BUYER&status=ACTIVE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[0].companyType", is("BUYER")));
    }

    @Test
    @Order(26)
    @DisplayName("26. PUT /companies/me: taxCode uniqueness conflict -> 409 Conflict")
    void testUpdateCurrentCompany_TaxCodeConflict() throws Exception {
        // Buyer tries to update taxCode to supplier's existing taxCode "0107654321"
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .name("Buyer Corp Conflict Attempt")
                .taxCode("0107654321")
                .build();

        mockMvc.perform(put("/api/v1/companies/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Tax code already exists")));
    }

    @Test
    @Order(27)
    @DisplayName("27. PATCH status: invalid status value -> 400 Bad Request")
    void testUpdateCompanyStatus_InvalidStatus() throws Exception {
        CompanyStatusUpdateRequest request = CompanyStatusUpdateRequest.builder()
                .status("INVALID_STATUS")
                .build();

        mockMvc.perform(patch("/api/v1/companies/" + buyerCompany.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Invalid company status")));
    }

}

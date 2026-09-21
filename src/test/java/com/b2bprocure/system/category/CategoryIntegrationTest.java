package com.b2bprocure.system.category;

import com.b2bprocure.system.category.dto.CategoryStatusUpdateRequest;
import com.b2bprocure.system.category.dto.CreateCategoryRequest;
import com.b2bprocure.system.category.dto.UpdateCategoryRequest;
import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CategoryIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired(required = false)
    private com.b2bprocure.system.product.repository.ProductPriceRepository productPriceRepository;

    @Autowired(required = false)
    private com.b2bprocure.system.product.repository.ProductRepository productRepository;

    private String adminToken;
    private String buyerToken;
    private String supplierToken;

    private static Long activeCategoryId;
    private static Long inactiveCategoryId;
    private static boolean initialized = false;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        if (!initialized) {
            if (productPriceRepository != null) {
                productPriceRepository.deleteAll();
            }
            if (productRepository != null) {
                productRepository.deleteAll();
            }
            categoryRepository.deleteAll();
            initialized = true;
        }

        User adminUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();
        User buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        User supplierUser = userRepository.findByUsernameWithRoleAndCompany("supplier").orElseThrow();

        adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));
        buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));
        supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplierUser));
    }

    // ========================================================================
    // 1. ADMIN TEST CASES
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Admin: Create Category successfully -> 201 Created (default status = ACTIVE)")
    void testAdminCreateCategory_Success() throws Exception {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Electronics")
                .description("Electronic devices and accessories")
                .build();

        var result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Category created successfully")))
                .andExpect(jsonPath("$.data.name", is("Electronics")))
                .andExpect(jsonPath("$.data.description", is("Electronic devices and accessories")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andReturn();

        var responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        activeCategoryId = responseBody.get("data").get("id").asLong();

        // Also create a second category to be turned INACTIVE later
        CreateCategoryRequest inactiveRequest = CreateCategoryRequest.builder()
                .name("Office Supplies")
                .description("Office desks, chairs and stationery")
                .build();

        var inactiveResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiveRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var inactiveResponseBody = objectMapper.readTree(inactiveResult.getResponse().getContentAsString());
        inactiveCategoryId = inactiveResponseBody.get("data").get("id").asLong();

        // Mark second category as INACTIVE
        CategoryStatusUpdateRequest statusRequest = CategoryStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/categories/" + inactiveCategoryId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));
    }

    @Test
    @Order(2)
    @DisplayName("2. Admin: View ACTIVE Category by ID -> 200 OK")
    void testAdminGetActiveCategoryById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + activeCategoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(activeCategoryId.intValue())))
                .andExpect(jsonPath("$.data.name", is("Electronics")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(3)
    @DisplayName("3. Admin: View INACTIVE Category by ID -> 200 OK")
    void testAdminGetInactiveCategoryById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + inactiveCategoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(inactiveCategoryId.intValue())))
                .andExpect(jsonPath("$.data.name", is("Office Supplies")))
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));
    }

    @Test
    @Order(4)
    @DisplayName("4. Admin: View category list containing both ACTIVE and INACTIVE -> 200 OK")
    void testAdminGetCategories_ContainsBothActiveAndInactive() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", not(empty())))
                .andExpect(jsonPath("$.data.content[*].status", hasItem("ACTIVE")))
                .andExpect(jsonPath("$.data.content[*].status", hasItem("INACTIVE")));

        // Test filtering by lowercase 'active'
        mockMvc.perform(get("/api/v1/categories?status=active")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].status", not(hasItem("INACTIVE"))))
                .andExpect(jsonPath("$.data.content[*].name", hasItem("Electronics")));

        // Test filtering by lowercase 'inactive'
        mockMvc.perform(get("/api/v1/categories?status=inactive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].status", not(hasItem("ACTIVE"))))
                .andExpect(jsonPath("$.data.content[*].name", hasItem("Office Supplies")));

        // Test searching by keyword
        mockMvc.perform(get("/api/v1/categories?keyword=elect")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].name", hasItem("Electronics")));
    }

    @Test
    @Order(5)
    @DisplayName("5. Admin: Update Category (name & description) -> 200 OK")
    void testAdminUpdateCategory_Success() throws Exception {
        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .name("Smart Electronics")
                .description("Smart electronic devices and gadgets")
                .build();

        mockMvc.perform(put("/api/v1/categories/" + activeCategoryId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Smart Electronics")))
                .andExpect(jsonPath("$.data.description", is("Smart electronic devices and gadgets")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(6)
    @DisplayName("6. Admin: Change Category status ACTIVE -> INACTIVE -> 200 OK")
    void testAdminChangeStatus_ActiveToInactive_Success() throws Exception {
        CategoryStatusUpdateRequest request = CategoryStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/categories/" + activeCategoryId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));
    }

    @Test
    @Order(7)
    @DisplayName("7. Admin: Change Category status INACTIVE -> ACTIVE -> 200 OK")
    void testAdminChangeStatus_InactiveToActive_Success() throws Exception {
        CategoryStatusUpdateRequest request = CategoryStatusUpdateRequest.builder()
                .status("ACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/categories/" + activeCategoryId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(8)
    @DisplayName("8. Admin: Cannot create Category with duplicate name -> 409 Conflict")
    void testAdminCreateCategory_DuplicateName_Conflict() throws Exception {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Smart Electronics")
                .description("Duplicate name check")
                .build();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(9)
    @DisplayName("9. Admin: Update Category retaining same name -> 200 OK")
    void testAdminUpdateCategory_RetainingSameName_Success() throws Exception {
        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .name("Smart Electronics")
                .description("Description updated without changing name")
                .build();

        mockMvc.perform(put("/api/v1/categories/" + activeCategoryId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Smart Electronics")))
                .andExpect(jsonPath("$.data.description", is("Description updated without changing name")));
    }

    // ========================================================================
    // 2. BUYER TEST CASES
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("10. Buyer: View ACTIVE Category in list -> 200 OK")
    void testBuyerGetCategories_SeesActive() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].name", hasItem("Smart Electronics")));
    }

    @Test
    @Order(11)
    @DisplayName("11. Buyer: Does NOT see INACTIVE Category in list -> 200 OK")
    void testBuyerGetCategories_DoesNotSeeInactive() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].name", not(hasItem("Office Supplies"))))
                .andExpect(jsonPath("$.data.content[*].status", not(hasItem("INACTIVE"))));
    }

    @Test
    @Order(12)
    @DisplayName("12. Buyer: Get ACTIVE Category by ID -> 200 OK")
    void testBuyerGetActiveCategoryById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + activeCategoryId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(activeCategoryId.intValue())))
                .andExpect(jsonPath("$.data.name", is("Smart Electronics")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(13)
    @DisplayName("13. Buyer: Get INACTIVE Category by ID -> 404 Not Found")
    void testBuyerGetInactiveCategoryById_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + inactiveCategoryId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(14)
    @DisplayName("14. Buyer: Cannot create Category -> 403 Forbidden")
    void testBuyerCreateCategory_Forbidden() throws Exception {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Buyer Unauthorized Category")
                .description("Buyer trying to create category")
                .build();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(15)
    @DisplayName("15. Buyer: Cannot update Category -> 403 Forbidden")
    void testBuyerUpdateCategory_Forbidden() throws Exception {
        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .name("Buyer Hacked Name")
                .description("Buyer trying to update category")
                .build();

        mockMvc.perform(put("/api/v1/categories/" + activeCategoryId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(16)
    @DisplayName("16. Buyer: Cannot update Category status -> 403 Forbidden")
    void testBuyerUpdateCategoryStatus_Forbidden() throws Exception {
        CategoryStatusUpdateRequest request = CategoryStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/categories/" + activeCategoryId + "/status")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // 3. SUPPLIER TEST CASES
    // ========================================================================

    @Test
    @Order(17)
    @DisplayName("17. Supplier: View ACTIVE Category in list -> 200 OK")
    void testSupplierGetCategories_SeesActive() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].name", hasItem("Smart Electronics")));
    }

    @Test
    @Order(18)
    @DisplayName("18. Supplier: Does NOT see INACTIVE Category in list -> 200 OK")
    void testSupplierGetCategories_DoesNotSeeInactive() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].name", not(hasItem("Office Supplies"))))
                .andExpect(jsonPath("$.data.content[*].status", not(hasItem("INACTIVE"))));
    }

    @Test
    @Order(19)
    @DisplayName("19. Supplier: Get ACTIVE Category by ID -> 200 OK")
    void testSupplierGetActiveCategoryById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + activeCategoryId)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(activeCategoryId.intValue())))
                .andExpect(jsonPath("$.data.name", is("Smart Electronics")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(20)
    @DisplayName("20. Supplier: Cannot view INACTIVE Category by ID -> 404 Not Found")
    void testSupplierGetInactiveCategoryById_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + inactiveCategoryId)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(21)
    @DisplayName("21. Supplier: Cannot create Category -> 403 Forbidden")
    void testSupplierCreateCategory_Forbidden() throws Exception {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Supplier Unauthorized Category")
                .description("Supplier trying to create category")
                .build();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(22)
    @DisplayName("22. Supplier: Cannot update Category -> 403 Forbidden")
    void testSupplierUpdateCategory_Forbidden() throws Exception {
        UpdateCategoryRequest request = UpdateCategoryRequest.builder()
                .name("Supplier Hacked Name")
                .description("Supplier trying to update category")
                .build();

        mockMvc.perform(put("/api/v1/categories/" + activeCategoryId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(23)
    @DisplayName("23. Supplier: Cannot update Category status -> 403 Forbidden")
    void testSupplierUpdateCategoryStatus_Forbidden() throws Exception {
        CategoryStatusUpdateRequest request = CategoryStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/categories/" + activeCategoryId + "/status")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // 4. GENERAL TEST CASES
    // ========================================================================

    @Test
    @Order(24)
    @DisplayName("24. General: Validation fails when category name is blank -> 400 Bad Request")
    void testValidation_NameBlank_BadRequest() throws Exception {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("   ")
                .description("Blank name test")
                .build();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(25)
    @DisplayName("25. General: Category not found by ID -> 404 Not Found")
    void testCategoryNotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/categories/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(26)
    @DisplayName("26. General: Invalid status in status update request -> 400 Bad Request")
    void testInvalidStatus_ReturnsBadRequest() throws Exception {
        CategoryStatusUpdateRequest request = CategoryStatusUpdateRequest.builder()
                .status("SUSPENDED")
                .build();

        mockMvc.perform(patch("/api/v1/categories/" + activeCategoryId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(27)
    @DisplayName("27. General: No DELETE Category API -> 405 Method Not Allowed")
    void testDeleteCategory_MethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/" + activeCategoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(28)
    @DisplayName("28. General: Unauthenticated access to category endpoints -> 401 Unauthorized")
    void testUnauthenticatedAccess_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));

        mockMvc.perform(get("/api/v1/categories/" + activeCategoryId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));

        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("No Auth Category")
                .build();

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========================================================================
    // BONUS: BUYER tries to query ?status=INACTIVE -> still only gets ACTIVE
    // ========================================================================
    @Test
    @Order(29)
    @DisplayName("29. Bonus: Buyer querying ?status=INACTIVE cannot bypass rule -> still only gets ACTIVE")
    void testBuyerCannotBypassStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/categories?status=INACTIVE")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[*].name", not(hasItem("Office Supplies"))))
                .andExpect(jsonPath("$.data.content[*].status", not(hasItem("INACTIVE"))));
    }

    @AfterAll
    static void tearDown(
            @Autowired CategoryRepository categoryRepository,
            @Autowired(required = false) com.b2bprocure.system.product.repository.ProductPriceRepository productPriceRepository,
            @Autowired(required = false) com.b2bprocure.system.product.repository.ProductRepository productRepository
    ) {
        if (productPriceRepository != null) {
            productPriceRepository.deleteAll();
        }
        if (productRepository != null) {
            productRepository.deleteAll();
        }
        categoryRepository.deleteAll();
    }

}

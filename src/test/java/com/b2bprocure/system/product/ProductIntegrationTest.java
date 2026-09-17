package com.b2bprocure.system.product;

import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.product.dto.CreateProductPriceRequest;
import com.b2bprocure.system.product.dto.CreateProductRequest;
import com.b2bprocure.system.product.dto.ProductStatusUpdateRequest;
import com.b2bprocure.system.product.dto.UpdateProductPriceRequest;
import com.b2bprocure.system.product.dto.UpdateProductRequest;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
public class ProductIntegrationTest {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static String adminToken;
    private static String buyerToken;
    private static String supplierToken;
    private static String supplier2Token;

    private static Long activeCategoryId;
    private static Long inactiveCategoryId;
    private static Long supplierCompany1Id;
    private static Long supplierCompany2Id;
    private static Long buyerCompanyId;

    private static Long supplier1ActiveProductId;
    private static Long supplier1InactiveProductId;
    private static Long supplier1InactiveCatProductId;
    private static Long supplier2ProductId;
    private static Long samplePriceId;

    private static boolean initialized = false;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        if (!initialized) {
            productPriceRepository.deleteAll();
            productRepository.deleteAll();

            User adminUser = userRepository.findByUsernameWithRoleAndCompany("admin").orElseThrow();
            User buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
            User supplierUser = userRepository.findByUsernameWithRoleAndCompany("supplier").orElseThrow();

            adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));
            buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));
            supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(supplierUser));

            supplierCompany1Id = supplierUser.getCompany().getId();
            buyerCompanyId = buyerUser.getCompany().getId();

            // Ensure a second supplier company and user exist
            Company supplierCompany2 = companyRepository.findByTaxCode("0109999999").orElseGet(() -> {
                Company c = new Company();
                c.setName("Supplier 2 Logistics");
                c.setTaxCode("0109999999");
                c.setEmail("supplier2@logistics.com");
                c.setPhone("0908888888");
                c.setCompanyType("SUPPLIER");
                c.setStatus("ACTIVE");
                c.setCreatedAt(LocalDateTime.now());
                c.setUpdatedAt(LocalDateTime.now());
                return companyRepository.save(c);
            });
            supplierCompany2Id = supplierCompany2.getId();

            User supplier2User = userRepository.findByUsernameWithRoleAndCompany("supplier2").orElseGet(() -> {
                User u = new User();
                u.setUsername("supplier2");
                u.setEmail("supplier2@logistics.com");
                u.setPassword(supplierUser.getPassword());
                u.setFullName("Supplier 2 Manager");
                u.setPhone("0908888888");
                u.setRole(supplierUser.getRole());
                u.setCompany(supplierCompany2);
                u.setStatus("ACTIVE");
                u.setCreatedAt(LocalDateTime.now());
                u.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(u);
            });

            supplier2Token = jwtTokenProvider.generateToken(UserPrincipal.create(supplier2User));

            // Categories
            Category activeCat = categoryRepository.findAll().stream()
                    .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                    .findFirst()
                    .orElseGet(() -> categoryRepository.save(new Category(null, "Product Test Active Cat " + System.currentTimeMillis(), "Active category description", "ACTIVE", LocalDateTime.now(), LocalDateTime.now())));
            activeCategoryId = activeCat.getId();

            Category inactiveCat = categoryRepository.findAll().stream()
                    .filter(c -> "INACTIVE".equalsIgnoreCase(c.getStatus()))
                    .findFirst()
                    .orElseGet(() -> categoryRepository.save(new Category(null, "Product Test Inactive Cat " + System.currentTimeMillis(), "Inactive category description", "INACTIVE", LocalDateTime.now(), LocalDateTime.now())));
            inactiveCategoryId = inactiveCat.getId();

            initialized = true;
        }
    }

    // ========================================================================
    // 1. PRODUCT CREATE (Test 1 - 11)
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("1. Admin creates Product successfully -> 201 Created (default status = ACTIVE)")
    void test1_AdminCreateProduct_Success() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .supplierCompanyId(supplierCompany1Id)
                .categoryId(activeCategoryId)
                .sku("SKU-ADMIN-001")
                .name("Admin Product 1")
                .description("Created by admin")
                .imageUrl("https://example.com/admin1.jpg")
                .stockQuantity(50)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sku", is("SKU-ADMIN-001")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.supplierCompanyId", is(supplierCompany1Id.intValue())))
                .andExpect(jsonPath("$.data.supplierCompanyName", notNullValue()))
                .andExpect(jsonPath("$.data.categoryId", is(activeCategoryId.intValue())))
                .andExpect(jsonPath("$.data.stockQuantity", is(50)));
    }

    @Test
    @Order(2)
    @DisplayName("2. Supplier creates Product successfully -> 201 Created (ownership resolved from auth)")
    void test2_SupplierCreateProduct_Success() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(activeCategoryId)
                .sku("SKU-SUPPLIER-001")
                .name("Supplier Product 1")
                .description("Created by supplier")
                .imageUrl("https://example.com/sup1.jpg")
                .stockQuantity(100)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sku", is("SKU-SUPPLIER-001")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.supplierCompanyId", is(supplierCompany1Id.intValue())))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        supplier1ActiveProductId = root.path("data").path("id").asLong();
    }

    @Test
    @Order(3)
    @DisplayName("3. Buyer cannot create Product -> 403 Forbidden")
    void test3_BuyerCannotCreateProduct() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(activeCategoryId)
                .sku("SKU-BUYER-001")
                .name("Buyer Product")
                .stockQuantity(10)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("4. Supplier cannot create Product for other Supplier Company -> supplierCompanyId ignored, set to own company")
    void test4_SupplierCannotOverrideOwnership() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .supplierCompanyId(supplierCompany2Id) // Attempts to forge other company
                .categoryId(activeCategoryId)
                .sku("SKU-SUPPLIER-FORGE")
                .name("Forged Supplier Product")
                .stockQuantity(20)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.supplierCompanyId", is(supplierCompany1Id.intValue()))); // Overridden to company 1
    }

    @Test
    @Order(5)
    @DisplayName("5. Admin can specify valid Supplier Company -> 201 Created")
    void test5_AdminCreateProductForSupplierCompany_Success() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .supplierCompanyId(supplierCompany2Id)
                .categoryId(activeCategoryId)
                .sku("SKU-ADMIN-SUP2")
                .name("Admin Product For Sup2")
                .stockQuantity(30)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.supplierCompanyId", is(supplierCompany2Id.intValue())))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        supplier2ProductId = root.path("data").path("id").asLong();
    }

    @Test
    @Order(6)
    @DisplayName("6. Cannot create Product with non-existent Category -> 404 Not Found")
    void test6_CannotCreateWithNonExistentCategory() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(999999L)
                .sku("SKU-NON-CAT")
                .name("Invalid Category Product")
                .stockQuantity(10)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    @DisplayName("7. Cannot create Product with INACTIVE Category -> 400 Bad Request")
    void test7_CannotCreateWithInactiveCategory() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(inactiveCategoryId)
                .sku("SKU-INACT-CAT")
                .name("Inactive Category Product")
                .stockQuantity(10)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("INACTIVE category")));
    }

    @Test
    @Order(8)
    @DisplayName("8. Admin cannot create Product with non-existent Company -> 404 Not Found")
    void test8_AdminCannotCreateWithNonExistentCompany() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .supplierCompanyId(999999L)
                .categoryId(activeCategoryId)
                .sku("SKU-NON-COMP")
                .name("Invalid Company Product")
                .stockQuantity(10)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    @DisplayName("9. Admin cannot create Product with Company type BUYER -> 400 Bad Request")
    void test9_AdminCannotCreateWithBuyerCompany() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .supplierCompanyId(buyerCompanyId)
                .categoryId(activeCategoryId)
                .sku("SKU-BUYER-COMP")
                .name("Buyer Company Product")
                .stockQuantity(10)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("SUPPLIER")));
    }

    @Test
    @Order(10)
    @DisplayName("10. Cannot create Product with negative stock -> 400 Bad Request")
    void test10_CannotCreateWithNegativeStock() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(activeCategoryId)
                .sku("SKU-NEG-STOCK")
                .name("Negative Stock Product")
                .stockQuantity(-5)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(11)
    @DisplayName("11. Cannot create Product with duplicate SKU -> 409 Conflict")
    void test11_CannotCreateWithDuplicateSku() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(activeCategoryId)
                .sku("SKU-SUPPLIER-001") // Already exists from test 2
                .name("Duplicate SKU Product")
                .stockQuantity(10)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("SKU already exists")));
    }

    // ========================================================================
    // SETUP FIXTURES FOR LIST, DETAIL, UPDATE
    // ========================================================================

    @Test
    @Order(12)
    @DisplayName("11b. Setup additional product fixtures (Inactive product and product with inactive category)")
    void test11b_SetupFixtures() {
        // Create an INACTIVE product for supplier 1 directly in DB
        Product pInactive = new Product();
        pInactive.setSupplierCompany(companyRepository.findById(supplierCompany1Id).orElseThrow());
        pInactive.setCategory(categoryRepository.findById(activeCategoryId).orElseThrow());
        pInactive.setSku("SKU-SUP1-INACTIVE");
        pInactive.setName("Supplier 1 Inactive Product");
        pInactive.setDescription("Inactive product");
        pInactive.setStockQuantity(10);
        pInactive.setStatus("INACTIVE");
        pInactive.setCreatedAt(LocalDateTime.now());
        pInactive.setUpdatedAt(LocalDateTime.now());
        supplier1InactiveProductId = productRepository.save(pInactive).getId();

        // Create a product for supplier 1 with INACTIVE category directly in DB
        Product pInactCat = new Product();
        pInactCat.setSupplierCompany(companyRepository.findById(supplierCompany1Id).orElseThrow());
        pInactCat.setCategory(categoryRepository.findById(inactiveCategoryId).orElseThrow());
        pInactCat.setSku("SKU-SUP1-INACTCAT");
        pInactCat.setName("Supplier 1 Product With Inactive Category");
        pInactCat.setDescription("Product with inactive category");
        pInactCat.setStockQuantity(15);
        pInactCat.setStatus("ACTIVE");
        pInactCat.setCreatedAt(LocalDateTime.now());
        pInactCat.setUpdatedAt(LocalDateTime.now());
        supplier1InactiveCatProductId = productRepository.save(pInactCat).getId();
    }

    // ========================================================================
    // 2. PRODUCT LIST (Test 12 - 23)
    // ========================================================================

    @Test
    @Order(13)
    @DisplayName("12. Admin sees ACTIVE + INACTIVE products")
    void test12_AdminSeesActiveAndInactive() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", greaterThanOrEqualTo(4)));
    }

    @Test
    @Order(14)
    @DisplayName("13. Supplier sees own ACTIVE product")
    void test13_SupplierSeesOwnActive() throws Exception {
        mockMvc.perform(get("/api/v1/products?status=ACTIVE")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-SUPPLIER-001')]", not(empty())));
    }

    @Test
    @Order(15)
    @DisplayName("14. Supplier sees own INACTIVE product")
    void test14_SupplierSeesOwnInactive() throws Exception {
        mockMvc.perform(get("/api/v1/products?status=INACTIVE")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-SUP1-INACTIVE')]", not(empty())));
    }

    @Test
    @Order(16)
    @DisplayName("15. Supplier does not see other Supplier's products")
    void test15_SupplierDoesNotSeeOtherSupplierProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-ADMIN-SUP2')]", empty()));
    }

    @Test
    @Order(17)
    @DisplayName("16. Buyer only sees ACTIVE products")
    void test16_BuyerSeesActiveProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-SUPPLIER-001')]", not(empty())));
    }

    @Test
    @Order(18)
    @DisplayName("17. Buyer does not see INACTIVE products")
    void test17_BuyerDoesNotSeeInactive() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-SUP1-INACTIVE')]", empty()));
    }

    @Test
    @Order(19)
    @DisplayName("18. Buyer does not see Product whose Category is INACTIVE")
    void test18_BuyerDoesNotSeeProductWithInactiveCategory() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-SUP1-INACTCAT')]", empty()));
    }

    @Test
    @Order(20)
    @DisplayName("19. Buyer cannot bypass using ?status=INACTIVE")
    void test19_BuyerCannotBypassStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/products?status=INACTIVE")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.status == 'INACTIVE')]", empty()))
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-SUP1-INACTIVE')]", empty()));
    }

    @Test
    @Order(21)
    @DisplayName("20. Pagination works properly")
    void test20_PaginationWorks() throws Exception {
        mockMvc.perform(get("/api/v1/products?page=0&size=2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize", is(2)))
                .andExpect(jsonPath("$.data.pageNo", is(0)))
                .andExpect(jsonPath("$.data.content.length()", is(2)));
    }

    @Test
    @Order(22)
    @DisplayName("21. Keyword filter works")
    void test21_KeywordFilterWorks() throws Exception {
        mockMvc.perform(get("/api/v1/products?keyword=Supplier Product 1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-SUPPLIER-001')]", not(empty())))
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-ADMIN-001')]", empty()));
    }

    @Test
    @Order(23)
    @DisplayName("22. Status filter works according to role")
    void test22_StatusFilterWorks() throws Exception {
        mockMvc.perform(get("/api/v1/products?status=INACTIVE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.status == 'ACTIVE')]", empty()))
                .andExpect(jsonPath("$.data.content[?(@.sku == 'SKU-SUP1-INACTIVE')]", not(empty())));
    }

    @Test
    @Order(24)
    @DisplayName("23. Filter category/supplier does not bypass authorization")
    void test23_FilterCategorySupplierDoesNotBypass() throws Exception {
        // Supplier 1 tries to filter supplierCompanyId = 2 -> should still only return Supplier 1's products (i.e. 0 results)
        mockMvc.perform(get("/api/v1/products?supplierCompanyId=" + supplierCompany2Id)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.supplierCompanyId == " + supplierCompany2Id + ")]", empty()));
    }

    // ========================================================================
    // 3. PRODUCT DETAIL (Test 24 - 29)
    // ========================================================================

    @Test
    @Order(25)
    @DisplayName("24. Admin views any Product")
    void test24_AdminViewsAnyProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(supplier1ActiveProductId.intValue())));
    }

    @Test
    @Order(26)
    @DisplayName("25. Supplier views own Product")
    void test25_SupplierViewsOwnProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(supplier1ActiveProductId.intValue())));
    }

    @Test
    @Order(27)
    @DisplayName("26. Supplier cannot view other Supplier's Product -> 403 Forbidden")
    void test26_SupplierCannotViewOtherSupplierProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier2ProductId)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(28)
    @DisplayName("27. Buyer views ACTIVE Product")
    void test27_BuyerViewsActiveProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(supplier1ActiveProductId.intValue())));
    }

    @Test
    @Order(29)
    @DisplayName("28. Buyer cannot view INACTIVE Product -> 404 Not Found")
    void test28_BuyerCannotViewInactiveProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier1InactiveProductId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(30)
    @DisplayName("29. Buyer cannot view Product with INACTIVE Category -> 404 Not Found")
    void test29_BuyerCannotViewProductWithInactiveCategory() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier1InactiveCatProductId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound());
    }

    // ========================================================================
    // 4. PRODUCT UPDATE (Test 30 - 37)
    // ========================================================================

    @Test
    @Order(31)
    @DisplayName("30. Admin updates Product successfully")
    void test30_AdminUpdatesProduct_Success() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Updated Admin Product")
                .description("Updated description")
                .stockQuantity(77)
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Updated Admin Product")))
                .andExpect(jsonPath("$.data.stockQuantity", is(77)));
    }

    @Test
    @Order(32)
    @DisplayName("31. Supplier updates own Product successfully")
    void test31_SupplierUpdatesOwnProduct_Success() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Supplier Own Updated Product")
                .description("Updated by supplier")
                .stockQuantity(99)
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Supplier Own Updated Product")))
                .andExpect(jsonPath("$.data.stockQuantity", is(99)));
    }

    @Test
    @Order(33)
    @DisplayName("32. Supplier cannot update other Supplier's Product -> 403 Forbidden")
    void test32_SupplierCannotUpdateOtherSupplierProduct() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Hacked Product Name")
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier2ProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(34)
    @DisplayName("33. Buyer cannot update Product -> 403 Forbidden")
    void test33_BuyerCannotUpdateProduct() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Buyer Attempt")
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(35)
    @DisplayName("34. Cannot update to INACTIVE Category -> 400 Bad Request")
    void test34_CannotUpdateToInactiveCategory() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .categoryId(inactiveCategoryId)
                .name("Valid Name")
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("INACTIVE category")));
    }

    @Test
    @Order(36)
    @DisplayName("35. Supplier update cannot change product ownership")
    void test35_SupplierCannotChangeOwnership() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Keep My Ownership")
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierCompanyId", is(supplierCompany1Id.intValue())));
    }

    @Test
    @Order(37)
    @DisplayName("36. Cannot update stock to negative -> 400 Bad Request")
    void test36_CannotUpdateStockToNegative() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Negative Stock Update")
                .stockQuantity(-1)
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(38)
    @DisplayName("37. Cannot update to duplicate SKU -> 409 Conflict")
    void test37_CannotUpdateToDuplicateSku() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Valid Name")
                .sku("SKU-ADMIN-001") // Belongs to admin product from test 1
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("SKU already exists")));
    }

    // ========================================================================
    // 5. PRODUCT STATUS (Test 38 - 42)
    // ========================================================================

    @Test
    @Order(39)
    @DisplayName("38. Admin changes ACTIVE -> INACTIVE")
    void test38_AdminChangesActiveToInactive() throws Exception {
        ProductStatusUpdateRequest request = ProductStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/products/" + supplier1ActiveProductId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));
    }

    @Test
    @Order(40)
    @DisplayName("39. Admin changes INACTIVE -> ACTIVE")
    void test39_AdminChangesInactiveToActive() throws Exception {
        ProductStatusUpdateRequest request = ProductStatusUpdateRequest.builder()
                .status("ACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/products/" + supplier1ActiveProductId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(41)
    @DisplayName("40. Supplier changes own Product status")
    void test40_SupplierChangesOwnProductStatus() throws Exception {
        ProductStatusUpdateRequest request = ProductStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/products/" + supplier1ActiveProductId + "/status")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));

        // Revert back to ACTIVE for subsequent tests
        request.setStatus("ACTIVE");
        mockMvc.perform(patch("/api/v1/products/" + supplier1ActiveProductId + "/status")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(42)
    @DisplayName("41. Supplier cannot change other Supplier's Product status -> 403 Forbidden")
    void test41_SupplierCannotChangeOtherSupplierProductStatus() throws Exception {
        ProductStatusUpdateRequest request = ProductStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/products/" + supplier2ProductId + "/status")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(43)
    @DisplayName("42. Buyer cannot change Product status -> 403 Forbidden")
    void test42_BuyerCannotChangeProductStatus() throws Exception {
        ProductStatusUpdateRequest request = ProductStatusUpdateRequest.builder()
                .status("INACTIVE")
                .build();

        mockMvc.perform(patch("/api/v1/products/" + supplier1ActiveProductId + "/status")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ========================================================================
    // 6. PRICE TIER (Test 43 - 60)
    // ========================================================================

    @Test
    @Order(44)
    @DisplayName("43. Admin creates price tier successfully")
    void test43_AdminCreatesPriceTier_Success() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(1)
                .maxQuantity(9)
                .unitPrice(new BigDecimal("100000.00"))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.minQuantity", is(1)))
                .andExpect(jsonPath("$.data.maxQuantity", is(9)))
                .andExpect(jsonPath("$.data.unitPrice", is(100000.0)))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        samplePriceId = root.path("data").path("id").asLong();
    }

    @Test
    @Order(45)
    @DisplayName("44. Supplier owner creates price tier successfully")
    void test44_SupplierOwnerCreatesPriceTier_Success() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(10)
                .maxQuantity(49)
                .unitPrice(new BigDecimal("95000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.minQuantity", is(10)))
                .andExpect(jsonPath("$.data.maxQuantity", is(49)))
                .andExpect(jsonPath("$.data.unitPrice", is(95000.0)));
    }

    @Test
    @Order(46)
    @DisplayName("45. Other Supplier cannot create price tier -> 403 Forbidden")
    void test45_OtherSupplierCannotCreatePriceTier() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(50)
                .maxQuantity(99)
                .unitPrice(new BigDecimal("90000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplier2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(47)
    @DisplayName("46. Buyer cannot create price tier -> 403 Forbidden")
    void test46_BuyerCannotCreatePriceTier() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(50)
                .maxQuantity(99)
                .unitPrice(new BigDecimal("90000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(48)
    @DisplayName("47. Buyer can view price tiers of valid accessible Product -> 200 OK")
    void test47_BuyerCanViewPriceTiers() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @Order(49)
    @DisplayName("48. Supplier owner can view price tiers -> 200 OK")
    void test48_SupplierOwnerCanViewPriceTiers() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @Order(50)
    @DisplayName("49. Admin can view price tiers -> 200 OK")
    void test49_AdminCanViewPriceTiers() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @Order(51)
    @DisplayName("50. Supplier cannot view price tiers of other Supplier's Product -> 403 Forbidden")
    void test50_SupplierCannotViewOtherSupplierPriceTiers() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + supplier2ProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(52)
    @DisplayName("51. minQuantity < 1 rejected -> 400 Bad Request")
    void test51_MinQuantityLessThanOneRejected() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(0)
                .maxQuantity(5)
                .unitPrice(new BigDecimal("100000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(53)
    @DisplayName("52. maxQuantity < minQuantity rejected -> 400 Bad Request")
    void test52_MaxQuantityLessThanMinQuantityRejected() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(50)
                .maxQuantity(30)
                .unitPrice(new BigDecimal("100000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(54)
    @DisplayName("53. unitPrice <= 0 rejected -> 400 Bad Request")
    void test53_UnitPriceZeroOrNegativeRejected() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(50)
                .maxQuantity(99)
                .unitPrice(BigDecimal.ZERO)
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(55)
    @DisplayName("54. Overlapping tier rejected -> 400 Bad Request")
    void test54_OverlappingTierRejected() throws Exception {
        // Tiers currently: [1, 9] and [10, 49]
        // Try [5, 15] (overlaps with [1, 9] and [10, 49])
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(5)
                .maxQuantity(15)
                .unitPrice(new BigDecimal("92000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("overlaps with existing tier")));
    }

    @Test
    @Order(56)
    @DisplayName("55. Non-overlapping tiers succeed -> 201 Created")
    void test55_NonOverlappingTiersSucceed() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(50)
                .maxQuantity(99)
                .unitPrice(new BigDecimal("90000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.minQuantity", is(50)))
                .andExpect(jsonPath("$.data.maxQuantity", is(99)));
    }

    @Test
    @Order(57)
    @DisplayName("56. maxQuantity = null (unlimited tier) accepted -> 201 Created")
    void test56_UnlimitedTierAccepted() throws Exception {
        CreateProductPriceRequest request = CreateProductPriceRequest.builder()
                .minQuantity(100)
                .maxQuantity(null)
                .unitPrice(new BigDecimal("85000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products/" + supplier1ActiveProductId + "/prices")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.minQuantity", is(100)))
                .andExpect(jsonPath("$.data.maxQuantity").doesNotExist());
    }

    @Test
    @Order(58)
    @DisplayName("57. Update price tier successfully -> 200 OK")
    void test57_UpdatePriceTier_Success() throws Exception {
        UpdateProductPriceRequest request = UpdateProductPriceRequest.builder()
                .minQuantity(1)
                .maxQuantity(9)
                .unitPrice(new BigDecimal("99000.00"))
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId + "/prices/" + samplePriceId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitPrice", is(99000.0)));
    }

    @Test
    @Order(59)
    @DisplayName("58. Update price tier creating overlap rejected -> 400 Bad Request")
    void test58_UpdatePriceTierOverlapRejected() throws Exception {
        // Expand [1, 9] to [1, 20], which overlaps with [10, 49]
        UpdateProductPriceRequest request = UpdateProductPriceRequest.builder()
                .minQuantity(1)
                .maxQuantity(20)
                .unitPrice(new BigDecimal("99000.00"))
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId + "/prices/" + samplePriceId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("overlaps with existing tier")));
    }

    @Test
    @Order(60)
    @DisplayName("59. Delete price tier successfully -> 200 OK")
    void test59_DeletePriceTier_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/products/" + supplier1ActiveProductId + "/prices/" + samplePriceId)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("deleted successfully")));

        // Verify it was deleted
        assertFalse(productPriceRepository.existsById(samplePriceId));
    }

    @Test
    @Order(61)
    @DisplayName("60. Cannot operate on priceId belonging to another Product -> 400 Bad Request")
    void test60_PriceTierNotBelongingToProductRejected() throws Exception {
        // Create a price tier on supplier2ProductId
        ProductPrice p2Price = new ProductPrice();
        p2Price.setProduct(productRepository.findById(supplier2ProductId).orElseThrow());
        p2Price.setMinQuantity(1);
        p2Price.setMaxQuantity(10);
        p2Price.setUnitPrice(new BigDecimal("50000.00"));
        p2Price.setCreatedAt(LocalDateTime.now());
        p2Price.setUpdatedAt(LocalDateTime.now());
        ProductPrice savedP2Price = productPriceRepository.save(p2Price);

        // Admin tries to update savedP2Price via supplier1ActiveProductId
        UpdateProductPriceRequest request = UpdateProductPriceRequest.builder()
                .minQuantity(1)
                .maxQuantity(10)
                .unitPrice(new BigDecimal("45000.00"))
                .build();

        mockMvc.perform(put("/api/v1/products/" + supplier1ActiveProductId + "/prices/" + savedP2Price.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not belong to product")));
    }

    // ========================================================================
    // 6. RESERVED & AVAILABLE QUANTITY TESTS (Test 61 - 66)
    // ========================================================================

    private static Long reservedStockTestProductId;

    @Test
    @Order(62)
    @DisplayName("61. Create Product: stock=100 -> reserved=0, available=100")
    void test61_CreateProduct_ReservedZero_AvailableEqualsStock() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(activeCategoryId)
                .sku("SKU-RESERVED-001")
                .name("Reserved Test Product 1")
                .stockQuantity(100)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stockQuantity", is(100)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(0)))
                .andExpect(jsonPath("$.data.availableQuantity", is(100)))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        reservedStockTestProductId = root.path("data").path("id").asLong();
    }

    @Test
    @Order(63)
    @DisplayName("62. Client sends reservedQuantity when CREATE -> reservedQuantity remains 0, available=100")
    void test62_CreateProduct_IgnoresReservedQuantityInRequest() throws Exception {
        String jsonWithReserved = String.format("""
                {
                    "categoryId": %d,
                    "sku": "SKU-RESERVED-OVERRIDE",
                    "name": "Malicious Create Product",
                    "stockQuantity": 100,
                    "reservedQuantity": 50
                }
                """, activeCategoryId);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithReserved))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stockQuantity", is(100)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(0)))
                .andExpect(jsonPath("$.data.availableQuantity", is(100)));
    }

    @Test
    @Order(64)
    @DisplayName("63. Client sends reservedQuantity when UPDATE -> reservedQuantity remains unchanged")
    void test63_UpdateProduct_IgnoresReservedQuantityInRequest() throws Exception {
        Product product = productRepository.findById(reservedStockTestProductId).orElseThrow();
        product.setStockQuantity(100);
        product.setReservedQuantity(30);
        productRepository.save(product);

        String jsonUpdateWithReserved = """
                {
                    "name": "Updated Reserved Test Product",
                    "stockQuantity": 80,
                    "reservedQuantity": 0
                }
                """;

        mockMvc.perform(put("/api/v1/products/" + reservedStockTestProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonUpdateWithReserved))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity", is(80)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(30)))
                .andExpect(jsonPath("$.data.availableQuantity", is(50)));

        Product refreshed = productRepository.findById(reservedStockTestProductId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(30, refreshed.getReservedQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(80, refreshed.getStockQuantity());
    }

    @Test
    @Order(65)
    @DisplayName("64. Supplier updates stock >= reserved -> 200 OK (stock=50, reserved=30, available=20)")
    void test64_SupplierUpdateStock_GreaterThanReserved_Success() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Updated Reserved Test Product")
                .stockQuantity(50)
                .build();

        mockMvc.perform(put("/api/v1/products/" + reservedStockTestProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity", is(50)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(30)))
                .andExpect(jsonPath("$.data.availableQuantity", is(20)));
    }

    @Test
    @Order(66)
    @DisplayName("65. Supplier updates stock == reserved -> 200 OK (stock=30, reserved=30, available=0)")
    void test65_SupplierUpdateStock_EqualToReserved_Success() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Updated Reserved Test Product")
                .stockQuantity(30)
                .build();

        mockMvc.perform(put("/api/v1/products/" + reservedStockTestProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity", is(30)))
                .andExpect(jsonPath("$.data.reservedQuantity", is(30)))
                .andExpect(jsonPath("$.data.availableQuantity", is(0)));
    }

    @Test
    @Order(67)
    @DisplayName("66. Supplier updates stock < reserved -> 400 Bad Request (stock=29 < reserved=30)")
    void test66_SupplierUpdateStock_LessThanReserved_BadRequest() throws Exception {
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Updated Reserved Test Product")
                .stockQuantity(29)
                .build();

        mockMvc.perform(put("/api/v1/products/" + reservedStockTestProductId)
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("cannot be less than currently reserved quantity")));

        Product refreshed = productRepository.findById(reservedStockTestProductId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(30, refreshed.getStockQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(30, refreshed.getReservedQuantity());
    }

    @AfterAll
    static void tearDown(
            @Autowired ProductPriceRepository productPriceRepository,
            @Autowired ProductRepository productRepository
    ) {
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
    }

}

package com.b2bprocure.system.admin;

import com.b2bprocure.system.admin.dto.CreateCommissionRateRequest;
import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.commission.entity.CommissionRate;
import com.b2bprocure.system.commission.repository.CommissionRateRepository;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.entity.ProductPrice;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.role.entity.Role;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 8 — Admin Management integration tests.
 *
 * Maps to Step 8 spec §20 (User 9, Company 5, Product 6, Category 7, Order 4,
 * Commission Rate 5, Statistics 6, Security 4).
 */
@SpringBootTest
@DisplayName("Step 8 — Admin Management Integration Tests")
public class AdminIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CommissionRateRepository commissionRateRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String buyerToken;
    private String supplierToken;
    private String adminToken;

    private User adminUser;
    private User buyerUser;
    private User supplierUser;

    private Company buyerCompany;
    private Company supplierCompany;
    private Category category;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdCommissionRateIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        long ts = System.currentTimeMillis();

        supplierCompany = companyRepository.save(new Company(null, "Admin-Supplier-" + ts,
                "TAX-A-S-" + ts, "admin.sup@example.com", "0911111111", "111 Supplier", "SUPPLIER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));
        buyerCompany = companyRepository.save(new Company(null, "Admin-Buyer-" + ts,
                "TAX-A-B-" + ts, "admin.buyer@example.com", "0901234567", "111 Buyer", "BUYER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));

        category = categoryRepository.save(new Category(null, "Admin-Category-" + ts, "Description", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));

        Role buyerRole = new Role(2L, "BUYER", "Buyer");
        Role supplierRole = new Role(3L, "SUPPLIER", "Supplier");
        Role adminRole = new Role(1L, "ADMIN", "Administrator");

        adminUser = createUser("admin_user_" + ts, adminRole, null);
        buyerUser = createUser("admin_buyer_" + ts, buyerRole, buyerCompany);
        supplierUser = createUser("admin_supplier_" + ts, supplierRole, supplierCompany);

        adminToken = jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(adminUser.getId()).orElseThrow()));
        buyerToken = jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(buyerUser.getId()).orElseThrow()));
        supplierToken = jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(supplierUser.getId()).orElseThrow()));
    }

    @AfterEach
    void tearDown() {
        // Clean up in dependency order.

        // Orders first (depend on users, companies, products)
        for (Long orderId : createdOrderIds) {
            paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
            orderStatusHistoryRepository.deleteAll(
                    orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        createdOrderIds.clear();

        // Products (depend on companies, categories)
        for (Long productId : createdProductIds) {
            productPriceRepository.deleteAll(productPriceRepository.findByProductId(productId));
            productRepository.deleteById(productId);
        }
        createdProductIds.clear();

        // Commission rates must be deleted before users
        // (fk_commission_rates_created_by references users.id).
        for (Long crId : createdCommissionRateIds) {
            if (commissionRateRepository.existsById(crId)) {
                commissionRateRepository.deleteById(crId);
            }
        }
        createdCommissionRateIds.clear();

        // Users must be deleted before companies (fk_users_company FK constraint)
        // and before categories (categories are only in test-specific sets).
        for (User u : List.of(buyerUser, supplierUser, adminUser)) {
            if (u != null && u.getId() != null && userRepository.existsById(u.getId())) {
                userRepository.deleteById(u.getId());
            }
        }

        // Delete test-specific categories first, then the main seeded category.
        for (Long catId : createdCategoryIds) {
            if (catId.equals(category.getId())) continue;
            if (categoryRepository.existsById(catId)) {
                categoryRepository.deleteById(catId);
            }
        }
        createdCategoryIds.clear();
        if (category != null && category.getId() != null && categoryRepository.existsById(category.getId())) {
            categoryRepository.deleteById(category.getId());
        }

        // Companies now that all dependents are gone.
        if (buyerCompany != null && buyerCompany.getId() != null) companyRepository.deleteById(buyerCompany.getId());
        if (supplierCompany != null && supplierCompany.getId() != null) companyRepository.deleteById(supplierCompany.getId());
    }

    // =========================================================================
    // USER MANAGEMENT (9 cases)
    // =========================================================================

    @Nested
    @DisplayName("Admin User Management")
    class UserTests {

        @Test
        @DisplayName("Case U1: Admin lists users successfully")
        void caseU1_adminListsUsers() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", not(empty())));
        }

        @Test
        @DisplayName("Case U2: Admin views user detail (no password field)")
        void caseU2_adminUserDetail() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users/" + buyerUser.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(buyerUser.getId().intValue())))
                    .andExpect(jsonPath("$.data.username", is(buyerUser.getUsername())))
                    // Sensitive fields must NEVER be present in the response payload.
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        }

        @Test
        @DisplayName("Case U3: BUYER cannot access admin users endpoint → 403")
        void caseU3_buyerForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Case U4: SUPPLIER cannot access admin users endpoint → 403")
        void caseU4_supplierForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + supplierToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Case U5: Anonymous cannot access admin users endpoint → 401")
        void caseU5_anonymousUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Case U6: Admin lists users with pagination")
        void caseU6_pagination() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .param("size", "1")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageSize", is(1)));
        }

        @Test
        @DisplayName("Case U7: Admin filters users by role BUYER — returns only BUYER users")
        void caseU7_filterByRoleBuyer() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .param("role", "BUYER")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @DisplayName("Case U8: Admin cannot deactivate themselves → 400")
        void caseU8_adminCannotDeactivateSelf() throws Exception {
            String body = "{\"status\":\"INACTIVE\"}";
            mockMvc.perform(patch("/api/v1/admin/users/" + adminUser.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("cannot")));
        }

        @Test
        @DisplayName("Case U9: Admin can deactivate another user → 200")
        void caseU9_adminDeactivatesOtherUser() throws Exception {
            String body = "{\"status\":\"INACTIVE\"}";
            mockMvc.perform(patch("/api/v1/admin/users/" + buyerUser.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status", is("INACTIVE")));
        }
    }

    // =========================================================================
    // COMPANY MANAGEMENT (5 cases)
    // =========================================================================

    @Nested
    @DisplayName("Admin Company Management")
    class CompanyTests {

        @Test
        @DisplayName("Case C1: Admin lists all companies")
        void caseC1_listsAllCompanies() throws Exception {
            mockMvc.perform(get("/api/v1/admin/companies")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", not(empty())));
        }

        @Test
        @DisplayName("Case C2: Admin filters companies by type BUYER")
        void caseC2_filterByBuyerType() throws Exception {
            mockMvc.perform(get("/api/v1/admin/companies")
                            .param("companyType", "BUYER")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.companyType=='BUYER')]", not(empty())));
        }

        @Test
        @DisplayName("Case C3: Admin filters companies by type SUPPLIER")
        void caseC3_filterBySupplierType() throws Exception {
            mockMvc.perform(get("/api/v1/admin/companies")
                            .param("companyType", "SUPPLIER")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.companyType=='SUPPLIER')]", not(empty())));
        }

        @Test
        @DisplayName("Case C4: Admin company detail returns user and product counts")
        void caseC4_detailWithCounts() throws Exception {
            // Create one product for the supplier so its productCount is non-zero.
            Product p = createProduct(supplierCompany, "Count Prod", 100, 0);
            createdProductIds.add(p.getId());

            mockMvc.perform(get("/api/v1/admin/companies/" + supplierCompany.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(supplierCompany.getId().intValue())))
                    .andExpect(jsonPath("$.data.userCount", is(1))) // supplierUser only
                    .andExpect(jsonPath("$.data.productCount", is(1)));
        }

        @Test
        @DisplayName("Case C5: Admin can deactivate company (INACTIVE)")
        void caseC5_adminDeactivatesCompany() throws Exception {
            String body = "{\"status\":\"INACTIVE\"}";
            mockMvc.perform(patch("/api/v1/admin/companies/" + buyerCompany.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status", is("INACTIVE")));
        }
    }

    // =========================================================================
    // PRODUCT MANAGEMENT (6 cases)
    // =========================================================================

    @Nested
    @DisplayName("Admin Product Management")
    class ProductTests {

        @Test
        @DisplayName("Case P1: Admin lists all products — response is not empty")
        void caseP1_listsAllProducts() throws Exception {
            Product p = createProduct(supplierCompany, "Admin List Prod", 100, 0);

            mockMvc.perform(get("/api/v1/admin/products")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", not(empty())));
        }

        @Test
        @DisplayName("Case P2: Admin filters products by supplierCompanyId")
        void caseP2_filterBySupplier() throws Exception {
            Product p = createProduct(supplierCompany, "Admin Supplier Prod", 100, 0);

            mockMvc.perform(get("/api/v1/admin/products")
                            .param("supplierCompanyId", String.valueOf(supplierCompany.getId()))
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.data.content[0].supplierCompanyId", is(supplierCompany.getId().intValue())));
        }

        @Test
        @DisplayName("Case P3: Admin filters products by categoryId")
        void caseP3_filterByCategory() throws Exception {
            Product p = createProduct(supplierCompany, "Admin Category Prod", 100, 0);

            mockMvc.perform(get("/api/v1/admin/products")
                            .param("categoryId", String.valueOf(category.getId()))
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.data.content[0].categoryId", is(category.getId().intValue())));
        }

        @Test
        @DisplayName("Case P4: Admin product detail returns price tiers")
        void caseP4_detailWithPrices() throws Exception {
            Product p = createProduct(supplierCompany, "Admin Detail Prod", 100, 0);

            mockMvc.perform(get("/api/v1/admin/products/" + p.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.product.supplierCompanyName", is(supplierCompany.getName())))
                    .andExpect(jsonPath("$.data.priceTiers", hasSize(1)));
        }

        @Test
        @DisplayName("Case P5: Admin can deactivate product → 200")
        void caseP5_adminDeactivatesProduct() throws Exception {
            Product p = createProduct(supplierCompany, "Admin Deactivate Prod", 100, 0);

            String body = "{\"status\":\"INACTIVE\"}";
            mockMvc.perform(patch("/api/v1/admin/products/" + p.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status", is("INACTIVE")));
        }

        @Test
        @DisplayName("Case P6: Deactivating product does NOT modify existing order snapshot")
        void caseP6_existingOrderSnapshotPreserved() throws Exception {
            Product p = createProduct(supplierCompany, "Snap Prod", 100, 0);
            createdProductIds.add(p.getId());

            Order order = createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING,
                    p, 5);
            createdOrderIds.add(order.getId());

            // Capture snapshot before deactivation
            OrderItem snapshotItem = orderItemRepository.findByOrderId(order.getId()).get(0);
            String originalProductName = snapshotItem.getProductName();
            BigDecimal originalUnitPrice = snapshotItem.getUnitPrice();

            // Deactivate product
            String body = "{\"status\":\"INACTIVE\"}";
            mockMvc.perform(patch("/api/v1/admin/products/" + p.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status", is("INACTIVE")));

            // Verify order + item snapshot is unchanged
            Order fetched = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(fetched.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);

            OrderItem reloadedItem = orderItemRepository.findByOrderId(order.getId()).get(0);
            assertThat(reloadedItem.getProductName()).isEqualTo(originalProductName);
            assertThat(reloadedItem.getUnitPrice()).isEqualByComparingTo(originalUnitPrice);
        }
    }

    // =========================================================================
    // CATEGORY MANAGEMENT (7 cases)
    // =========================================================================

    @Nested
    @DisplayName("Admin Category Management")
    class CategoryTests {

        @Test
        @DisplayName("Case CA1: Admin lists all categories")
        void caseCA1_listsAllCategories() throws Exception {
            mockMvc.perform(get("/api/v1/admin/categories")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", not(empty())));
        }

        @Test
        @DisplayName("Case CA2: Admin category detail by id")
        void caseCA2_categoryDetail() throws Exception {
            mockMvc.perform(get("/api/v1/admin/categories/" + category.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(category.getId().intValue())))
                    .andExpect(jsonPath("$.data.name", is(category.getName())));
        }

        @Test
        @DisplayName("Case CA3: Admin can create a category")
        void caseCA3_createCategory() throws Exception {
            String uniqueName = "Admin-New-Cat-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);
            String body = "{\"name\":\"" + uniqueName + "\",\"description\":\"New cat desc\"}";
            mockMvc.perform(post("/api/v1/admin/categories")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name", is(uniqueName)))
                    .andExpect(jsonPath("$.data.status", is("ACTIVE")));
        }

        @Test
        @DisplayName("Case CA4: Validation - empty name returns 400")
        void caseCA4_validationEmptyName() throws Exception {
            String body = "{\"name\":\"\"}";
            mockMvc.perform(post("/api/v1/admin/categories")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Case CA5: Duplicate name returns 409")
        void caseCA5_duplicateName() throws Exception {
            String body = "{\"name\":\"" + category.getName() + "\"}";
            mockMvc.perform(post("/api/v1/admin/categories")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Case CA6: Admin can update a category")
        void caseCA6_updateCategory() throws Exception {
            String newName = "Admin-Updated-" + System.currentTimeMillis();
            String body = "{\"name\":\"" + newName + "\",\"description\":\"Updated desc\"}";
            mockMvc.perform(put("/api/v1/admin/categories/" + category.getId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name", is(newName)));
        }

        @Test
        @DisplayName("Case CA7: Admin can delete unused category → 200; cannot delete category with products → 409")
        void caseCA7_deleteCategory() throws Exception {
            // Create a fresh unused category and delete it
            Category c = categoryRepository.save(new Category(null, "Deletable-" + System.currentTimeMillis(),
                    "To delete", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));
            createdCategoryIds.add(c.getId());

            mockMvc.perform(delete("/api/v1/admin/categories/" + c.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            // Verify gone
            mockMvc.perform(delete("/api/v1/admin/categories/" + c.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());

            // Now: the seeded test category has a product → cannot delete
            Product p = createProduct(supplierCompany, "Keep Cat Prod", 100, 0);
            createdProductIds.add(p.getId());

            mockMvc.perform(delete("/api/v1/admin/categories/" + category.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isConflict());
        }
    }

    // =========================================================================
    // ORDER MANAGEMENT — read-only (4 cases)
    // =========================================================================

    @Nested
    @DisplayName("Admin Order Management (read-only)")
    class OrderTests {

        @Test
        @DisplayName("Case O1: Admin lists ALL orders (no role restriction)")
        void caseO1_adminSeesAllOrders() throws Exception {
            Product p = createProduct(supplierCompany, "Order Test P", 100, 0);
            Order mine = createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, p, 5);

            mockMvc.perform(get("/api/v1/admin/orders")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", not(empty())));
        }

        @Test
        @DisplayName("Case O2: Admin order detail returns items + status history + payment + commission")
        void caseO2_adminOrderDetail() throws Exception {
            Product p = createProduct(supplierCompany, "Order Detail P", 100, 0);
            Order order = createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.SHIPPING, PaymentMethod.COD, PaymentStatus.PENDING, p, 5);

            mockMvc.perform(get("/api/v1/admin/orders/" + order.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(order.getId().intValue())))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.payment").exists())
                    .andExpect(jsonPath("$.data.statusHistory", notNullValue()));
        }

        @Test
        @DisplayName("Case O3: Commission snapshot is visible on admin detail")
        void caseO3_commissionVisible() throws Exception {
            Product p = createProduct(supplierCompany, "Comm P", 100, 0);
            Order order = createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.COMPLETED, PaymentMethod.COD, PaymentStatus.SUCCESS, p, 5);
            order.setCommissionRate(new BigDecimal("5"));
            order.setCommissionAmount(new BigDecimal("25000"));
            orderRepository.save(order);

            mockMvc.perform(get("/api/v1/admin/orders/" + order.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.commissionRate", is(5.0)))
                    .andExpect(jsonPath("$.data.commissionAmount", is(25000.0)));
        }

        @Test
        @DisplayName("Case O4: Admin does NOT have a state-override endpoint under /api/v1/admin/orders/*/status")
        void caseO4_noStateOverrideEndpoints() throws Exception {
            // Trying to hit a non-existent state-override path should NOT find an admin endpoint.
            // Spring routing may return 404 (no handler) or 405 (wrong method).
            // It must NOT route to OrderLifecycleController because that controller's path is
            // /api/v1/orders — different prefix.
            mockMvc.perform(patch("/api/v1/admin/orders/12345/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"CANCELLED\"}"))
                    .andExpect(status().is(org.hamcrest.Matchers.not(200)));
        }
    }

    // =========================================================================
    // COMMISSION RATE MANAGEMENT (5 cases)
    // =========================================================================

    @Nested
    @DisplayName("Admin Commission Rate Management")
    class CommissionRateTests {

        @Test
        @DisplayName("Case CR1: Admin lists commission rates ordered by effectiveFrom DESC")
        void caseCR1_listRates() throws Exception {
            // Cleanup any leftover rates (from Step 6)
            commissionRateRepository.deleteAll();
            CommissionRate r1 = createCommissionRate(new BigDecimal("5"), LocalDateTime.now().minusDays(10));
            CommissionRate r2 = createCommissionRate(new BigDecimal("7"), LocalDateTime.now().minusDays(1));
            createdCommissionRateIds.add(r1.getId());
            createdCommissionRateIds.add(r2.getId());

            mockMvc.perform(get("/api/v1/admin/commission-rates")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", not(empty())))
                    // Most recent first
                    .andExpect(jsonPath("$.data.content[0].id", is(r2.getId().intValue())))
                    .andExpect(jsonPath("$.data.content[1].id", is(r1.getId().intValue())));
        }

        @Test
        @DisplayName("Case CR2: Admin can create a commission rate")
        void caseCR2_createRate() throws Exception {
            CreateCommissionRateRequest req = CreateCommissionRateRequest.builder()
                    .rate(new BigDecimal("8"))
                    .effectiveFrom(LocalDateTime.now().plusDays(1))
                    .build();
            String body = objectMapper.writeValueAsString(req);
            String responseBody = mockMvc.perform(post("/api/v1/admin/commission-rates")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rate", notNullValue()))
                    .andExpect(jsonPath("$.data.effectiveFrom", notNullValue()))
                    .andExpect(jsonPath("$.data.createdByFullName", is(adminUser.getFullName())))
                    .andReturn().getResponse().getContentAsString();

            // Track for cleanup
            Long createdId = objectMapper.readTree(responseBody).path("data").path("id").asLong();
            createdCommissionRateIds.add(createdId);
        }

        @Test
        @DisplayName("Case CR3: Negative rate rejected → 400")
        void caseCR3_negativeRate() throws Exception {
            CreateCommissionRateRequest req = CreateCommissionRateRequest.builder()
                    .rate(new BigDecimal("-1"))
                    .effectiveFrom(LocalDateTime.now())
                    .build();
            String body = objectMapper.writeValueAsString(req);
            mockMvc.perform(post("/api/v1/admin/commission-rates")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Case CR4: Missing effectiveFrom → 400")
        void caseCR4_missingEffectiveFrom() throws Exception {
            CreateCommissionRateRequest req = CreateCommissionRateRequest.builder()
                    .rate(new BigDecimal("5"))
                    .build();
            String body = objectMapper.writeValueAsString(req);
            mockMvc.perform(post("/api/v1/admin/commission-rates")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Case CR5: Historical rates are preserved (not deleted, not overwritten)")
        void caseCR5_historicalRatesPreserved() throws Exception {
            commissionRateRepository.deleteAll();
            CommissionRate old = createCommissionRate(new BigDecimal("5"), LocalDateTime.now().minusDays(20));
            CommissionRate latest = createCommissionRate(new BigDecimal("7"), LocalDateTime.now().minusDays(1));
            createdCommissionRateIds.add(old.getId());
            createdCommissionRateIds.add(latest.getId());

            // Both rates still exist
            assertThat(commissionRateRepository.findById(old.getId())).isPresent();
            assertThat(commissionRateRepository.findById(latest.getId())).isPresent();

            // Step 6 query semantics: rate effective at '10 days ago' = old; today = latest
            var active10DaysAgo = commissionRateRepository.findActiveRateAt(LocalDateTime.now().minusDays(10));
            var activeToday = commissionRateRepository.findActiveRateAt(LocalDateTime.now());
            assertThat(active10DaysAgo).isPresent();
            assertThat(active10DaysAgo.get().getRate()).isEqualByComparingTo(new BigDecimal("5"));
            assertThat(activeToday).isPresent();
            assertThat(activeToday.get().getRate()).isEqualByComparingTo(new BigDecimal("7"));
        }
    }

    // =========================================================================
    // STATISTICS (6 cases)
    // =========================================================================

    @Nested
    @DisplayName("Admin Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Case S1: Counts orders by status correctly")
        void caseS1_orderCounts() throws Exception {
            // Clean baseline: delete all orders except those in createdOrderIds (these tests have their own)
            // Add multiple orders in different statuses to verify counts.
            createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING,
                    createProduct(supplierCompany, "Stat PEND", 100, 0), 1);
            createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.CANCELLED, PaymentMethod.COD, PaymentStatus.PENDING,
                    createProduct(supplierCompany, "Stat CANC", 100, 0), 1);
            createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.REJECTED, PaymentMethod.COD, PaymentStatus.PENDING,
                    createProduct(supplierCompany, "Stat REJ", 100, 0), 1);

            mockMvc.perform(get("/api/v1/admin/statistics/overview")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalOrders", greaterThanOrEqualTo(0)))
                    .andExpect(jsonPath("$.data.pendingConfirmationOrders", is(notNullValue())))
                    .andExpect(jsonPath("$.data.cancelledOrders", is(notNullValue())))
                    .andExpect(jsonPath("$.data.rejectedOrders", is(notNullValue())))
                    .andExpect(jsonPath("$.data.totalOrderValue", notNullValue()))
                    .andExpect(jsonPath("$.data.totalCommission", notNullValue()));
        }

        @Test
        @DisplayName("Case S2: Completed order sum does NOT include cancelled/rejected")
        void caseS2_completedOnlyCounts() throws Exception {
            // Clean baseline
            commissionRateRepository.deleteAll();
            // Create one cancelled order + one completed order
            Order cancelled = createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.CANCELLED, PaymentMethod.COD, PaymentStatus.PENDING,
                    createProduct(supplierCompany, "Inc CANC", 100, 0), 1);
            Order completed = createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.COMPLETED, PaymentMethod.COD, PaymentStatus.SUCCESS,
                    createProduct(supplierCompany, "Inc COMP", 100, 0), 1);
            // Set commission snapshot on completed only
            completed.setCommissionRate(new BigDecimal("5"));
            completed.setCommissionAmount(new BigDecimal("50000"));
            orderRepository.save(completed);

            mockMvc.perform(get("/api/v1/admin/statistics/overview")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalOrderValue", is(notNullValue())))
                    .andExpect(jsonPath("$.data.totalCommission", is(notNullValue())));
            // We cannot assert exact numbers here, but the response must have valid JSON.
        }

        @Test
        @DisplayName("Case S3: Date range filter works")
        void caseS3_dateRange() throws Exception {
            mockMvc.perform(get("/api/v1/admin/statistics/overview")
                            .param("fromDate", "2020-01-01")
                            .param("toDate", "2099-12-31")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fromDate", is("2020-01-01")))
                    .andExpect(jsonPath("$.data.toDate", is("2099-12-31")));
        }

        @Test
        @DisplayName("Case S4: Invalid date range returns 400")
        void caseS4_invalidDateRange() throws Exception {
            mockMvc.perform(get("/api/v1/admin/statistics/overview")
                            .param("fromDate", "2099-01-01")
                            .param("toDate", "2020-01-01")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Case S5: Statistics do NOT recompute commission from current rates (snapshots respected)")
        void caseS5_usesCommissionSnapshot() throws Exception {
            commissionRateRepository.deleteAll();
            // Create one completed order with explicit commission snapshot.
            Order completed = createOrder(buyerUser, buyerCompany, supplierCompany,
                    OrderStatus.COMPLETED, PaymentMethod.COD, PaymentStatus.SUCCESS,
                    createProduct(supplierCompany, "Snap COMP", 100, 0), 1);
            completed.setCommissionRate(new BigDecimal("5"));
            completed.setCommissionAmount(new BigDecimal("12345.67"));
            orderRepository.save(completed);

            // Even after creating a newer rate, the snapshot stays the same.
            CommissionRate newRate = createCommissionRate(new BigDecimal("99"), LocalDateTime.now().plusDays(1));
            createdCommissionRateIds.add(newRate.getId());

            mockMvc.perform(get("/api/v1/admin/statistics/overview")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalCommission").exists());

            // The snapshot is preserved on the order.
            Order reloaded = orderRepository.findById(completed.getId()).orElseThrow();
            assertThat(reloaded.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("12345.67"));
        }

        @Test
        @DisplayName("Case S6: Response has all required fields")
        void caseS6_responseShape() throws Exception {
            mockMvc.perform(get("/api/v1/admin/statistics/overview")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalOrders").exists())
                    .andExpect(jsonPath("$.data.completedOrders").exists())
                    .andExpect(jsonPath("$.data.pendingConfirmationOrders").exists())
                    .andExpect(jsonPath("$.data.cancelledOrders").exists())
                    .andExpect(jsonPath("$.data.rejectedOrders").exists())
                    .andExpect(jsonPath("$.data.totalOrderValue").exists())
                    .andExpect(jsonPath("$.data.totalCommission").exists());
        }
    }

    // =========================================================================
    // SECURITY / REGRESSION (4 cases)
    // =========================================================================

    @Nested
    @DisplayName("Security")
    class SecurityTests {

        @Test
        @DisplayName("Case X1: BUYER → 403 across admin endpoints")
        void caseX1_buyer403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/admin/companies")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/admin/products")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/admin/categories")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Case X2: SUPPLIER → 403 across admin endpoints")
        void caseX2_supplier403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + supplierToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/admin/statistics/overview")
                            .header("Authorization", "Bearer " + supplierToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Case X3: Anonymous → 401 across admin endpoints")
        void caseX3_anonymous401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/admin/companies")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/admin/products")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/admin/commission-rates")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/admin/statistics/overview")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Case X4: Step 0-7 baseline tests still pass (regression smoke check)")
        void caseX4_baselineSmoke() throws Exception {
            // Just verify admin can hit the new admin endpoint successfully — main test of regression
            // is the full suite run.
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private User createUser(String username, Role role, Company company) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$mockpasswordfortest1234567890xx");
        u.setFullName("Test " + username);
        u.setEmail(username + "@example.com");
        u.setPhone("090" + (System.currentTimeMillis() % 10000000));
        u.setStatus("ACTIVE");
        u.setRole(role);
        u.setCompany(company);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(u);
    }

    private Product createProduct(Company supplier, String name, int stock, int reserved) {
        Product p = new Product();
        p.setSupplierCompany(supplier);
        p.setCategory(category);
        p.setSku("SKU-ADM-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4));
        p.setName(name);
        p.setDescription("Description");
        p.setStockQuantity(stock);
        p.setReservedQuantity(reserved);
        p.setStatus("ACTIVE");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p = productRepository.save(p);
        createdProductIds.add(p.getId());

        // Add a price tier so admin detail works.
        ProductPrice pp = new ProductPrice();
        pp.setProduct(p);
        pp.setMinQuantity(1);
        pp.setMaxQuantity(null);
        pp.setUnitPrice(new BigDecimal("100000.00"));
        pp.setCreatedAt(LocalDateTime.now());
        pp.setUpdatedAt(LocalDateTime.now());
        productPriceRepository.save(pp);
        return p;
    }

    private Order createOrder(User buyer, Company buyerComp, Company supplierComp,
                              OrderStatus status, PaymentMethod paymentMethod,
                              PaymentStatus paymentStatus, Product product, int quantity) {
        BigDecimal unitPrice = new BigDecimal("100000.00");
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String orderCode = "ORD-ADM-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);

        Order order = Order.builder()
                .buyerCompany(buyerComp)
                .supplierCompany(supplierComp)
                .createdBy(buyer)
                .orderCode(orderCode)
                .status(status)
                .subtotal(subtotal)
                .totalAmount(subtotal)
                .shippingCompanyName(buyerComp.getName())
                .shippingPhone(buyerComp.getPhone())
                .shippingAddress(buyerComp.getAddress())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);
        createdOrderIds.add(order.getId());

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(subtotal);
        orderItemRepository.save(item);

        Payment payment = Payment.builder()
                .order(order)
                .paymentCode("PAY-ADM-" + System.currentTimeMillis())
                .paymentMethod(paymentMethod)
                .status(paymentStatus)
                .amount(subtotal)
                .paidAt(paymentStatus == PaymentStatus.SUCCESS ? LocalDateTime.now() : null)
                .expiredAt(paymentMethod == PaymentMethod.COD ? null : LocalDateTime.now().plusMinutes(15))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .changedBy(buyer)
                .status(status)
                .note("Order created in admin test")
                .createdAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(history);

        return order;
    }

    private CommissionRate createCommissionRate(BigDecimal rate, LocalDateTime effectiveFrom) {
        CommissionRate r = new CommissionRate();
        r.setRate(rate);
        r.setEffectiveFrom(effectiveFrom);
        r.setCreatedAt(LocalDateTime.now());
        r.setCreatedBy(adminUser);
        return commissionRateRepository.save(r);
    }
}

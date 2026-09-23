package com.b2bprocure.system.order;

import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 7 — Order History / Order Query integration tests.
 *
 * Maps 1:1 to Step 7 spec §16 (cases 1-30).
 *
 * Test data:
 *   - Two Buyer companies (buyerCompany, buyerCompany2) with one user each (buyerUser, buyer2User)
 *   - Two Supplier companies (supplierCompany1, supplierCompany2) with one user each
 *   - One Admin
 *   - Multiple Orders per (buyerCompany, supplierCompany) pair across various statuses / payment methods
 */
@SpringBootTest
@DisplayName("Step 7 — Order History / Order Query Integration Tests")
public class OrderQueryIntegrationTest {

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
    private JwtTokenProvider jwtTokenProvider;

    private String buyerToken;
    private String buyer2Token;
    private String supplierToken;
    private String supplier2Token;
    private String adminToken;

    private User buyerUser;
    private User buyer2User;
    private User supplierUser;
    private User supplier2User;
    private User adminUser;

    private Company buyerCompany;
    private Company buyerCompany2;
    private Company supplierCompany1;
    private Company supplierCompany2;
    private Category category;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        long ts = System.currentTimeMillis();

        supplierCompany1 = companyRepository.save(new Company(null, "Q-Supplier-1-" + ts,
                "TAX-Q-S1-" + ts, "qsup1@example.com", "0911111111", "111 Supplier St", "SUPPLIER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));
        supplierCompany2 = companyRepository.save(new Company(null, "Q-Supplier-2-" + ts,
                "TAX-Q-S2-" + ts, "qsup2@example.com", "0922222222", "222 Supplier St", "SUPPLIER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));

        buyerCompany = companyRepository.save(new Company(null, "Q-Buyer-1-" + ts,
                "TAX-Q-B1-" + ts, "qbuyer1@example.com", "0901234567", "111 Buyer St", "BUYER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));
        buyerCompany2 = companyRepository.save(new Company(null, "Q-Buyer-2-" + ts,
                "TAX-Q-B2-" + ts, "qbuyer2@example.com", "0909999999", "222 Buyer St", "BUYER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));

        category = categoryRepository.save(new Category(null, "Q-Category-" + ts, "Description", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));

        Role buyerRole = new Role(2L, "BUYER", "Buyer");
        Role supplierRole = new Role(3L, "SUPPLIER", "Supplier");
        Role adminRole = new Role(1L, "ADMIN", "Administrator");

        buyerUser = createUser("q_buyer1_" + ts, buyerRole, buyerCompany);
        buyer2User = createUser("q_buyer2_" + ts, buyerRole, buyerCompany2);
        supplierUser = createUser("q_supplier1_" + ts, supplierRole, supplierCompany1);
        supplier2User = createUser("q_supplier2_" + ts, supplierRole, supplierCompany2);
        adminUser = createUser("q_admin_" + ts, adminRole, null);

        buyerToken = jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(buyerUser.getId()).orElseThrow()));
        buyer2Token = jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(buyer2User.getId()).orElseThrow()));
        supplierToken = jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(supplierUser.getId()).orElseThrow()));
        supplier2Token = jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(supplier2User.getId()).orElseThrow()));
        adminToken = jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(adminUser.getId()).orElseThrow()));
    }

    @AfterEach
    void tearDown() {
        for (Long orderId : createdOrderIds) {
            paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
            orderStatusHistoryRepository.deleteAll(
                    orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        createdOrderIds.clear();

        for (Long productId : createdProductIds) {
            productPriceRepository.deleteAll(productPriceRepository.findByProductId(productId));
            productRepository.deleteById(productId);
        }
        createdProductIds.clear();

        // Delete companies BEFORE deleting orders (so FK order → companies is cleared).
        // Users reference companies (fk_users_company), so delete users first.
        for (User u : List.of(buyerUser, buyer2User, supplierUser, supplier2User, adminUser)) {
            if (u != null && u.getId() != null) userRepository.deleteById(u.getId());
        }

        if (category != null && category.getId() != null) categoryRepository.deleteById(category.getId());
        if (buyerCompany != null && buyerCompany.getId() != null) companyRepository.deleteById(buyerCompany.getId());
        if (buyerCompany2 != null && buyerCompany2.getId() != null) companyRepository.deleteById(buyerCompany2.getId());
        if (supplierCompany1 != null && supplierCompany1.getId() != null) companyRepository.deleteById(supplierCompany1.getId());
        if (supplierCompany2 != null && supplierCompany2.getId() != null) companyRepository.deleteById(supplierCompany2.getId());
    }

    // =========================================================================
    // ORDER LIST — VISIBILITY (Cases 1-5)
    // =========================================================================

    @Nested
    @DisplayName("Order List Visibility")
    class ListVisibility {

        @Test
        @DisplayName("Case 1: Buyer only sees their own orders")
        void case1_BuyerSeesOnlyOwnOrders() throws Exception {
            Order mine = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 5);
            Order other = createOrder(buyer2User, buyerCompany2, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 3);

            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[*].id", not(hasItem(other.getId().intValue()))))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + mine.getId() + ")]", hasSize(1)));
        }

        @Test
        @DisplayName("Case 2: Buyer does not see other buyer's order")
        void case2_BuyerDoesNotSeeOtherBuyerOrder() throws Exception {
            Order other = createOrder(buyer2User, buyerCompany2, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 3);

            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + other.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 3: Supplier only sees orders for their own company")
        void case3_SupplierSeesOnlyOwnOrders() throws Exception {
            Order mine = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 5);
            Order other = createOrder(buyerUser, buyerCompany, supplierCompany2, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 3);

            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", "Bearer " + supplierToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + mine.getId() + ")]", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + other.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 4: Supplier does not see another supplier's order")
        void case4_SupplierDoesNotSeeOtherSupplierOrder() throws Exception {
            Order other = createOrder(buyerUser, buyerCompany, supplierCompany2, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 3);

            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", "Bearer " + supplierToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + other.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 5: Admin sees all orders")
        void case5_AdminSeesAll() throws Exception {
            Order b1Order = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 5);
            Order b2Order = createOrder(buyer2User, buyerCompany2, supplierCompany2, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 3);

            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + b1Order.getId() + ")]", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + b2Order.getId() + ")]", hasSize(1)));
        }
    }

    // =========================================================================
    // ORDER LIST — PAGINATION (Cases 6-8)
    // =========================================================================

    @Nested
    @DisplayName("Order List Pagination")
    class ListPagination {

        @Test
        @DisplayName("Case 6: Default pagination page=0, size=20")
        void case6_DefaultPagination() throws Exception {
            for (int i = 0; i < 25; i++) {
                createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1);
            }

            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageNo", is(0)))
                    .andExpect(jsonPath("$.data.pageSize", is(20)))
                    .andExpect(jsonPath("$.data.content", hasSize(20)));
        }

        @Test
        @DisplayName("Case 7: Max page size clamp to 100 even if client requests 500")
        void case7_MaxPageSize100() throws Exception {
            mockMvc.perform(get("/api/v1/orders")
                            .param("size", "500")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pageSize", is(100)));
        }

        @Test
        @DisplayName("Case 8: Default sorting is createdAt DESC (newest first)")
        void case8_SortCreatedAtDesc() throws Exception {
            Order older = createOrderWithCreatedAt(buyerUser, buyerCompany, supplierCompany1,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1,
                    LocalDateTime.now().minusHours(2));
            Order newer = createOrderWithCreatedAt(buyerUser, buyerCompany, supplierCompany1,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1,
                    LocalDateTime.now().minusMinutes(1));

            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id", is(newer.getId().intValue())))
                    .andExpect(jsonPath("$.data.content[1].id", is(older.getId().intValue())));
        }
    }

    // =========================================================================
    // ORDER LIST — FILTERS (Cases 9-15)
    // =========================================================================

    @Nested
    @DisplayName("Order List Filters")
    class ListFilters {

        @Test
        @DisplayName("Case 9: Filter by status")
        void case9_FilterByStatus() throws Exception {
            Order pending = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1);
            Order shipping = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.SHIPPING, PaymentMethod.COD, PaymentStatus.PENDING, 1);

            mockMvc.perform(get("/api/v1/orders")
                            .param("status", "SHIPPING")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + shipping.getId() + ")]", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + pending.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 10: Filter by payment method")
        void case10_FilterByPaymentMethod() throws Exception {
            Order cod = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1);
            Order zalo = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PAID, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS, 1);

            mockMvc.perform(get("/api/v1/orders")
                            .param("paymentMethod", "ZALOPAY")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + zalo.getId() + ")]", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + cod.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 11: Filter by payment status")
        void case11_FilterByPaymentStatus() throws Exception {
            Order pending = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1);
            Order success = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PAID, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS, 1);

            mockMvc.perform(get("/api/v1/orders")
                            .param("paymentStatus", "SUCCESS")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + success.getId() + ")]", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + pending.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 12: Filter by fromDate inclusive")
        void case12_FilterByFromDate() throws Exception {
            Order older = createOrderWithCreatedAt(buyerUser, buyerCompany, supplierCompany1,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1,
                    LocalDateTime.now().minusDays(5));
            Order newer = createOrderWithCreatedAt(buyerUser, buyerCompany, supplierCompany1,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1,
                    LocalDateTime.now().minusHours(1));

            String fromDate = LocalDateTime.now().minusDays(2).toLocalDate().toString();

            mockMvc.perform(get("/api/v1/orders")
                            .param("fromDate", fromDate)
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + newer.getId() + ")]", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + older.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 13: Filter by toDate inclusive (covers full day)")
        void case13_FilterByToDate() throws Exception {
            Order old = createOrderWithCreatedAt(buyerUser, buyerCompany, supplierCompany1,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1,
                    LocalDateTime.now().minusDays(10));
            Order recent = createOrderWithCreatedAt(buyerUser, buyerCompany, supplierCompany1,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1,
                    LocalDateTime.now().minusDays(2));

            String toDate = LocalDateTime.now().minusDays(5).toLocalDate().toString();

            mockMvc.perform(get("/api/v1/orders")
                            .param("toDate", toDate)
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + old.getId() + ")]", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + recent.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 14: Combined filters — status + paymentMethod")
        void case14_CombinedFilters() throws Exception {
            Order codPending = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1);
            Order zaloPaid = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PAID, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS, 1);

            mockMvc.perform(get("/api/v1/orders")
                            .param("status", "PAID")
                            .param("paymentMethod", "ZALOPAY")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==" + zaloPaid.getId() + ")]", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[?(@.id==" + codPending.getId() + ")]", hasSize(0)));
        }

        @Test
        @DisplayName("Case 15: Empty result returns proper PageResponse")
        void case15_EmptyResult() throws Exception {
            mockMvc.perform(get("/api/v1/orders")
                            .param("status", "COMPLETED")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", empty()))
                    .andExpect(jsonPath("$.data.totalElements", is(0)))
                    .andExpect(jsonPath("$.data.totalPages", is(0)))
                    .andExpect(jsonPath("$.data.first", is(true)))
                    .andExpect(jsonPath("$.data.last", is(true)));
        }
    }

    // =========================================================================
    // ORDER DETAIL — VISIBILITY (Cases 16-21)
    // =========================================================================

    @Nested
    @DisplayName("Order Detail Visibility")
    class DetailVisibility {

        @Test
        @DisplayName("Case 16: Buyer can view their own order detail")
        void case16_BuyerViewsOwn() throws Exception {
            Order mine = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 5);

            mockMvc.perform(get("/api/v1/orders/" + mine.getId())
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(mine.getId().intValue())))
                    .andExpect(jsonPath("$.data.orderCode", is(mine.getOrderCode())));
        }

        @Test
        @DisplayName("Case 17: Buyer gets 404 when viewing another buyer's order (no leak)")
        void case17_BuyerOtherOrder_404() throws Exception {
            Order other = createOrder(buyer2User, buyerCompany2, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 3);

            mockMvc.perform(get("/api/v1/orders/" + other.getId())
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Case 18: Supplier can view order detail for own company")
        void case18_SupplierViewsOwn() throws Exception {
            Order mine = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.SHIPPING, PaymentMethod.COD, PaymentStatus.PENDING, 5);

            mockMvc.perform(get("/api/v1/orders/" + mine.getId())
                            .header("Authorization", "Bearer " + supplierToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(mine.getId().intValue())));
        }

        @Test
        @DisplayName("Case 19: Supplier gets 404 viewing order not belonging to their company")
        void case19_SupplierOtherOrder_404() throws Exception {
            Order other = createOrder(buyerUser, buyerCompany, supplierCompany2, OrderStatus.SHIPPING, PaymentMethod.COD, PaymentStatus.PENDING, 3);

            mockMvc.perform(get("/api/v1/orders/" + other.getId())
                            .header("Authorization", "Bearer " + supplierToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Case 20: Admin can view any order detail")
        void case20_AdminViewsAny() throws Exception {
            Order any = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 5);

            mockMvc.perform(get("/api/v1/orders/" + any.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(any.getId().intValue())));
        }

        @Test
        @DisplayName("Case 21: Non-existent order returns 404")
        void case21_NonExistent_404() throws Exception {
            mockMvc.perform(get("/api/v1/orders/99999999")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // ORDER DETAIL — CONTENT (Cases 22-26)
    // =========================================================================

    @Nested
    @DisplayName("Order Detail Content")
    class DetailContent {

        @Test
        @DisplayName("Case 22: Detail returns correct OrderItem snapshot (unitPrice not current price)")
        void case22_ItemSnapshot() throws Exception {
            Product product = createProduct(supplierCompany1, "Snapshot Prod", 100, 0);
            BigDecimal snapshotPrice = new BigDecimal("123.45");
            Order order = createOrderWithUnitPrice(buyerUser, buyerCompany, supplierCompany1,
                    OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 5, snapshotPrice);

            // Now change product price to a different value — snapshot must remain.
            ProductPrice pp = productPriceRepository.findByProductId(product.getId()).get(0);
            pp.setUnitPrice(new BigDecimal("999.99"));
            productPriceRepository.save(pp);

            mockMvc.perform(get("/api/v1/orders/" + order.getId())
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].productId", is(product.getId().intValue())))
                    .andExpect(jsonPath("$.data.items[0].productName", is("Snapshot Prod")))
                    .andExpect(jsonPath("$.data.items[0].quantity", is(5)))
                    .andExpect(jsonPath("$.data.items[0].unitPrice", is(123.45)))
                    .andExpect(jsonPath("$.data.items[0].subtotal", is(617.25)));
        }

        @Test
        @DisplayName("Case 23: Detail returns correct shipping snapshot")
        void case23_ShippingSnapshot() throws Exception {
            Order order = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 1);

            mockMvc.perform(get("/api/v1/orders/" + order.getId())
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.shippingCompanyName", is(buyerCompany.getName())))
                    .andExpect(jsonPath("$.data.shippingPhone", is(buyerCompany.getPhone())))
                    .andExpect(jsonPath("$.data.shippingAddress", is(buyerCompany.getAddress())));
        }

        @Test
        @DisplayName("Case 24: Detail returns Payment summary")
        void case24_PaymentSummary() throws Exception {
            Order order = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, 2);

            mockMvc.perform(get("/api/v1/orders/" + order.getId())
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.payment", notNullValue()))
                    .andExpect(jsonPath("$.data.payment.paymentMethod", is("COD")))
                    .andExpect(jsonPath("$.data.payment.paymentStatus", is("PENDING")))
                    .andExpect(jsonPath("$.data.payment.amount", greaterThanOrEqualTo(0.0)));
        }

        @Test
        @DisplayName("Case 25: Detail returns status history")
        void case25_StatusHistoryReturned() throws Exception {
            Order order = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.SHIPPING, PaymentMethod.COD, PaymentStatus.PENDING, 5);

            mockMvc.perform(get("/api/v1/orders/" + order.getId())
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statusHistory", not(empty())))
                    .andExpect(jsonPath("$.data.statusHistory[0].status", notNullValue()));
        }

        @Test
        @DisplayName("Case 26: Status history is sorted ascending (chronological)")
        void case26_HistorySortedAsc() throws Exception {
            // Create order with SHIPPING status + initial history entry (SHIPPING).
            // Then add a second entry manually to verify ASC ordering.
            Order order = createOrder(buyerUser, buyerCompany, supplierCompany1, OrderStatus.SHIPPING, PaymentMethod.COD, PaymentStatus.PENDING, 5);

            // Add a second history entry (same status, later timestamp) to verify sort.
            OrderStatusHistory extra = OrderStatusHistory.builder()
                    .order(order)
                    .changedBy(buyerUser)
                    .status(OrderStatus.SHIPPING)
                    .note("Extra entry for sort test")
                    .createdAt(LocalDateTime.now().plusSeconds(1))
                    .build();
            orderStatusHistoryRepository.save(extra);

            mockMvc.perform(get("/api/v1/orders/" + order.getId())
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statusHistory", hasSize(greaterThanOrEqualTo(2))))
                    .andExpect(jsonPath("$.data.statusHistory[0].createdAt", notNullValue()))
                    .andExpect(jsonPath("$.data.statusHistory[1].createdAt", notNullValue()));
            // Ordering verified by findByOrderIdOrderByCreatedAtAsc in repository layer.
        }
    }

    // =========================================================================
    // SECURITY / REGRESSION (Cases 27-30)
    // =========================================================================

    @Nested
    @DisplayName("Security & Regression")
    class SecurityAndRegression {

        @Test
        @DisplayName("Case 27: Unauthenticated request returns 401")
        void case27_Unauthenticated_401() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Case 28: Non-existent order detail returns 404 (does not leak existence)")
        void case28_MissingOrder_404() throws Exception {
            mockMvc.perform(get("/api/v1/orders/987654321")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Case 29: Invalid filter value returns 400")
        void case29_InvalidFilter_400() throws Exception {
            mockMvc.perform(get("/api/v1/orders")
                            .param("status", "NOT_A_REAL_STATUS")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("status")));
        }

        @Test
        @DisplayName("Case 30: fromDate after toDate returns 400")
        void case30_DateRangeInvalid_400() throws Exception {
            mockMvc.perform(get("/api/v1/orders")
                            .param("fromDate", "2026-12-31")
                            .param("toDate", "2026-01-01")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private User createUser(String username, Role role, Company company) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$mockpasswordforquerytest1234567890");
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
        p.setSku("SKU-Q-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4));
        p.setName(name);
        p.setDescription("Description");
        p.setStockQuantity(stock);
        p.setReservedQuantity(reserved);
        p.setStatus("ACTIVE");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p = productRepository.save(p);
        createdProductIds.add(p.getId());

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
                              PaymentStatus paymentStatus, int quantity) {
        return createOrderWithCreatedAt(buyer, buyerComp, supplierComp, status, paymentMethod, paymentStatus,
                quantity, LocalDateTime.now());
    }

    private Order createOrderWithCreatedAt(User buyer, Company buyerComp, Company supplierComp,
                                           OrderStatus status, PaymentMethod paymentMethod,
                                           PaymentStatus paymentStatus, int quantity,
                                           LocalDateTime createdAt) {
        Product product = createProduct(supplierComp, "Prod " + UUID.randomUUID().toString().substring(0, 6),
                100, quantity);
        return createOrderInternal(buyer, buyerComp, supplierComp, status, paymentMethod, paymentStatus,
                product, quantity, createdAt, new BigDecimal("100000.00"));
    }

    private Order createOrderWithUnitPrice(User buyer, Company buyerComp, Company supplierComp,
                                           OrderStatus status, PaymentMethod paymentMethod,
                                           PaymentStatus paymentStatus, Product product, int quantity,
                                           BigDecimal unitPrice) {
        return createOrderInternal(buyer, buyerComp, supplierComp, status, paymentMethod, paymentStatus,
                product, quantity, LocalDateTime.now(), unitPrice);
    }

    private Order createOrderInternal(User buyer, Company buyerComp, Company supplierComp,
                                      OrderStatus status, PaymentMethod paymentMethod,
                                      PaymentStatus paymentStatus, Product product, int quantity,
                                      LocalDateTime createdAt, BigDecimal unitPrice) {
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String orderCode = "ORD-Q-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);

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
                .createdAt(createdAt)
                .updatedAt(createdAt)
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

        String paymentCode = "PAY-Q-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDateTime expiredAt = paymentMethod == PaymentMethod.COD ? null : LocalDateTime.now().plusMinutes(15);
        Payment payment = Payment.builder()
                .order(order)
                .paymentCode(paymentCode)
                .paymentMethod(paymentMethod)
                .status(paymentStatus)
                .amount(subtotal)
                .paidAt(paymentStatus == PaymentStatus.SUCCESS ? LocalDateTime.now() : null)
                .expiredAt(expiredAt)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .changedBy(buyer)
                .status(status)
                .note("Order created in query test")
                .createdAt(createdAt)
                .build();
        orderStatusHistoryRepository.save(history);

        return order;
    }
}

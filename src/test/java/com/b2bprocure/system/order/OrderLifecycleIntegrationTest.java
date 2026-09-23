package com.b2bprocure.system.order;

import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.commission.entity.CommissionRate;
import com.b2bprocure.system.commission.repository.CommissionRateRepository;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.order.dto.CancelOrderRequest;
import com.b2bprocure.system.order.dto.RejectOrderRequest;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("Order Lifecycle Integration Tests")
public class OrderLifecycleIntegrationTest {

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
    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        long ts = System.currentTimeMillis();

        // 1. Setup Companies
        supplierCompany1 = companyRepository.save(new Company(null, "Supplier Co 1", "TAX-LC-S1-" + ts,
                "sup1@example.com", "0911111111", "111 Supplier St, HCM", "SUPPLIER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));
        supplierCompany2 = companyRepository.save(new Company(null, "Supplier Co 2", "TAX-LC-S2-" + ts,
                "sup2@example.com", "0922222222", "222 Supplier St, HCM", "SUPPLIER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));

        buyerCompany = companyRepository.save(new Company(null, "Buyer Co 1", "TAX-LC-B1-" + ts,
                "buyer1@example.com", "0901234567", "123 Buyer St, HN", "BUYER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));
        buyerCompany2 = companyRepository.save(new Company(null, "Buyer Co 2", "TAX-LC-B2-" + ts,
                "buyer2@example.com", "0909999999", "999 Buyer St, HN", "BUYER", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));

        // 2. Setup Category
        category = categoryRepository.save(new Category(null, "LC Category " + ts, "Description", "ACTIVE", LocalDateTime.now(), LocalDateTime.now()));

        // 3. Setup Users
        Role buyerRole = new Role(2L, "BUYER", "Buyer");
        Role supplierRole = new Role(3L, "SUPPLIER", "Supplier");
        Role adminRole = new Role(1L, "ADMIN", "Administrator");

        buyerUser = createOrGetUser("lc_buyer1_" + ts, buyerRole, buyerCompany);
        buyer2User = createOrGetUser("lc_buyer2_" + ts, buyerRole, buyerCompany2);
        supplierUser = createOrGetUser("lc_supplier1_" + ts, supplierRole, supplierCompany1);
        supplier2User = createOrGetUser("lc_supplier2_" + ts, supplierRole, supplierCompany2);
        adminUser = createOrGetUser("lc_admin_" + ts, adminRole, null);

        // 4. Generate Tokens
        buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(buyerUser.getId()).orElseThrow()));
        buyer2Token = jwtTokenProvider.generateToken(UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(buyer2User.getId()).orElseThrow()));
        supplierToken = jwtTokenProvider.generateToken(UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(supplierUser.getId()).orElseThrow()));
        supplier2Token = jwtTokenProvider.generateToken(UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(supplier2User.getId()).orElseThrow()));
        adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(adminUser.getId()).orElseThrow()));
    }

    @AfterEach
    void tearDown() {
        // Clean up commission rates first (FK dependency)
        commissionRateRepository.deleteAll();

        for (Long orderId : createdOrderIds) {
            paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        createdOrderIds.clear();

        for (Long productId : createdProductIds) {
            productPriceRepository.deleteAll(productPriceRepository.findByProductId(productId));
            productRepository.deleteById(productId);
        }
        createdProductIds.clear();

        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();

        if (category != null && category.getId() != null) {
            categoryRepository.deleteById(category.getId());
        }
        if (buyerCompany != null && buyerCompany.getId() != null) companyRepository.deleteById(buyerCompany.getId());
        if (buyerCompany2 != null && buyerCompany2.getId() != null) companyRepository.deleteById(buyerCompany2.getId());
        if (supplierCompany1 != null && supplierCompany1.getId() != null) companyRepository.deleteById(supplierCompany1.getId());
        if (supplierCompany2 != null && supplierCompany2.getId() != null) companyRepository.deleteById(supplierCompany2.getId());
    }

    // =========================================================================
    // SECTION 1: SUPPLIER CONFIRM TESTS
    // =========================================================================

    @Test
    @DisplayName("Case 1: COD order in PENDING_CONFIRMATION confirmed successfully -> stock & reservation decremented, payment stays PENDING")
    void testConfirm_COD_Success() throws Exception {
        Product product = createProduct(supplierCompany1, "Confirm COD Prod", 100, 20);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 20);

        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/confirm")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        // Verify Order
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Verify Payment (COD stays PENDING)
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // Verify Product: stock and reserved decremented by 20
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(80);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(0);
        assertThat(updatedProduct.getAvailableQuantity()).isEqualTo(80);

        // Verify History
        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertThat(histories).hasSize(2);
        assertThat(histories.get(1).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(histories.get(1).getChangedBy().getId()).isEqualTo(supplierUser.getId());
    }

    @Test
    @DisplayName("Case 2: Online order in PAID status confirmed successfully -> stock & reservation decremented, payment stays SUCCESS")
    void testConfirm_OnlinePaid_Success() throws Exception {
        Product product = createProduct(supplierCompany1, "Confirm Online Prod", 50, 10);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PAID, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS, product, 10);

        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/confirm")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(40);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Case 3: Online order in PENDING_CONFIRMATION (unpaid) cannot be confirmed by Supplier -> 400 Bad Request")
    void testConfirm_OnlineUnpaid_Rejected() throws Exception {
        Product product = createProduct(supplierCompany1, "Unpaid Online Prod", 50, 10);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.ZALOPAY, PaymentStatus.PENDING, product, 10);

        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/confirm")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Online order cannot be confirmed until payment is successful")));

        Product p = productRepository.findById(product.getId()).orElseThrow();
        assertThat(p.getStockQuantity()).isEqualTo(50);
        assertThat(p.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Case 4: Supplier does not own the order -> 403 Forbidden")
    void testConfirm_WrongSupplier_Forbidden() throws Exception {
        Product product = createProduct(supplierCompany1, "Supplier 1 Prod", 50, 10);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 10);

        // Supplier 2 attempts to confirm Supplier 1's order
        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/confirm")
                        .header("Authorization", "Bearer " + supplier2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Case 5: Order in CANCELLED status cannot be confirmed -> 400 Bad Request")
    void testConfirm_InvalidStatus_Rejected() throws Exception {
        Product product = createProduct(supplierCompany1, "Cancelled Prod", 50, 0);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.CANCELLED, PaymentMethod.COD, PaymentStatus.PENDING, product, 10);

        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/confirm")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be confirmed from status CANCELLED")));
    }

    @Test
    @DisplayName("Case 6: Confirm when stock equals reserved (e.g. 15 == 15) decrements safely to 0 without violating check constraints")
    void testConfirm_CheckConstraintSafety_WhenStockEqualsReserved() throws Exception {
        Product product = createProduct(supplierCompany1, "Boundary Check Prod", 15, 15);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 15);

        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/confirm")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk());

        Product p = productRepository.findById(product.getId()).orElseThrow();
        assertThat(p.getStockQuantity()).isEqualTo(0);
        assertThat(p.getReservedQuantity()).isEqualTo(0);
        assertThat(p.getAvailableQuantity()).isEqualTo(0);
    }

    // =========================================================================
    // SECTION 2: SUPPLIER REJECT TESTS
    // =========================================================================

    @Test
    @DisplayName("Case 7: Supplier rejects COD order -> REJECTED, reservation released immediately, payment stays PENDING")
    void testReject_COD_Success_ReleasesReservation() throws Exception {
        Product product = createProduct(supplierCompany1, "Reject COD Prod", 100, 10);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 10);

        RejectOrderRequest request = new RejectOrderRequest("Hết hàng trong kho");

        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/reject")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.REJECTED);

        // Reservation released immediately
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(100);
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(0);

        // Payment stays PENDING
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // History contains reason
        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertThat(histories.get(histories.size() - 1).getNote()).isEqualTo("Hết hàng trong kho");
    }

    @Test
    @DisplayName("Case 8: Supplier rejects Online PAID order -> REJECTED, payment becomes REFUND_PENDING, reservation RETAINED")
    void testReject_OnlinePaid_RefundPending_RetainsReservation() throws Exception {
        Product product = createProduct(supplierCompany1, "Reject Online Paid Prod", 100, 10);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PAID, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS, product, 10);

        RejectOrderRequest request = new RejectOrderRequest("Không thể vận chuyển đến khu vực này");

        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/reject")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));

        // Payment must be REFUND_PENDING
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        // Reservation MUST be retained (NOT released until refund completes)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getReservedQuantity()).isEqualTo(10);
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Case 9: Reject with null or blank reason is rejected -> 400 Bad Request")
    void testReject_ReasonMissingOrBlank_BadRequest() throws Exception {
        Product product = createProduct(supplierCompany1, "Blank Reason Prod", 50, 5);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 5);

        // Blank reason
        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/reject")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectOrderRequest("   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Case 10: Supplier cannot reject an already CONFIRMED order -> 400 Bad Request")
    void testReject_AfterConfirmed_Rejected() throws Exception {
        Product product = createProduct(supplierCompany1, "Confirmed Reject Prod", 50, 0);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.CONFIRMED, PaymentMethod.COD, PaymentStatus.PENDING, product, 5);

        mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/reject")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectOrderRequest("Muốn từ chối"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be rejected. Rejection is only allowed prior to confirmation")));
    }

    // =========================================================================
    // SECTION 3: BUYER CANCEL TESTS
    // =========================================================================

    @Test
    @DisplayName("Case 11: Buyer cancels COD order -> CANCELLED, reservation released immediately")
    void testCancel_COD_Success_ReleasesReservation() throws Exception {
        Product product = createProduct(supplierCompany1, "Cancel COD Prod", 100, 10);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 10);

        CancelOrderRequest request = new CancelOrderRequest("Đổi ý không mua nữa");

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Product p = productRepository.findById(product.getId()).orElseThrow();
        assertThat(p.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Case 12: Buyer cancels Online PAID order -> CANCELLED, payment becomes REFUND_PENDING, reservation RETAINED")
    void testCancel_OnlinePaid_RefundPending_RetainsReservation() throws Exception {
        Product product = createProduct(supplierCompany1, "Cancel Online Paid Prod", 100, 10);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PAID, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS, product, 10);

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        // Reservation retained
        Product p = productRepository.findById(product.getId()).orElseThrow();
        assertThat(p.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Case 13: Buyer cannot cancel order belonging to another buyer -> 403 Forbidden")
    void testCancel_WrongBuyer_Forbidden() throws Exception {
        Product product = createProduct(supplierCompany1, "Buyer 1 Prod", 50, 5);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 5);

        // Buyer 2 attempts to cancel Buyer 1's order
        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel")
                        .header("Authorization", "Bearer " + buyer2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Case 14: Buyer cannot cancel an already CONFIRMED order -> 400 Bad Request")
    void testCancel_AfterConfirmed_Rejected() throws Exception {
        Product product = createProduct(supplierCompany1, "Confirmed Cancel Prod", 50, 0);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.CONFIRMED, PaymentMethod.COD, PaymentStatus.PENDING, product, 5);

        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be cancelled")));
    }

    // =========================================================================
    // SECTION 4: FULFILLMENT LIFECYCLE TESTS
    // =========================================================================

    @Test
    @DisplayName("Case 15: Full Fulfillment Cycle: CONFIRMED -> PREPARING -> SHIPPING -> COMPLETED (COD payment becomes SUCCESS) + commission snapshot")
    void testFulfillment_COD_FullCycle_WithCommission() throws Exception {
        // Given: Commission rate active since yesterday (5%)
        createCommissionRate(BigDecimal.valueOf(5), LocalDateTime.now().minusDays(1));

        Product product = createProduct(supplierCompany1, "Fulfillment COD Prod", 100, 0);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.CONFIRMED, PaymentMethod.COD, PaymentStatus.PENDING, product, 10);
        // subtotal = 100000 * 10 = 1,000,000

        // 1. CONFIRMED -> PREPARING
        mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/preparing")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PREPARING")));

        // 2. PREPARING -> SHIPPING
        mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/shipping")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SHIPPING")));

        // 3. SHIPPING -> COMPLETED
        mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        // Verify COD Payment transitioned PENDING -> SUCCESS
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();

        // Verify Commission snapshot: rate = 5%, subtotal = 1,000,000, commission = 50,000
        Order completedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(completedOrder.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(completedOrder.getCommissionAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));

        // Verify all 4 history states recorded
        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertThat(histories).hasSize(4);
    }

    @Test
    @DisplayName("Case 16: Online order completed -> Payment stays SUCCESS + commission snapshot")
    void testFulfillment_Online_Completed_RetainsPaymentSuccess_WithCommission() throws Exception {
        // Given: Commission rate active since yesterday (5%)
        createCommissionRate(BigDecimal.valueOf(5), LocalDateTime.now().minusDays(1));

        Product product = createProduct(supplierCompany1, "Fulfillment Online Prod", 100, 0);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.SHIPPING, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS, product, 10);
        // subtotal = 100000 * 10 = 1,000,000

        mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Verify Commission snapshot: same calculation as COD
        Order completedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(completedOrder.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(completedOrder.getCommissionAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("Case 17: Invalid fulfillment transition (e.g. CONFIRMED -> SHIPPING) is rejected -> 400 Bad Request")
    void testFulfillment_InvalidTransition_Rejected() throws Exception {
        Product product = createProduct(supplierCompany1, "Skip Step Prod", 50, 0);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.CONFIRMED, PaymentMethod.COD, PaymentStatus.PENDING, product, 5);

        // Attempting to skip PREPARING
        mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/shipping")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot transition order from status CONFIRMED to SHIPPING")));
    }

    // =========================================================================
    // SECTION 5: QUERY & AUDIT HISTORY TESTS
    // =========================================================================

    @Test
    @DisplayName("Case 18: Get Order Detail and Order Status History returns complete data for authorized users")
    void testGetOrderDetail_And_History() throws Exception {
        Product product = createProduct(supplierCompany1, "Detail Query Prod", 50, 5);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 5);

        // 1. Buyer accesses order detail
        mockMvc.perform(get("/api/v1/orders/" + order.getId())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCode", is(order.getOrderCode())))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        // 2. Supplier accesses order status history
        mockMvc.perform(get("/api/v1/orders/" + order.getId() + "/history")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 3. Unrelated Buyer cannot access order detail — returns 404 (Step 7 spec §7.2: do NOT leak existence)
        mockMvc.perform(get("/api/v1/orders/" + order.getId())
                        .header("Authorization", "Bearer " + buyer2Token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // SECTION 6: CONCURRENCY TEST (CONFIRM VS CANCEL)
    // =========================================================================

    @Test
    @DisplayName("Case 19: Concurrent Supplier Confirm vs Buyer Cancel -> Exactly one succeeds, one fails; no dual state")
    void testConcurrent_SupplierConfirm_vs_BuyerCancel() throws Exception {
        Product product = createProduct(supplierCompany1, "Concurrent Race Prod", 100, 20);
        Order order = createOrderWithPayment(buyerUser, buyerCompany, supplierCompany1,
                OrderStatus.PENDING_CONFIRMATION, PaymentMethod.COD, PaymentStatus.PENDING, product, 20);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger confirmStatus = new AtomicInteger();
        AtomicInteger cancelStatus = new AtomicInteger();

        CompletableFuture<Void> confirmTask = CompletableFuture.runAsync(() -> {
            try {
                MvcResult res = mockMvc.perform(post("/api/v1/supplier/orders/" + order.getId() + "/confirm")
                                .header("Authorization", "Bearer " + supplierToken))
                        .andReturn();
                confirmStatus.set(res.getResponse().getStatus());
            } catch (Exception e) {
                confirmStatus.set(500);
            }
        }, executor);

        CompletableFuture<Void> cancelTask = CompletableFuture.runAsync(() -> {
            try {
                MvcResult res = mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel")
                                .header("Authorization", "Bearer " + buyerToken))
                        .andReturn();
                cancelStatus.set(res.getResponse().getStatus());
            } catch (Exception e) {
                cancelStatus.set(500);
            }
        }, executor);

        CompletableFuture.allOf(confirmTask, cancelTask).join();
        executor.shutdown();

        // Exactly one should succeed (200) and the other should fail with 400 (Bad Request due to invalid transition)
        int s1 = confirmStatus.get();
        int s2 = cancelStatus.get();

        boolean oneSucceededOneFailed = (s1 == 200 && s2 == 400) || (s1 == 400 && s2 == 200);
        assertThat(oneSucceededOneFailed)
                .as("Expected one 200 OK and one 400 Bad Request. Got confirm=" + s1 + ", cancel=" + s2)
                .isTrue();

        // Check final order state is clean and consistent
        Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finalOrder.getStatus()).isIn(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);

        // Product invariants must hold
        Product finalProd = productRepository.findById(product.getId()).orElseThrow();
        assertThat(finalProd.getStockQuantity()).isGreaterThanOrEqualTo(finalProd.getReservedQuantity());
        assertThat(finalProd.getReservedQuantity()).isGreaterThanOrEqualTo(0);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Product createProduct(Company supplier, String name, int stock, int reserved) {
        Product p = new Product();
        p.setSupplierCompany(supplier);
        p.setCategory(category);
        p.setSku("SKU-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4));
        p.setName(name);
        p.setDescription("Description");
        p.setStockQuantity(stock);
        p.setReservedQuantity(reserved);
        p.setStatus("ACTIVE");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p = productRepository.save(p);
        createdProductIds.add(p.getId());

        createPriceTier(p, 1, null, new BigDecimal("100000.00"));
        return p;
    }

    private void createPriceTier(Product product, Integer minQty, Integer maxQty, BigDecimal price) {
        ProductPrice pp = new ProductPrice();
        pp.setProduct(product);
        pp.setMinQuantity(minQty);
        pp.setMaxQuantity(maxQty);
        pp.setUnitPrice(price);
        pp.setCreatedAt(LocalDateTime.now());
        pp.setUpdatedAt(LocalDateTime.now());
        productPriceRepository.save(pp);
    }

    private Order createOrderWithPayment(
            User buyer,
            Company buyerComp,
            Company supplierComp,
            OrderStatus status,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            Product product,
            int quantity
    ) {
        BigDecimal unitPrice = new BigDecimal("100000.00");
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        String orderCode = "ORD-TST-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);
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

        // Create OrderItem
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(subtotal);
        orderItemRepository.save(item);

        // Create Payment
        String paymentCode = "PAY-TST-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);
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

        // Create Initial History
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .changedBy(buyer)
                .status(status)
                .note("Order created in test")
                .createdAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(history);

        return order;
    }

    private User createOrGetUser(String username, Role role, Company company) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("$2a$10$xyz123mockpassword");
        u.setFullName("Test " + username);
        u.setEmail(username + "@example.com");
        u.setPhone("090" + (System.currentTimeMillis() % 10000000));
        u.setStatus("ACTIVE");
        u.setRole(role);
        u.setCompany(company);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(u);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private CommissionRate createCommissionRate(BigDecimal rate, LocalDateTime effectiveFrom) {
        CommissionRate commissionRate = new CommissionRate();
        commissionRate.setRate(rate);
        commissionRate.setEffectiveFrom(effectiveFrom);
        commissionRate.setCreatedBy(adminUser);
        commissionRate.setCreatedAt(LocalDateTime.now());
        return commissionRateRepository.save(commissionRate);
    }
}

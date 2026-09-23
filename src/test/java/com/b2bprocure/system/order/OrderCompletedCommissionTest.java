package com.b2bprocure.system.order;

import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.common.enums.ErrorCode;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.commission.entity.CommissionRate;
import com.b2bprocure.system.commission.repository.CommissionRateRepository;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.company.repository.CompanyRepository;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductPriceRepository;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.role.entity.Role;
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * Integration tests for commission calculation on order completion.
 * Commission is snapshot when Order transitions SHIPPING -> COMPLETED.
 */
@SpringBootTest
@DisplayName("Order Completed Commission Tests")
class OrderCompletedCommissionTest {

    @Autowired
    private WebApplicationContext context;

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

    private MockMvc mockMvc;

    private String uniquePrefix;
    private Company buyerCompany;
    private Company supplierCompany;
    private Category category;
    private User buyerUser;
    private User supplierUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Use timestamp + uuid for unique data in each test run
        uniquePrefix = "c_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + "_";

        // Clean up any leftover test data first
        cleanupTestData();

        supplierCompany = companyRepository.save(new Company(null,
                uniquePrefix + "Supplier", "TAX-" + uniquePrefix.replace("_", "").substring(0, 10),
                "supplier@test.com", "0900000001", "111 Supplier St", "SUPPLIER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));

        buyerCompany = companyRepository.save(new Company(null,
                uniquePrefix + "Buyer", "TAXB-" + uniquePrefix.replace("_", "").substring(0, 10),
                "buyer@test.com", "0900000002", "222 Buyer St", "BUYER", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));

        category = categoryRepository.save(new Category(null,
                uniquePrefix + "Category", "Test category", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));

        Role supplierRole = new Role(3L, "SUPPLIER", "Supplier");
        Role buyerRole = new Role(2L, "BUYER", "Buyer");
        Role adminRole = new Role(1L, "ADMIN", "Admin");

        supplierUser = createUser(uniquePrefix + "sup", supplierRole, supplierCompany);
        buyerUser = createUser(uniquePrefix + "buy", buyerRole, buyerCompany);
        adminUser = createUser(uniquePrefix + "adm", adminRole, null);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    private void cleanupTestData() {
        // Clean up in correct order to handle FK constraints
        commissionRateRepository.deleteAll();

        orderRepository.findAll().stream()
                .filter(o -> o.getOrderCode() != null && o.getOrderCode().startsWith("ORD-COMM-"))
                .forEach(order -> {
                    // Delete order status history first (FK to orders)
                    orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())
                            .forEach(orderStatusHistoryRepository::delete);
                    paymentRepository.findByOrderId(order.getId())
                            .ifPresent(paymentRepository::delete);
                    orderItemRepository.findByOrderId(order.getId())
                            .forEach(orderItemRepository::delete);
                    orderRepository.delete(order);
                });

        productRepository.findAll().stream()
                .filter(p -> p.getSku() != null && p.getSku().startsWith("SKU-COMM-"))
                .forEach(product -> {
                    productPriceRepository.deleteByProductId(product.getId());
                    productRepository.delete(product);
                });

        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith("c_"))
                .forEach(userRepository::delete);

        companyRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().startsWith("c_"))
                .forEach(companyRepository::delete);

        categoryRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().startsWith("c_"))
                .forEach(categoryRepository::delete);
    }

    private User createUser(String username, Role role, Company company) {
        return userRepository.save(new User(null, role, company, username,
                "$2a$10$xyz123mockpassword", "Test " + username, username + "@test.com",
                "0900000001", null, null, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now()));
    }

    private Product createProduct(String name, int stock, int reserved) {
        Product product = new Product();
        product.setSupplierCompany(supplierCompany);
        product.setCategory(category);
        product.setSku("SKU-COMM-" + UUID.randomUUID().toString().substring(0, 8));
        product.setName(name);
        product.setDescription("Test description");
        product.setStockQuantity(stock);
        product.setReservedQuantity(reserved);
        product.setStatus("ACTIVE");
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private Order createOrderInShippingStatus(BigDecimal subtotal) {
        Order order = new Order();
        order.setBuyerCompany(buyerCompany);
        order.setSupplierCompany(supplierCompany);
        order.setCreatedBy(buyerUser);
        order.setOrderCode("ORD-COMM-" + UUID.randomUUID().toString().substring(0, 8));
        order.setStatus(OrderStatus.SHIPPING);
        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal);
        order.setShippingCompanyName(buyerCompany.getName());
        order.setShippingPhone(buyerCompany.getPhone());
        order.setShippingAddress(buyerCompany.getAddress());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    private Payment createPayment(Order order, PaymentMethod method, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentCode("PAY-COMM-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setPaymentMethod(method);
        payment.setStatus(status);
        payment.setAmount(order.getSubtotal());
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        if (status == PaymentStatus.SUCCESS) {
            payment.setPaidAt(LocalDateTime.now());
        }
        return paymentRepository.save(payment);
    }

    private CommissionRate createRate(BigDecimal rate, LocalDateTime effectiveFrom) {
        CommissionRate commissionRate = new CommissionRate();
        commissionRate.setRate(rate);
        commissionRate.setEffectiveFrom(effectiveFrom);
        commissionRate.setCreatedBy(adminUser);
        commissionRate.setCreatedAt(LocalDateTime.now());
        return commissionRateRepository.save(commissionRate);
    }

    private String getSupplierToken() {
        return jwtTokenProvider.generateToken(
                UserPrincipal.create(userRepository.findByIdWithRoleAndCompany(supplierUser.getId()).orElseThrow()));
    }

    // =========================================================================
    // TEST 1: Active rate found - commission correctly calculated and snapshot
    // =========================================================================

    @Nested
    @DisplayName("1. Active rate found - commission correctly calculated and snapshot")
    class Test1_ActiveRateFound {

        @Test
        @DisplayName("Order completed with active rate -> commissionRate and commissionAmount snapshot correctly")
        void shouldSnapshotCommissionWhenActiveRateExists() throws Exception {
            createRate(BigDecimal.valueOf(5), LocalDateTime.now().minusDays(1));

            createProduct("Test Product 1", 100, 10);
            BigDecimal subtotal = BigDecimal.valueOf(1000000);
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.COD, PaymentStatus.PENDING);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()))
                    .andExpect(result -> {
                        assertThat(result.getResponse().getStatus()).isEqualTo(200);
                    });

            Order completed = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(completed.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(completed.getCommissionAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
            assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    // =========================================================================
    // TEST 2: No active rate -> BusinessException thrown
    // =========================================================================

    @Nested
    @DisplayName("2. No active rate -> BusinessException thrown")
    class Test2_NoActiveRate {

        @Test
        @DisplayName("Order completed with no active rate -> COMMISSION_RATE_NOT_FOUND error")
        void shouldThrowWhenNoActiveRate() throws Exception {
            createProduct("Test Product 2", 100, 10);
            BigDecimal subtotal = BigDecimal.valueOf(1000000);
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.COD, PaymentStatus.PENDING);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()))
                    .andExpect(result -> {
                        assertThat(result.getResponse().getStatus()).isEqualTo(500);
                        assertThat(result.getResolvedException()).isInstanceOf(BusinessException.class);
                        BusinessException ex = (BusinessException) result.getResolvedException();
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMMISSION_RATE_NOT_FOUND);
                    });

            Order saved = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.SHIPPING);
            assertThat(saved.getCommissionRate()).isNull();
            assertThat(saved.getCommissionAmount()).isNull();
        }
    }

    // =========================================================================
    // TEST 3: Future-only rate -> BusinessException thrown
    // =========================================================================

    @Nested
    @DisplayName("3. Future-only rate -> BusinessException thrown")
    class Test3_FutureOnlyRate {

        @Test
        @DisplayName("Order completed with only future rate -> COMMISSION_RATE_NOT_FOUND error")
        void shouldThrowWhenOnlyFutureRateExists() throws Exception {
            createRate(BigDecimal.valueOf(10), LocalDateTime.now().plusDays(30));

            createProduct("Test Product 3", 100, 10);
            BigDecimal subtotal = BigDecimal.valueOf(500000);
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.COD, PaymentStatus.PENDING);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()))
                    .andExpect(result -> {
                        assertThat(result.getResponse().getStatus()).isEqualTo(500);
                        assertThat(result.getResolvedException()).isInstanceOf(BusinessException.class);
                        BusinessException ex = (BusinessException) result.getResolvedException();
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMMISSION_RATE_NOT_FOUND);
                    });

            Order saved = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        }
    }

    // =========================================================================
    // TEST 4: Rate selection - newest wins
    // =========================================================================

    @Nested
    @DisplayName("4. Rate selection - newest active rate wins")
    class Test4_RateSelectionNewestWins {

        @Test
        @DisplayName("Multiple rates -> most recent effective_from is selected")
        void shouldSelectNewestActiveRate() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            createRate(BigDecimal.valueOf(1), now.minusMonths(12));
            createRate(BigDecimal.valueOf(3), now.minusMonths(6));
            createRate(BigDecimal.valueOf(5), now.minusMonths(1));

            createProduct("Test Product 4", 100, 10);
            BigDecimal subtotal = BigDecimal.valueOf(1000000);
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.COD, PaymentStatus.PENDING);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()));

            Order completed = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(completed.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(completed.getCommissionAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        }
    }

    // =========================================================================
    // TEST 5: Boundary effective_from == completedAt
    // =========================================================================

    @Nested
    @DisplayName("5. Boundary: effective_from == completedAt counts as active")
    class Test5_BoundaryEffectiveFromEqualsCompletedAt {

        @Test
        @DisplayName("Rate with effective_from exactly at completion time -> rate is used")
        void shouldUseRateWhenEffectiveFromEqualsCompletedAt() throws Exception {
            createRate(BigDecimal.valueOf(7), LocalDateTime.now());

            createProduct("Test Product 5", 100, 10);
            BigDecimal subtotal = BigDecimal.valueOf(200000);
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()));

            Order completed = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(completed.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(7));
            assertThat(completed.getCommissionAmount()).isEqualByComparingTo(BigDecimal.valueOf(14000));
        }
    }

    // =========================================================================
    // TEST 6: Idempotency - no double commission
    // =========================================================================

    @Nested
    @DisplayName("6. Idempotency - completed order cannot be completed again")
    class Test6_Idempotency {

        @Test
        @DisplayName("Already COMPLETED order -> INVALID_ORDER_STATE_TRANSITION error")
        void shouldRejectReCompletion() throws Exception {
            createRate(BigDecimal.valueOf(5), LocalDateTime.now().minusDays(1));

            createProduct("Test Product 6", 100, 10);
            BigDecimal subtotal = BigDecimal.valueOf(1000000);
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.COD, PaymentStatus.PENDING);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()));

            Order completed = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(completed.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(5));
            BigDecimal originalAmount = completed.getCommissionAmount();

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()))
                    .andExpect(result -> {
                        assertThat(result.getResponse().getStatus()).isEqualTo(400);
                    });

            Order stillSame = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(stillSame.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(stillSame.getCommissionAmount()).isEqualByComparingTo(originalAmount);
        }
    }

    // =========================================================================
    // TEST 7: Precision - large amounts
    // =========================================================================

    @Nested
    @DisplayName("7. Precision - large amount with 5% rate")
    class Test7_PrecisionLargeAmount {

        @Test
        @DisplayName("Large subtotal with 5% rate -> correct commission amount")
        void shouldCalculateCorrectlyForLargeAmount() throws Exception {
            createRate(BigDecimal.valueOf(5), LocalDateTime.now().minusDays(1));

            createProduct("Test Product 7", 100, 10);
            BigDecimal subtotal = new BigDecimal("1234567890.12");
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.COD, PaymentStatus.PENDING);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()));

            Order completed = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(completed.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(completed.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("61728394.51"));
        }
    }

    // =========================================================================
    // TEST 8: Precision - rounding edge case
    // =========================================================================

    @Nested
    @DisplayName("8. Precision - rounding edge case")
    class Test8_PrecisionRounding {

        @Test
        @DisplayName("3% rate on 999.99 -> 30.00 (rounded)")
        void shouldRoundCorrectlyForSimpleCase() throws Exception {
            createRate(BigDecimal.valueOf(3), LocalDateTime.now().minusDays(1));

            createProduct("Test Product 8", 100, 10);
            BigDecimal subtotal = new BigDecimal("999.99");
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.COD, PaymentStatus.PENDING);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()));

            Order completed = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(completed.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(3));
            assertThat(completed.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        }
    }

    // =========================================================================
    // TEST 9: COD and ZaloPay use the same commission logic
    // =========================================================================

    @Nested
    @DisplayName("9. COD and ZaloPay use the same commission logic")
    class Test9_CodAndZaloPaySameLogic {

        @Test
        @DisplayName("ZaloPay order -> same commission calculation as COD")
        void shouldCalculateSameForZaloPayAsCod() throws Exception {
            createRate(BigDecimal.valueOf(5), LocalDateTime.now().minusDays(1));

            createProduct("Test Product 9", 100, 10);
            BigDecimal subtotal = BigDecimal.valueOf(1000000);
            Order order = createOrderInShippingStatus(subtotal);
            createPayment(order, PaymentMethod.ZALOPAY, PaymentStatus.SUCCESS);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/complete")
                            .header("Authorization", "Bearer " + getSupplierToken()));

            Order completed = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(completed.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(5));
            assertThat(completed.getCommissionAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));

            Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }

    // =========================================================================
    // TEST 10: Other transitions do NOT calculate commission
    // =========================================================================

    @Nested
    @DisplayName("10. Other transitions do NOT calculate commission")
    class Test10_OtherTransitionsNoCommission {

        @Test
        @DisplayName("CONFIRMED -> PREPARING does not set commission")
        void shouldNotCalculateCommissionBeforeCompletion() throws Exception {
            createRate(BigDecimal.valueOf(5), LocalDateTime.now().minusDays(1));

            createProduct("Test Product 10", 100, 10);
            BigDecimal subtotal = BigDecimal.valueOf(1000000);

            Order order = new Order();
            order.setBuyerCompany(buyerCompany);
            order.setSupplierCompany(supplierCompany);
            order.setCreatedBy(buyerUser);
            order.setOrderCode("ORD-COMM-" + UUID.randomUUID().toString().substring(0, 8));
            order.setStatus(OrderStatus.CONFIRMED);
            order.setSubtotal(subtotal);
            order.setTotalAmount(subtotal);
            order.setShippingCompanyName(buyerCompany.getName());
            order.setShippingPhone(buyerCompany.getPhone());
            order.setShippingAddress(buyerCompany.getAddress());
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            order = orderRepository.save(order);

            createPayment(order, PaymentMethod.COD, PaymentStatus.PENDING);

            mockMvc.perform(patch("/api/v1/supplier/orders/" + order.getId() + "/preparing")
                            .header("Authorization", "Bearer " + getSupplierToken()));

            Order updated = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.PREPARING);
            assertThat(updated.getCommissionRate()).isNull();
            assertThat(updated.getCommissionAmount()).isNull();
        }
    }
}

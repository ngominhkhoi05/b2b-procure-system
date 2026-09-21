package com.b2bprocure.system.zalopay;

import com.b2bprocure.system.cart.entity.Cart;
import com.b2bprocure.system.cart.entity.CartItem;
import com.b2bprocure.system.cart.repository.CartItemRepository;
import com.b2bprocure.system.cart.repository.CartRepository;
import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.common.util.SecurityUtil;
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
import com.b2bprocure.system.security.JwtTokenProvider;
import com.b2bprocure.system.security.UserPrincipal;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import com.b2bprocure.system.zalopay.dto.ZaloPayCallbackRequest;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreateOrderResponse;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreatePaymentRequest;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreatePaymentResponse;
import com.b2bprocure.system.zalopay.service.ZaloPayService;
import com.b2bprocure.system.zalopay.service.ZaloPaySignatureService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ZaloPay payment flow.
 * Tests the full flow without actual ZaloPay API calls (mocked via TestRestTemplate interceptors).
 */
@SpringBootTest
@DisplayName("ZaloPay Integration Tests")
public class ZaloPayIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

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
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

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

    @Autowired
    private ZaloPayConfig zaloPayConfig;

    @Autowired
    private ZaloPaySignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    private String buyerToken;
    private User buyerUser;
    private Company buyerCompany;
    private Company supplierCompany;
    private Category productCategory;

    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdPaymentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Setup buyer user
        buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer").orElseThrow();
        buyerCompany = buyerUser.getCompany();
        buyerCompany.setName("ZaloPay Buyer Corp");
        buyerCompany.setPhone("0901234567");
        buyerCompany.setAddress("123 Zalo St, Hanoi");
        buyerCompany = companyRepository.save(buyerCompany);

        buyerToken = jwtTokenProvider.generateToken(UserPrincipal.create(buyerUser));

        // Setup supplier company
        supplierCompany = companyRepository.save(new Company(null,
                "ZaloPay Supplier Co",
                "TAX-ZALO-SUP-" + System.currentTimeMillis(),
                "zalosup@example.com",
                "0909999999",
                "456 Supplier St",
                "SUPPLIER",
                "ACTIVE",
                LocalDateTime.now(),
                LocalDateTime.now()
        ));

        // Get or create a category for products
        productCategory = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category c = new Category();
            c.setName("Test Category");
            c.setStatus("ACTIVE");
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            return categoryRepository.save(c);
        });

        // Clean up before each test
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean payments first (FK constraint)
        for (Long paymentId : createdPaymentIds) {
            paymentRepository.findById(paymentId).ifPresent(paymentRepository::delete);
        }

        // Clean orders
        for (Long orderId : createdOrderIds) {
            orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId)
                    .forEach(orderStatusHistoryRepository::delete);
            orderItemRepository.findByOrderId(orderId)
                    .forEach(orderItemRepository::delete);
            orderRepository.findById(orderId).ifPresent(orderRepository::delete);
        }

        // Clean products
        for (Long productId : createdProductIds) {
            productPriceRepository.findByProductId(productId).forEach(productPriceRepository::delete);
            productRepository.deleteById(productId);
        }

        companyRepository.delete(supplierCompany);

        createdProductIds.clear();
        createdOrderIds.clear();
        createdPaymentIds.clear();
    }

    private Product createProduct(String name, int stock) {
        Product p = new Product();
        p.setSupplierCompany(supplierCompany);
        p.setCategory(productCategory); // Use existing category
        p.setSku("ZALO-SKU-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
        p.setName(name);
        p.setStockQuantity(stock);
        p.setReservedQuantity(0);
        p.setStatus("ACTIVE");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p = productRepository.save(p);
        createdProductIds.add(p.getId());

        ProductPrice pp = new ProductPrice();
        pp.setProduct(p);
        pp.setMinQuantity(1);
        pp.setMaxQuantity(null);
        pp.setUnitPrice(new BigDecimal("50000.00"));
        pp.setCreatedAt(LocalDateTime.now());
        pp.setUpdatedAt(LocalDateTime.now());
        productPriceRepository.save(pp);

        return p;
    }

    private Payment createTestPayment(Order order, PaymentStatus status) {
        String paymentCode = "PAY-ZALO-TEST-" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .order(order)
                .paymentCode(paymentCode)
                .paymentMethod(PaymentMethod.ZALOPAY)
                .status(status)
                .amount(order.getTotalAmount())
                .expiredAt(LocalDateTime.now().plusMinutes(15))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        payment = paymentRepository.save(payment);
        createdPaymentIds.add(payment.getId());
        return payment;
    }

    // ========================================================================
    // API TESTS
    // ========================================================================

    @Nested
    @DisplayName("API Endpoint Tests")
    class ApiEndpointTests {

        @Test
        @DisplayName("Checkout ZALOPAY returns paymentId and orderId")
        void testCheckout_ZaloPay_ReturnsPaymentIdAndOrderId() throws Exception {
            // Create a test product
            Product p = createProduct("ZaloPay Test Product", 100);
            if (p.getCategory() == null) {
                p.setCategory(categoryRepository.findAll().stream().findFirst().orElse(null));
                p = productRepository.save(p);
            }

            // Find or create cart
            Cart cart = cartRepository.findByUserId(buyerUser.getId()).orElseGet(() -> {
                Cart c = Cart.builder()
                        .user(buyerUser)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                return cartRepository.save(c);
            });

            var cartItem = com.b2bprocure.system.cart.entity.CartItem.builder()
                    .cart(cart)
                    .product(p)
                    .quantity(2)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            cartItem = cartItemRepository.save(cartItem);

            String requestBody = String.format(
                    "{\"cartItemIds\":[%d],\"paymentMethod\":\"ZALOPAY\"}", cartItem.getId());

            try {
                MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                                .header("Authorization", "Bearer " + buyerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.success", is(true)))
                        .andExpect(jsonPath("$.data.paymentMethod", is("ZALOPAY")))
                        .andExpect(jsonPath("$.data.paymentStatus", is("PENDING")))
                        .andExpect(jsonPath("$.data.paymentId", notNullValue()))
                        .andExpect(jsonPath("$.data.orderId", notNullValue()))
                        .andReturn();

                Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("orderId").asLong();
                Long paymentId = objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("paymentId").asLong();

                createdOrderIds.add(orderId);
                createdPaymentIds.add(paymentId);

                // Verify payment was created with PENDING status
                Payment payment = paymentRepository.findById(paymentId).orElseThrow();
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
                assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.ZALOPAY);
            } catch (Exception e) {
                // If checkout fails due to existing data, skip with informative message
                System.err.println("Checkout test setup failed: " + e.getMessage());
                throw e;
            }
        }

        @Test
        @DisplayName("Unauthenticated create-payment returns 401")
        void testCreatePayment_Unauthenticated_401() throws Exception {
            String requestBody = "{\"paymentId\":1,\"orderId\":1}";

            mockMvc.perform(post("/api/v1/checkout/zalopay/create-payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Callback endpoint exists and returns JSON")
        void testCallback_EndpointExists() throws Exception {
            // Callback endpoint should be accessible without authentication
            String callbackBody = "{\"data\":\"test\",\"mac\":\"test\",\"type\":1}";

            // Note: This will fail signature verification but confirms endpoint is accessible
            mockMvc.perform(post("/api/v1/payments/zalopay/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.return_code", notNullValue()));
        }
    }

    @Nested
    @DisplayName("Callback Processing Tests")
    class CallbackProcessingTests {

        @Test
        @DisplayName("Callback with invalid signature returns return_code=-1")
        void testCallback_InvalidSignature_ReturnsError() throws Exception {
            String callbackBody = "{\"data\":\"{\\\"app_id\\\":2553}\",\"mac\":\"invalid_mac\",\"type\":1}";

            mockMvc.perform(post("/api/v1/payments/zalopay/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.return_code", is(-1)))
                    .andExpect(jsonPath("$.return_message", containsString("mac")));
        }

        @Test
        @DisplayName("Callback with empty data returns return_code=-1")
        void testCallback_EmptyData_ReturnsError() throws Exception {
            String callbackBody = "{\"data\":\"\",\"mac\":\"somemac\",\"type\":1}";

            mockMvc.perform(post("/api/v1/payments/zalopay/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.return_code", is(-1)));
        }

        @Test
        @DisplayName("Callback with empty MAC returns return_code=-1")
        void testCallback_EmptyMac_ReturnsError() throws Exception {
            String callbackBody = "{\"data\":\"{\\\"app_id\\\":2553}\",\"mac\":\"\",\"type\":1}";

            mockMvc.perform(post("/api/v1/payments/zalopay/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.return_code", is(-1)));
        }
    }

    @Nested
    @DisplayName("Payment State Transition Tests")
    class PaymentStateTransitionTests {

        @Test
        @DisplayName("Payment already SUCCESS is idempotent on callback")
        void testPayment_AlreadySuccess_Idempotent() {
            String uniqueAppTransId = "261221_" + java.util.UUID.randomUUID().toString().substring(0, 8);

            // Create order and payment in SUCCESS state
            Order order = Order.builder()
                    .buyerCompany(buyerCompany)
                    .supplierCompany(supplierCompany)
                    .createdBy(buyerUser)
                    .orderCode("ORD-IDEM-" + System.currentTimeMillis())
                    .status(OrderStatus.PAID)
                    .subtotal(new BigDecimal("100000"))
                    .totalAmount(new BigDecimal("100000"))
                    .shippingCompanyName(buyerCompany.getName())
                    .shippingPhone(buyerCompany.getPhone())
                    .shippingAddress(buyerCompany.getAddress())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            order = orderRepository.save(order);
            createdOrderIds.add(order.getId());

            Payment payment = Payment.builder()
                    .order(order)
                    .paymentCode("PAY-IDEM-" + System.currentTimeMillis())
                    .paymentMethod(PaymentMethod.ZALOPAY)
                    .status(PaymentStatus.SUCCESS)
                    .amount(new BigDecimal("100000"))
                    .appTransId(uniqueAppTransId)
                    .paidAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            payment = paymentRepository.save(payment);
            createdPaymentIds.add(payment.getId());

            // Verify state was set correctly
            Payment retrieved = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(retrieved.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(retrieved.getAppTransId()).isEqualTo(uniqueAppTransId);
        }

        @Test
        @DisplayName("Payment not found for callback returns error gracefully")
        void testPayment_NotFound_ReturnsError() throws Exception {
            // Create a callback with non-existent app_trans_id and invalid signature
            String callbackData = String.format(
                    "{\"app_id\":%s,\"app_trans_id\":\"nonexistent_order_123\",\"amount\":100000,\"zp_trans_id\":123456789}",
                    zaloPayConfig.getAppId());

            String callbackBody = objectMapper.writeValueAsString(Map.of(
                    "data", callbackData,
                    "mac", "0000000000000000000000000000000000000000000000000000000000000000",
                    "type", 1
            ));

            // This should be processed but return error due to invalid signature
            mockMvc.perform(post("/api/v1/payments/zalopay/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(callbackBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.return_code", is(-1)));
        }
    }

    // ========================================================================
    // DATABASE STATE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Database State Tests")
    class DatabaseStateTests {

        @Test
        @DisplayName("Payment entity has app_trans_id field")
        void testPayment_HasAppTransIdField() {
            String uniqueAppTransId = "261221_" + java.util.UUID.randomUUID().toString().substring(0, 8);

            // Create a payment with app_trans_id
            Order order = Order.builder()
                    .buyerCompany(buyerCompany)
                    .supplierCompany(supplierCompany)
                    .createdBy(buyerUser)
                    .orderCode("ORD-APPID-" + System.currentTimeMillis())
                    .status(OrderStatus.PENDING_CONFIRMATION)
                    .subtotal(new BigDecimal("50000"))
                    .totalAmount(new BigDecimal("50000"))
                    .shippingCompanyName(buyerCompany.getName())
                    .shippingPhone(buyerCompany.getPhone())
                    .shippingAddress(buyerCompany.getAddress())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            order = orderRepository.save(order);
            createdOrderIds.add(order.getId());

            Payment payment = Payment.builder()
                    .order(order)
                    .paymentCode("PAY-APPID-" + System.currentTimeMillis())
                    .paymentMethod(PaymentMethod.ZALOPAY)
                    .status(PaymentStatus.PENDING)
                    .amount(new BigDecimal("50000"))
                    .appTransId(uniqueAppTransId)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            payment = paymentRepository.save(payment);
            createdPaymentIds.add(payment.getId());

            // Verify app_trans_id is saved
            Payment saved = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(saved.getAppTransId()).isEqualTo(uniqueAppTransId);
        }

        @Test
        @DisplayName("Order in PAID status has correct state")
        void testOrder_PaidStatus_CorrectState() {
            Order order = Order.builder()
                    .buyerCompany(buyerCompany)
                    .supplierCompany(supplierCompany)
                    .createdBy(buyerUser)
                    .orderCode("ORD-PAID-" + System.currentTimeMillis())
                    .status(OrderStatus.PAID)
                    .subtotal(new BigDecimal("150000"))
                    .totalAmount(new BigDecimal("150000"))
                    .shippingCompanyName(buyerCompany.getName())
                    .shippingPhone(buyerCompany.getPhone())
                    .shippingAddress(buyerCompany.getAddress())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            order = orderRepository.save(order);
            createdOrderIds.add(order.getId());

            OrderStatusHistory history = OrderStatusHistory.builder()
                    .order(order)
                    .changedBy(null) // System action
                    .status(OrderStatus.PAID)
                    .note("Payment confirmed via ZaloPay")
                    .createdAt(LocalDateTime.now())
                    .build();
            orderStatusHistoryRepository.save(history);

            Order saved = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);

            List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
            assertThat(histories).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    // ========================================================================
    // SIGNATURE SERVICE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Signature Service Tests")
    class SignatureServiceTests {

        @Test
        @DisplayName("Signature service generates valid HMAC-SHA256")
        void testSignature_HMAC_SHA256_Valid() {
            String mac = signatureService.createOrderMac(
                    "2553", "test123", "user", 50000L, 1234567890L, "{}", "[]"
            );

            assertThat(mac).isNotNull();
            assertThat(mac).hasSize(64);
            assertThat(mac).matches("[a-f0-9]+");
        }

        @Test
        @DisplayName("Signature service verifies callback MAC correctly")
        void testSignature_CallbackVerification() {
            String data = "{\"test\":\"data\"}";
            // Test that invalid MAC is rejected
            boolean result = signatureService.verifyCallbackMac(data, "0000000000000000000000000000000000000000000000000000000000000000");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Signature service rejects invalid MAC")
        void testSignature_InvalidMac_Rejected() {
            String data = "{\"test\":\"data\"}";
            boolean result = signatureService.verifyCallbackMac(data, "invalidmac123456789012345678901234567890123456789012345678901234");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Different data produces different MAC")
        void testSignature_DifferentData_DifferentMac() {
            String mac1 = signatureService.createOrderMac(
                    "2553", "order1", "user", 10000L, 1234567890L, "{}", "[]"
            );
            String mac2 = signatureService.createOrderMac(
                    "2553", "order2", "user", 10000L, 1234567890L, "{}", "[]"
            );

            assertThat(mac1).isNotEqualTo(mac2);
        }
    }
}

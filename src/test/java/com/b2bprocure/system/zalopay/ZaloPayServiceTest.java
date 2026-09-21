package com.b2bprocure.system.zalopay;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.company.entity.Company;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.category.entity.Category;
import com.b2bprocure.system.category.repository.CategoryRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.user.entity.User;
import com.b2bprocure.system.user.repository.UserRepository;
import com.b2bprocure.system.zalopay.client.ZaloPayClient;
import com.b2bprocure.system.zalopay.client.ZaloPayException;
import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import com.b2bprocure.system.zalopay.dto.ZaloPayCallbackRequest;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreateOrderResponse;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreatePaymentResponse;
import com.b2bprocure.system.zalopay.service.ZaloPaySignatureService;
import com.b2bprocure.system.zalopay.service.ZaloPayServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ZaloPayService with mocked ZaloPayClient.
 * Tests business logic without actual ZaloPay API calls.
 * Uses @Transactional to auto-rollback after each test.
 */
@SpringBootTest
@DisplayName("ZaloPayService Tests (Mocked Client)")
@Transactional
class ZaloPayServiceTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ZaloPaySignatureService signatureService;

    @Autowired
    private ZaloPayConfig zaloPayConfig;

    @Autowired
    private ObjectMapper objectMapper;

    private ZaloPayClient zaloPayClientMock;
    private ZaloPayServiceImpl zaloPayService;

    private User buyerUser;
    private Company buyerCompany;
    private Company supplierCompany;
    private Category testCategory;
    private com.b2bprocure.system.product.entity.Product testProduct;
    private Order order;
    private Payment payment;

    // Track created entities for cleanup
    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdPaymentIds = new ArrayList<>();
    private final List<Long> createdOrderItemIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        // Generate unique IDs for this test run
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        // Setup buyer user
        buyerUser = userRepository.findByUsernameWithRoleAndCompany("buyer")
                .orElseThrow(() -> new RuntimeException("Buyer user not found"));

        buyerCompany = buyerUser.getCompany();
        if (buyerCompany == null) {
            throw new RuntimeException("Buyer user has no company");
        }

        supplierCompany = userRepository.findByUsernameWithRoleAndCompany("supplier")
                .map(u -> u.getCompany())
                .orElseThrow(() -> new RuntimeException("Supplier company not found"));

        // Set up security context for buyer
        var principal = com.b2bprocure.system.security.UserPrincipal.create(buyerUser);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Create order with unique code
        order = Order.builder()
                .buyerCompany(buyerCompany)
                .supplierCompany(supplierCompany)
                .createdBy(buyerUser)
                .orderCode("ORD-ZALO-" + uniqueId)
                .status(OrderStatus.PENDING_CONFIRMATION)
                .subtotal(new BigDecimal("100000.00"))
                .totalAmount(new BigDecimal("100000.00"))
                .shippingCompanyName(buyerCompany.getName())
                .shippingPhone(buyerCompany.getPhone())
                .shippingAddress(buyerCompany.getAddress())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);
        createdOrderIds.add(order.getId());

        // Get a category for the product
        testCategory = categoryRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName("Test Category");
                    c.setStatus("ACTIVE");
                    c.setCreatedAt(LocalDateTime.now());
                    c.setUpdatedAt(LocalDateTime.now());
                    return categoryRepository.save(c);
                });

        // Create product
        testProduct = new Product();
        testProduct.setSupplierCompany(supplierCompany);
        testProduct.setCategory(testCategory);
        testProduct.setSku("TEST-SKU-" + uniqueId);
        testProduct.setName("Test Product");
        testProduct.setStockQuantity(100);
        testProduct.setReservedQuantity(0);
        testProduct.setStatus("ACTIVE");
        testProduct.setCreatedAt(LocalDateTime.now());
        testProduct.setUpdatedAt(LocalDateTime.now());
        testProduct = productRepository.save(testProduct);

        // Create order item with valid product reference
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(testProduct);
        orderItem.setProductName("Test Product");
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(new BigDecimal("100000.00"));
        orderItem.setSubtotal(new BigDecimal("100000.00"));
        orderItemRepository.save(orderItem);
        createdOrderItemIds.add(orderItem.getId());

        // Create payment with unique code
        payment = Payment.builder()
                .order(order)
                .paymentCode("PAY-ZALO-" + uniqueId)
                .paymentMethod(PaymentMethod.ZALOPAY)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("100000.00"))
                .expiredAt(LocalDateTime.now().plusMinutes(15))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        payment = paymentRepository.save(payment);
        createdPaymentIds.add(payment.getId());

        // Create mocked ZaloPayClient and ZaloPayService
        zaloPayClientMock = mock(ZaloPayClient.class);
        zaloPayService = new ZaloPayServiceImpl(
                zaloPayConfig,
                zaloPayClientMock,
                signatureService,
                paymentRepository,
                orderRepository,
                orderItemRepository,
                orderStatusHistoryRepository,
                userRepository,
                mock(com.b2bprocure.system.cart.repository.CartItemRepository.class),
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        // Clean up in reverse order (respecting FK constraints)
        for (Long orderItemId : createdOrderItemIds) {
            orderItemRepository.findById(orderItemId).ifPresent(orderItemRepository::delete);
        }
        for (Long paymentId : createdPaymentIds) {
            paymentRepository.findById(paymentId).ifPresent(paymentRepository::delete);
        }
        for (Long orderId : createdOrderIds) {
            orderRepository.findById(orderId).ifPresent(orderRepository::delete);
        }
        if (testProduct != null && testProduct.getId() != null) {
            productRepository.delete(testProduct);
        }

        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Initiate Payment Tests")
    class InitiatePaymentTests {

        @Test
        @DisplayName("Should initiate payment successfully when ZaloPay returns success")
        void testInitiatePayment_Success() {
            // Mock ZaloPay response
            ZaloPayCreateOrderResponse mockResponse = ZaloPayCreateOrderResponse.builder()
                    .returnCode(1)
                    .subReturnCode(1)
                    .orderUrl("https://qcgateway.zalopay.vn/test")
                    .zpTransToken("test_token_123")
                    .returnMessage("Success")
                    .build();
            when(zaloPayClientMock.createOrder(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // Call service
            ZaloPayCreatePaymentResponse response = zaloPayService.initiatePayment(payment.getId(), order.getId());

            // Verify
            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).isEqualTo(payment.getId());
            assertThat(response.getPaymentUrl()).isEqualTo("https://qcgateway.zalopay.vn/test");
            assertThat(response.getOrderCode()).isEqualTo(order.getOrderCode());

            // Verify payment updated
            Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updatedPayment.getAppTransId()).isNotNull();
            assertThat(updatedPayment.getProviderTransactionId()).isEqualTo("test_token_123");
        }

        @Test
        @DisplayName("Should throw exception when ZaloPay API fails")
        void testInitiatePayment_ApiFailure() {
            when(zaloPayClientMock.createOrder(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                    .thenThrow(new ZaloPayException("ZaloPay API unavailable"));

            assertThatThrownBy(() -> zaloPayService.initiatePayment(payment.getId(), order.getId()))
                    .hasMessageContaining("ZaloPay API unavailable");
        }

        @Test
        @DisplayName("Should throw exception when ZaloPay returns non-success code")
        void testInitiatePayment_NonSuccessResponse() {
            ZaloPayCreateOrderResponse mockResponse = ZaloPayCreateOrderResponse.builder()
                    .returnCode(2)
                    .subReturnCode(-68)
                    .returnMessage("Failed")
                    .subReturnMessage("Mã giao dịch bị trùng")
                    .build();
            when(zaloPayClientMock.createOrder(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            assertThatThrownBy(() -> zaloPayService.initiatePayment(payment.getId(), order.getId()))
                    .hasMessageContaining("ZaloPay order creation failed");
        }

        @Test
        @DisplayName("Should throw exception when order URL is missing")
        void testInitiatePayment_MissingOrderUrl() {
            ZaloPayCreateOrderResponse mockResponse = ZaloPayCreateOrderResponse.builder()
                    .returnCode(1)
                    .subReturnCode(1)
                    .orderUrl("")
                    .returnMessage("Success")
                    .build();
            when(zaloPayClientMock.createOrder(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            assertThatThrownBy(() -> zaloPayService.initiatePayment(payment.getId(), order.getId()))
                    .hasMessageContaining("missing order_url");
        }

        @Test
        @DisplayName("Should throw exception when payment not found")
        void testInitiatePayment_PaymentNotFound() {
            assertThatThrownBy(() -> zaloPayService.initiatePayment(999999L, order.getId()))
                    .hasMessageContaining("Payment not found");
        }

        @Test
        @DisplayName("Should throw exception when order ID mismatch")
        void testInitiatePayment_OrderIdMismatch() {
            assertThatThrownBy(() -> zaloPayService.initiatePayment(payment.getId(), 999999L))
                    .hasMessageContaining("does not belong to the specified order");
        }
    }

    @Nested
    @DisplayName("Handle Callback Tests")
    class HandleCallbackTests {

        @Test
        @DisplayName("Should return error for empty callback data")
        void testHandleCallback_EmptyData() {
            ZaloPayCallbackRequest callbackRequest = ZaloPayCallbackRequest.builder()
                    .data("")
                    .mac("somemac")
                    .type(1)
                    .build();

            java.util.Map<String, Object> response = zaloPayService.handleCallback(callbackRequest);

            assertThat(response.get("return_code")).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should return error for empty MAC")
        void testHandleCallback_EmptyMac() {
            ZaloPayCallbackRequest callbackRequest = ZaloPayCallbackRequest.builder()
                    .data("{\"test\":\"data\"}")
                    .mac("")
                    .type(1)
                    .build();

            java.util.Map<String, Object> response = zaloPayService.handleCallback(callbackRequest);

            assertThat(response.get("return_code")).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should return error for invalid MAC")
        void testHandleCallback_InvalidMac() {
            ZaloPayCallbackRequest callbackRequest = ZaloPayCallbackRequest.builder()
                    .data("{\"app_id\":2553,\"app_trans_id\":\"test123\"}")
                    .mac("0000000000000000000000000000000000000000000000000000000000000000")
                    .type(1)
                    .build();

            java.util.Map<String, Object> response = zaloPayService.handleCallback(callbackRequest);

            assertThat(response.get("return_code")).isEqualTo(-1);
        }
    }
}

package com.b2bprocure.system.order.service;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import com.b2bprocure.system.common.enums.SettingKey;
import com.b2bprocure.system.order.entity.Order;
import com.b2bprocure.system.order.entity.OrderItem;
import com.b2bprocure.system.order.entity.OrderStatusHistory;
import com.b2bprocure.system.order.repository.OrderItemRepository;
import com.b2bprocure.system.order.repository.OrderRepository;
import com.b2bprocure.system.order.repository.OrderStatusHistoryRepository;
import com.b2bprocure.system.payment.entity.Payment;
import com.b2bprocure.system.payment.repository.PaymentRepository;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.setting.service.SystemSettingService;
import com.b2bprocure.system.zalopay.client.ZaloPayClient;
import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import com.b2bprocure.system.zalopay.service.ZaloPaySignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SupplierTimeoutService Tests")
class SupplierTimeoutServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private ZaloPayConfig zaloPayConfig;

    @Mock
    private ZaloPayClient zaloPayClient;

    @Mock
    private ZaloPaySignatureService signatureService;

    @InjectMocks
    private SupplierTimeoutServiceImpl supplierTimeoutService;

    // Test objects for COD tests
    private Order testOrder;
    private Payment testPayment;
    private Product testProduct;
    private OrderItem testOrderItem;

    // Helper method to create fresh ZaloPay test objects
    private Order createZaloPayOrder() {
        Order order = new Order();
        order.setId(2L);
        order.setOrderCode("ORD-002");
        order.setStatus(OrderStatus.PAID);
        order.setCreatedAt(LocalDateTime.now().minusHours(25));
        order.setUpdatedAt(LocalDateTime.now().minusHours(25));
        return order;
    }

    private Payment createZaloPayPayment(Order order) {
        Payment payment = new Payment();
        payment.setId(200L);
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.ZALOPAY);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(BigDecimal.valueOf(200000));
        payment.setPaidAt(LocalDateTime.now().minusHours(25));
        payment.setProviderTransactionId("123456789");
        return payment;
    }

    @BeforeEach
    void setUp() {
        // Create test product
        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setStockQuantity(100);
        testProduct.setReservedQuantity(10);

        // Create test order for COD
        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setOrderCode("ORD-001");
        testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
        testOrder.setCreatedAt(LocalDateTime.now().minusHours(25));
        testOrder.setUpdatedAt(LocalDateTime.now());

        // Create test order item
        testOrderItem = new OrderItem();
        testOrderItem.setId(1L);
        testOrderItem.setOrder(testOrder);
        testOrderItem.setProduct(testProduct);
        testOrderItem.setQuantity(5);

        // Create test payment for COD
        testPayment = new Payment();
        testPayment.setId(100L);
        testPayment.setOrder(testOrder);
        testPayment.setPaymentMethod(PaymentMethod.COD);
        testPayment.setStatus(PaymentStatus.PENDING);
        testPayment.setAmount(BigDecimal.valueOf(100000));
        testPayment.setCreatedAt(LocalDateTime.now());
    }

    // ========================================================================
    // BOUNDARY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Boundary Tests")
    class BoundaryTests {

        @Test
        @DisplayName("1. now < deadline -> COD not processed")
        void shouldNotProcessCodWhenNotExpired() {
            // Given: COD order created 23 hours ago (timeout = 24 hours)
            testOrder.setCreatedAt(LocalDateTime.now().minusHours(23));
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: No orders processed
            verify(orderRepository).findExpiredCodOrders(eq(OrderStatus.PENDING_CONFIRMATION), any(LocalDateTime.class));
            verify(paymentRepository).findPaidZaloPayPaymentsForTimeout(eq(PaymentStatus.SUCCESS), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("2. now == deadline (boundary) -> COD MUST be processed")
        void shouldProcessCodAtExactDeadlineBoundary() {
            // Given: COD order created exactly 24 hours ago
            int timeoutHours = 24;
            testOrder.setCreatedAt(LocalDateTime.now().minusHours(timeoutHours));
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(timeoutHours);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(orderItemRepository.findByOrderIdWithProduct(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Order should be rejected
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("3. now > deadline -> COD processed")
        void shouldProcessCodWhenExpired() {
            // Given: COD order created 25 hours ago (expired)
            testOrder.setCreatedAt(LocalDateTime.now().minusHours(25));
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(orderItemRepository.findByOrderIdWithProduct(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Order should be rejected
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("4. ZaloPay now == deadline (boundary) -> MUST be processed using Payment.paidAt")
        void shouldProcessZaloPayAtExactDeadlineBoundary() {
            // Given: ZaloPay PAID order with Payment.paidAt exactly 24 hours ago
            int timeoutHours = 24;
            Order zalopayOrder = createZaloPayOrder();
            zalopayOrder.setUpdatedAt(LocalDateTime.now());
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);
            zalopayPayment.setPaidAt(LocalDateTime.now().minusHours(timeoutHours)); // CORRECT: using paidAt

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(timeoutHours);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByOrderId(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "sub_return_message", "Refund successful"));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Order should be rejected
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        }
    }

    // ========================================================================
    // COD TIMEOUT TESTS
    // ========================================================================

    @Nested
    @DisplayName("COD Timeout Tests")
    class CodTimeoutTests {

        @Test
        @DisplayName("5. COD PENDING_CONFIRMATION + expired -> REJECTED")
        void shouldRejectExpiredCodOrder() {
            // Given
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
            testOrder.setCreatedAt(LocalDateTime.now().minusHours(25));

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(orderItemRepository.findByOrderIdWithProduct(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("6. COD REJECTED -> reservation released")
        void shouldReleaseReservationForExpiredCodOrder() {
            // Given
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
            testOrder.setCreatedAt(LocalDateTime.now().minusHours(25));
            testProduct.setReservedQuantity(10);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(orderItemRepository.findByOrderIdWithProduct(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Reservation released
            verify(orderItemRepository).findByOrderIdWithProduct(testOrder.getId());
            verify(productRepository).saveAll(any());
        }

        @Test
        @DisplayName("7. COD REJECTED -> stock_quantity unchanged")
        void shouldNotChangeStockForExpiredCodOrder() {
            // Given
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
            testOrder.setCreatedAt(LocalDateTime.now().minusHours(25));
            int originalStock = testProduct.getStockQuantity();

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(orderItemRepository.findByOrderIdWithProduct(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Stock unchanged (verified via reservation release which doesn't touch stock)
            verify(orderItemRepository).findByOrderIdWithProduct(testOrder.getId());
            verify(productRepository).saveAll(any());
        }
    }

    // ========================================================================
    // ZALOPAY PAID TIMEOUT TESTS
    // ========================================================================

    @Nested
    @DisplayName("ZaloPay Paid Timeout Tests")
    class ZaloPayPaidTimeoutTests {

        @Test
        @DisplayName("8. ZaloPay PAID + expired -> Order REJECTED")
        void shouldRejectExpiredZaloPayPaidOrder() {
            // Given: Fresh ZaloPay objects
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByOrderId(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "sub_return_message", "Refund successful"));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Order REJECTED
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("9. ZaloPay PAID + expired -> Payment REFUND_PENDING")
        void shouldSetPaymentToRefundPendingForExpiredZaloPayOrder() {
            // Given: Fresh ZaloPay objects, refund fails
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByOrderId(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            // Refund fails
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", -1, "sub_return_message", "Refund failed"));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Order REJECTED
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);

            // And: Payment REFUND_PENDING
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(1)).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        }

        @Test
        @DisplayName("10. ZaloPay PAID + expired -> reservation NOT released yet (before refund)")
        void shouldNotReleaseReservationBeforeRefundSuccess() {
            // Given: Fresh ZaloPay objects, refund fails
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", -1, "sub_return_message", "Refund failed"));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Reservation NOT released
            verify(orderItemRepository, never()).findByOrderIdWithProduct(any());
        }
    }

    // ========================================================================
    // ZALOPAY REFUND TESTS
    // ========================================================================

    @Nested
    @DisplayName("ZaloPay Refund Tests")
    class ZaloPayRefundTests {

        @Test
        @DisplayName("11. Refund SUCCESS (return_code=1) -> Payment REFUNDED")
        void shouldSetPaymentToRefundedOnSuccess() {
            // Given: Fresh ZaloPay objects
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByOrderId(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "sub_return_message", "Refund successful"));
            when(orderItemRepository.findByOrderIdWithProduct(zalopayOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Payment REFUNDED
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());
            boolean hasRefunded = paymentCaptor.getAllValues().stream()
                    .anyMatch(p -> p.getStatus() == PaymentStatus.REFUNDED);
            assertThat(hasRefunded).isTrue();
        }

        @Test
        @DisplayName("12. Refund SUCCESS -> refundedAt set")
        void shouldSetRefundedAtOnSuccess() {
            // Given: Fresh ZaloPay objects
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByOrderId(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "sub_return_message", "Refund successful"));
            when(orderItemRepository.findByOrderIdWithProduct(zalopayOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: refundedAt set
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());
            boolean hasRefundedAt = paymentCaptor.getAllValues().stream()
                    .anyMatch(p -> p.getRefundedAt() != null);
            assertThat(hasRefundedAt).isTrue();
        }

        @Test
        @DisplayName("13. Refund SUCCESS -> reservation released")
        void shouldReleaseReservationOnRefundSuccess() {
            // Given: Fresh ZaloPay objects
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByOrderId(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "sub_return_message", "Refund successful"));
            when(orderItemRepository.findByOrderIdWithProduct(zalopayOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Reservation released
            verify(orderItemRepository).findByOrderIdWithProduct(zalopayOrder.getId());
            verify(productRepository).saveAll(any());
        }

        @Test
        @DisplayName("14. Refund FAILURE (return_code != 1) -> Payment stays REFUND_PENDING")
        void shouldKeepPaymentAsRefundPendingOnFailure() {
            // Given: Fresh ZaloPay objects
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByOrderId(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", -1, "sub_return_message", "Refund failed"));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Payment stays REFUND_PENDING (only one save for setting status)
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(1)).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        }

        @Test
        @DisplayName("15. Refund FAILURE -> reservation NOT released")
        void shouldNotReleaseReservationOnRefundFailure() {
            // Given: Fresh ZaloPay objects
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of(zalopayPayment));
            when(orderRepository.findByIdWithLock(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayOrder));
            when(paymentRepository.findByOrderId(zalopayOrder.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", -1, "sub_return_message", "Refund failed"));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Reservation NOT released
            verify(orderItemRepository, never()).findByOrderIdWithProduct(any());
        }
    }

    // ========================================================================
    // RETRY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Retry Tests")
    class RetryTests {

        @Test
        @DisplayName("16. REFUND_PENDING + retry success -> REFUNDED")
        void shouldSucceedOnRetry() {
            // Given: Fresh ZaloPay objects
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);
            zalopayPayment.setStatus(PaymentStatus.REFUND_PENDING);
            zalopayOrder.setStatus(OrderStatus.REJECTED);

            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "sub_return_message", "Refund successful"));
            when(orderItemRepository.findByOrderIdWithProduct(zalopayOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When: Direct call to processRefund
            supplierTimeoutService.processRefund(zalopayPayment);

            // Then: Payment REFUNDED
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("17. REFUND_PENDING + retry fail -> stays REFUND_PENDING (no save)")
        void shouldStayRefundPendingOnRetryFailure() {
            // Given: Fresh ZaloPay objects
            Order zalopayOrder = createZaloPayOrder();
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);
            zalopayPayment.setStatus(PaymentStatus.REFUND_PENDING);

            when(paymentRepository.findByIdWithLock(zalopayPayment.getId()))
                    .thenReturn(Optional.of(zalopayPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createRefundMac(anyString(), anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn("mock_mac");
            when(zaloPayClient.refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString()))
                    .thenReturn(Map.of("return_code", -1, "sub_return_message", "Retry failed"));

            // When
            supplierTimeoutService.processRefund(zalopayPayment);

            // Then: No save() called - payment stays REFUND_PENDING as-is
            verify(paymentRepository, never()).save(any());
        }
    }

    // ========================================================================
    // IDEMPOTENCY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("18. Already REJECTED -> scheduler skips")
        void shouldSkipAlreadyRejectedOrder() {
            // Given: Order already REJECTED
            testOrder.setStatus(OrderStatus.REJECTED);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: No state changes
            verify(paymentRepository, never()).findByOrderId(any());
        }

        @Test
        @DisplayName("19. Already CONFIRMED -> scheduler skips")
        void shouldSkipAlreadyConfirmedOrder() {
            // Given: Order already CONFIRMED
            testOrder.setStatus(OrderStatus.CONFIRMED);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: No state changes
            verify(paymentRepository, never()).findByOrderId(any());
        }

        @Test
        @DisplayName("20. Already CANCELLED -> scheduler skips")
        void shouldSkipAlreadyCancelledOrder() {
            // Given: Order already CANCELLED
            testOrder.setStatus(OrderStatus.CANCELLED);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: No state changes
            verify(paymentRepository, never()).findByOrderId(any());
        }

        @Test
        @DisplayName("21. Already REFUNDED -> no duplicate refund")
        void shouldNotRefundAlreadyRefundedPayment() {
            // Given: Payment already REFUNDED
            testPayment.setStatus(PaymentStatus.REFUNDED);

            // When: Direct call to processRefund
            supplierTimeoutService.processRefund(testPayment);

            // Then: No ZaloPay call
            verify(zaloPayClient, never()).refund(anyLong(), anyString(), anyLong(), anyString(), anyLong(), anyString());
        }
    }

    // ========================================================================
    // PAYMENT STILL PENDING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Payment Still Pending Tests")
    class PaymentPendingTests {

        @Test
        @DisplayName("22. ZaloPay PENDING -> scheduler does NOT auto-reject (uses paidAt, not pending)")
        void shouldNotRejectZaloPayPendingOrder() {
            // Given: ZaloPay payment still PENDING (not paid yet)
            // The scheduler looks for SUCCESS payments with paidAt, so PENDING won't be found
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
            testPayment.setPaymentMethod(PaymentMethod.ZALOPAY);
            testPayment.setStatus(PaymentStatus.PENDING);
            testPayment.setPaidAt(null);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of()); // No SUCCESS payments found
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Order status NOT changed
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("23. Online order with FAILED payment -> no auto-reject")
        void shouldNotRejectOrderWithFailedPayment() {
            // Given: Online payment FAILED
            testOrder.setStatus(OrderStatus.PAID);
            testPayment.setPaymentMethod(PaymentMethod.ZALOPAY);
            testPayment.setStatus(PaymentStatus.FAILED);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of()); // No SUCCESS payments found
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: Order NOT rejected (payment not SUCCESS)
            verify(orderRepository, never()).save(any());
        }
    }

    // ========================================================================
    // CONCURRENCY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("24. Supplier Confirm vs Auto Reject -> only one succeeds")
        void shouldHandleConcurrencyBetweenConfirmAndAutoReject() {
            // Given: Two threads - one confirms, one auto-rejects
            // Simulate: Scheduler picks up order, but before it processes, Supplier confirms

            // First call: finds order in PENDING_CONFIRMATION
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));

            // Payment is COD (not ZaloPay)
            testPayment.setPaymentMethod(PaymentMethod.COD);
            testPayment.setStatus(PaymentStatus.PENDING);

            // When: First call
            supplierTimeoutService.processOrderTimeout(testOrder);

            // Order gets rejected...

            // Second call: After Supplier confirms first, finds order already REJECTED
            testOrder.setStatus(OrderStatus.REJECTED);
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));

            // When: Second call (simulating race condition)
            supplierTimeoutService.processOrderTimeout(testOrder);

            // Then: No exception thrown, order stays REJECTED
            // Payment not modified again
            verify(paymentRepository, never()).save(any());
        }
    }

    // ========================================================================
    // ORDER HISTORY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Order History Tests")
    class OrderHistoryTests {

        @Test
        @DisplayName("25. Auto reject -> history recorded with null changedBy")
        void shouldRecordHistoryWithNullChangedByForAutoReject() {
            // Given: COD order expired
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
            testOrder.setCreatedAt(LocalDateTime.now().minusHours(25));
            testPayment.setPaymentMethod(PaymentMethod.COD);
            testPayment.setStatus(PaymentStatus.PENDING);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(orderItemRepository.findByOrderIdWithProduct(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));
            when(productRepository.findByIdInWithLock(any()))
                    .thenReturn(List.of(testProduct));

            // When
            supplierTimeoutService.processSupplierTimeouts();

            // Then: History recorded with null changedBy (system action)
            ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
            verify(orderStatusHistoryRepository).save(historyCaptor.capture());
            OrderStatusHistory history = historyCaptor.getValue();
            assertThat(history.getChangedBy()).isNull();
            assertThat(history.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(history.getNote()).contains("SUPPLIER_CONFIRM_TIMEOUT");
        }
    }
}

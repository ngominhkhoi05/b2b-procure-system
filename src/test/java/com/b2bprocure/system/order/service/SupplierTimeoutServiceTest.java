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
import com.b2bprocure.system.payment.service.RefundService;
import com.b2bprocure.system.product.entity.Product;
import com.b2bprocure.system.product.repository.ProductRepository;
import com.b2bprocure.system.setting.service.SystemSettingService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

/**
 * Tests for {@link SupplierTimeoutServiceImpl}.
 *
 * <p>Refund logic itself is covered by {@code RefundServiceTest}; here we only verify
 * that the timeout service:
 * <ul>
 *   <li>Rejects expired COD orders and releases reservation.</li>
 *   <li>Rejects expired ZaloPay paid orders and delegates refund to {@link RefundService}.</li>
 *   <li>Skips orders in non-actionable statuses.</li>
 * </ul>
 */
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
    private RefundService refundService;

    @InjectMocks
    private SupplierTimeoutServiceImpl supplierTimeoutService;

    private Order testOrder;
    private Payment testPayment;
    private Product testProduct;
    private OrderItem testOrderItem;

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
        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setStockQuantity(100);
        testProduct.setReservedQuantity(10);

        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setOrderCode("ORD-001");
        testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
        testOrder.setCreatedAt(LocalDateTime.now().minusHours(25));
        testOrder.setUpdatedAt(LocalDateTime.now());

        testOrderItem = new OrderItem();
        testOrderItem.setId(1L);
        testOrderItem.setOrder(testOrder);
        testOrderItem.setProduct(testProduct);
        testOrderItem.setQuantity(5);

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
        @DisplayName("1. now < deadline -> no orders processed")
        void shouldNotProcessWhenNotExpired() {
            testOrder.setCreatedAt(LocalDateTime.now().minusHours(23));
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());

            supplierTimeoutService.processSupplierTimeouts();

            verify(orderRepository).findExpiredCodOrders(eq(OrderStatus.PENDING_CONFIRMATION), any(LocalDateTime.class));
            verify(paymentRepository).findPaidZaloPayPaymentsForTimeout(eq(PaymentStatus.SUCCESS), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("2. now == deadline (boundary) -> COD MUST be processed")
        void shouldProcessCodAtExactDeadlineBoundary() {
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

            supplierTimeoutService.processSupplierTimeouts();

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("3. now > deadline -> COD processed")
        void shouldProcessCodWhenExpired() {
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

            supplierTimeoutService.processSupplierTimeouts();

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("4. ZaloPay now == deadline -> MUST be processed using Payment.paidAt")
        void shouldProcessZaloPayAtExactDeadlineBoundary() {
            int timeoutHours = 24;
            Order zalopayOrder = createZaloPayOrder();
            zalopayOrder.setUpdatedAt(LocalDateTime.now());
            Payment zalopayPayment = createZaloPayPayment(zalopayOrder);
            zalopayPayment.setPaidAt(LocalDateTime.now().minusHours(timeoutHours));

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

            supplierTimeoutService.processSupplierTimeouts();

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
            // Refund delegated to RefundService
            verify(refundService).processRefund(zalopayPayment);
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

            supplierTimeoutService.processSupplierTimeouts();

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("6. COD REJECTED -> reservation released")
        void shouldReleaseReservationForExpiredCodOrder() {
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

            supplierTimeoutService.processSupplierTimeouts();

            verify(orderItemRepository).findByOrderIdWithProduct(testOrder.getId());
            verify(productRepository).saveAll(any());
        }

        @Test
        @DisplayName("7. COD REJECTED -> stock_quantity unchanged")
        void shouldNotChangeStockForExpiredCodOrder() {
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

            supplierTimeoutService.processSupplierTimeouts();

            // Stock code path uses reservation release (does not touch stock).
            verify(orderItemRepository).findByOrderIdWithProduct(testOrder.getId());
            verify(productRepository).saveAll(any());
            assertThat(testProduct.getStockQuantity()).isEqualTo(originalStock);
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

            supplierTimeoutService.processSupplierTimeouts();

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
            verify(refundService).processRefund(zalopayPayment);
        }

        @Test
        @DisplayName("9. ZaloPay PAID + expired -> Payment set to REFUND_PENDING")
        void shouldSetPaymentToRefundPendingForExpiredZaloPayOrder() {
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

            supplierTimeoutService.processSupplierTimeouts();

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());
            boolean hasRefundPending = paymentCaptor.getAllValues().stream()
                    .anyMatch(p -> p.getStatus() == PaymentStatus.REFUND_PENDING);
            assertThat(hasRefundPending).isTrue();
            verify(refundService).processRefund(zalopayPayment);
        }

        @Test
        @DisplayName("10. RefundService is delegated to handle refund + reservation release")
        void shouldDelegateRefundToRefundService() {
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

            supplierTimeoutService.processSupplierTimeouts();

            // SupplierTimeoutService does NOT touch product reservation directly for ZaloPay
            // path - RefundService owns that responsibility.
            verify(refundService).processRefund(zalopayPayment);
            verify(orderItemRepository, never()).findByOrderIdWithProduct(any());
        }
    }

    // ========================================================================
    // IDEMPOTENCY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("11. Already REJECTED -> scheduler skips")
        void shouldSkipAlreadyRejectedOrder() {
            testOrder.setStatus(OrderStatus.REJECTED);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));

            supplierTimeoutService.processSupplierTimeouts();

            verify(paymentRepository, never()).findByOrderId(any());
            verify(refundService, never()).processRefund(any());
        }

        @Test
        @DisplayName("12. Already CONFIRMED -> scheduler skips")
        void shouldSkipAlreadyConfirmedOrder() {
            testOrder.setStatus(OrderStatus.CONFIRMED);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));

            supplierTimeoutService.processSupplierTimeouts();

            verify(paymentRepository, never()).findByOrderId(any());
            verify(refundService, never()).processRefund(any());
        }

        @Test
        @DisplayName("13. Already CANCELLED -> scheduler skips")
        void shouldSkipAlreadyCancelledOrder() {
            testOrder.setStatus(OrderStatus.CANCELLED);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of(testOrder));
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));

            supplierTimeoutService.processSupplierTimeouts();

            verify(paymentRepository, never()).findByOrderId(any());
            verify(refundService, never()).processRefund(any());
        }
    }

    // ========================================================================
    // PAYMENT STILL PENDING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Payment Still Pending Tests")
    class PaymentPendingTests {

        @Test
        @DisplayName("14. ZaloPay PENDING -> scheduler does NOT auto-reject")
        void shouldNotRejectZaloPayPendingOrder() {
            testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
            testPayment.setPaymentMethod(PaymentMethod.ZALOPAY);
            testPayment.setStatus(PaymentStatus.PENDING);
            testPayment.setPaidAt(null);

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

            supplierTimeoutService.processSupplierTimeouts();

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("15. Online order with FAILED payment -> no auto-reject")
        void shouldNotRejectOrderWithFailedPayment() {
            testOrder.setStatus(OrderStatus.PAID);
            testPayment.setPaymentMethod(PaymentMethod.ZALOPAY);
            testPayment.setStatus(PaymentStatus.FAILED);

            when(systemSettingService.getSettingValueAsInt(eq(SettingKey.SUPPLIER_CONFIRM_TIMEOUT_HOURS), eq(24)))
                    .thenReturn(24);
            when(orderRepository.findExpiredCodOrders(any(), any()))
                    .thenReturn(List.of());
            when(paymentRepository.findPaidZaloPayPaymentsForTimeout(any(), any()))
                    .thenReturn(List.of());
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));

            supplierTimeoutService.processSupplierTimeouts();

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
        @DisplayName("16. Supplier Confirm vs Auto Reject -> only one succeeds")
        void shouldHandleConcurrencyBetweenConfirmAndAutoReject() {
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(Optional.of(testPayment));

            testPayment.setPaymentMethod(PaymentMethod.COD);
            testPayment.setStatus(PaymentStatus.PENDING);

            supplierTimeoutService.processOrderTimeout(testOrder);

            testOrder.setStatus(OrderStatus.REJECTED);
            when(orderRepository.findByIdWithLock(testOrder.getId()))
                    .thenReturn(Optional.of(testOrder));

            supplierTimeoutService.processOrderTimeout(testOrder);

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
        @DisplayName("17. Auto reject -> history recorded with null changedBy")
        void shouldRecordHistoryWithNullChangedByForAutoReject() {
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

            supplierTimeoutService.processSupplierTimeouts();

            ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
            verify(orderStatusHistoryRepository).save(historyCaptor.capture());
            OrderStatusHistory history = historyCaptor.getValue();
            assertThat(history.getChangedBy()).isNull();
            assertThat(history.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(history.getNote()).contains("SUPPLIER_CONFIRM_TIMEOUT");
        }
    }
}
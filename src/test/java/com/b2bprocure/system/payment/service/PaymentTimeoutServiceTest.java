package com.b2bprocure.system.payment.service;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
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
import com.b2bprocure.system.zalopay.client.ZaloPayException;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentTimeoutService.
 * Tests payment timeout processing, ZaloPay query, and state transitions.
 */
@ExtendWith(MockitoExtension.class)
class PaymentTimeoutServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ZaloPayConfig zaloPayConfig;

    @Mock
    private ZaloPayClient zaloPayClient;

    @Mock
    private ZaloPaySignatureService signatureService;

    @Mock
    private SystemSettingService systemSettingService;

    @InjectMocks
    private PaymentTimeoutService paymentTimeoutService;

    private Payment testPayment;
    private Order testOrder;
    private Product testProduct;
    private OrderItem testOrderItem;

    @BeforeEach
    void setUp() {
        // Set up test product
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setStockQuantity(100);
        testProduct.setReservedQuantity(10);

        // Set up order item
        testOrderItem = new OrderItem();
        testOrderItem.setId(1L);
        testOrderItem.setProduct(testProduct);
        testOrderItem.setQuantity(10);

        // Set up test order
        testOrder = new Order();
        testOrder.setId(100L);
        testOrder.setStatus(OrderStatus.PENDING_CONFIRMATION);
        testOrder.setBuyerCompany(null); // Simplified for unit test
        testOrder.setSupplierCompany(null);
        testOrder.setCreatedBy(null);

        // Set up test payment
        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setOrder(testOrder);
        testPayment.setPaymentMethod(PaymentMethod.ZALOPAY);
        testPayment.setStatus(PaymentStatus.PENDING);
        testPayment.setAmount(BigDecimal.valueOf(9000));
        testPayment.setAppTransId("260922_100_ABC123");
        testPayment.setExpiredAt(LocalDateTime.now().minusMinutes(5));
        testPayment.setCreatedAt(LocalDateTime.now().minusMinutes(20));
    }

    // ===== Scheduler Tests =====

    @Nested
    @DisplayName("Scheduler Tests")
    class SchedulerTests {

        @Test
        @DisplayName("1. PENDING + not expired -> no query")
        void shouldNotProcessNonExpiredPayments() {
            // Given: Payment is PENDING but not expired
            testPayment.setExpiredAt(LocalDateTime.now().plusMinutes(10));
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of());

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: No query to ZaloPay
            verify(zaloPayClient, never()).queryOrderStatus(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("2. now < expiredAt -> not in findExpiredPayments result")
        void shouldNotFindNonExpiredPayments() {
            // Given: Payment expires in 10 minutes
            testPayment.setExpiredAt(LocalDateTime.now().plusMinutes(10));
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of());

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: findExpiredPayments should return empty list
            verify(paymentRepository).findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any());
            verify(zaloPayClient, never()).queryOrderStatus(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("3. now >= expiredAt -> query ZaloPay")
        void shouldQueryZaloPayForExpiredPayment() {
            // Given: Payment expired 5 minutes ago
            testPayment.setExpiredAt(LocalDateTime.now().minusMinutes(5));
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 3, "return_message", "processing"));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Should query ZaloPay
            verify(zaloPayClient).queryOrderStatus(eq(2553L), eq(testPayment.getAppTransId()), eq("mock_mac"));
        }

        @Test
        @DisplayName("3a. now == expiredAt (boundary) -> must be processed")
        void shouldProcessPaymentAtExactExpirationBoundary() {
            // Given: Payment expires exactly now (boundary case)
            LocalDateTime now = LocalDateTime.now();
            testPayment.setExpiredAt(now);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 3, "return_message", "processing"));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Should query ZaloPay (expiredAt <= now includes equality)
            verify(zaloPayClient).queryOrderStatus(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("4. COD -> scheduler ignores COD payments")
        void shouldIgnoreCodPayments() {
            // Given: COD payment (COD does not have expiredAt set)
            testPayment.setPaymentMethod(PaymentMethod.COD);
            testPayment.setExpiredAt(null);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Should skip COD payment
            verify(zaloPayClient, never()).queryOrderStatus(anyLong(), anyString(), anyString());
        }
    }

    // ===== ZaloPay SUCCESS Tests =====

    @Nested
    @DisplayName("ZaloPay SUCCESS Tests")
    class ZaloPaySuccessTests {

        @Test
        @DisplayName("5. ZaloPay SUCCESS -> Payment SUCCESS")
        void shouldUpdatePaymentToSuccessOnZaloPaySuccess() {
            // Given
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "return_message", "success"));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then
            assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(testPayment.getPaidAt()).isNotNull();
            verify(paymentRepository).save(testPayment);
        }

        @Test
        @DisplayName("6. ZaloPay SUCCESS -> Order PAID")
        void shouldUpdateOrderToPaidOnZaloPaySuccess() {
            // Given
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "return_message", "success"));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then
            assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderRepository).save(testOrder);
        }

        @Test
        @DisplayName("7. paidAt is set to now")
        void shouldSetPaidAtTimestamp() {
            // Given
            testPayment.setPaidAt(null);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "return_message", "success"));

            LocalDateTime beforeCall = LocalDateTime.now();

            // When
            paymentTimeoutService.processExpiredPayments();

            LocalDateTime afterCall = LocalDateTime.now();

            // Then
            assertThat(testPayment.getPaidAt()).isNotNull();
            assertThat(testPayment.getPaidAt()).isBetween(beforeCall, afterCall.plusSeconds(1));
        }

        @Test
        @DisplayName("8. Reservation kept when Order reaches PAID (not CONFIRMED)")
        void shouldKeepReservationUntilConfirmed() {
            // Given: Order is PENDING_CONFIRMATION, after SUCCESS becomes PAID
            testProduct.setReservedQuantity(10);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 1, "return_message", "success"));
            // Note: orderItemRepository NOT stubbed because releaseReservation is not called for SUCCESS case

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Order becomes PAID but reservation is NOT released
            // Reservation release only happens on EXPIRE/FAIL -> CANCELLED
            assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(testProduct.getReservedQuantity()).isEqualTo(10); // Unchanged
            // Verify orderItemRepository was never called (no release happened)
            verify(orderItemRepository, never()).findByOrderId(anyLong());
        }

        @Test
        @DisplayName("9. Query SUCCESS idempotent - no double update")
        void shouldBeIdempotentOnSuccess() {
            // Given: Payment already SUCCESS
            testPayment.setStatus(PaymentStatus.SUCCESS);
            testPayment.setPaidAt(LocalDateTime.now().minusHours(1));
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: No changes, no save
            assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(paymentRepository, never()).save(any());
            verify(zaloPayClient, never()).queryOrderStatus(anyLong(), anyString(), anyString());
        }
    }

    // ===== ZaloPay FAIL Tests =====

    @Nested
    @DisplayName("ZaloPay FAIL Tests")
    class ZaloPayFailTests {

        @Test
        @DisplayName("10. ZaloPay FAIL -> Payment EXPIRED")
        void shouldExpirePaymentOnZaloPayFail() {
            // Given
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 2, "return_message", "fail"));
            when(orderItemRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then
            assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
            verify(paymentRepository).save(testPayment);
        }

        @Test
        @DisplayName("11. ZaloPay FAIL -> Order CANCELLED")
        void shouldCancelOrderOnZaloPayFail() {
            // Given
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 2, "return_message", "fail"));
            when(orderItemRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then
            assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderRepository).save(testOrder);
        }

        @Test
        @DisplayName("12. Reservation released on FAIL")
        void shouldReleaseReservationOnFail() {
            // Given
            testProduct.setReservedQuantity(10);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 2, "return_message", "fail"));
            when(orderItemRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then
            assertThat(testProduct.getReservedQuantity()).isEqualTo(0); // Released
            verify(productRepository).save(testProduct);
        }

        @Test
        @DisplayName("13. stock_quantity NOT reduced on release")
        void shouldNotReduceStockOnRelease() {
            // Given
            testProduct.setStockQuantity(100);
            testProduct.setReservedQuantity(10);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 2, "return_message", "fail"));
            when(orderItemRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Stock quantity unchanged
            assertThat(testProduct.getStockQuantity()).isEqualTo(100);
            // Only reserved reduced
            assertThat(testProduct.getReservedQuantity()).isEqualTo(0);
        }
    }

    // ===== ZaloPay PROCESSING Tests =====

    @Nested
    @DisplayName("ZaloPay PROCESSING Tests")
    class ZaloPayProcessingTests {

        @Test
        @DisplayName("14. PROCESSING -> Payment stays PENDING")
        void shouldNotFailPaymentOnProcessing() {
            // Given
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 3, "return_message", "processing"));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Payment still PENDING
            assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("15. PROCESSING -> Order stays PENDING_CONFIRMATION")
        void shouldNotCancelOrderOnProcessing() {
            // Given
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 3, "return_message", "processing"));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Order still PENDING_CONFIRMATION
            assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("16. PROCESSING -> Reservation not released")
        void shouldNotReleaseReservationOnProcessing() {
            // Given
            testProduct.setReservedQuantity(10);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 3, "return_message", "processing"));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Reservation unchanged
            assertThat(testProduct.getReservedQuantity()).isEqualTo(10);
            verify(productRepository, never()).save(any());
        }
    }

    // ===== Concurrency Tests =====

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("17. Scheduler + Callback same Payment -> no double transition")
        void shouldHandleSchedulerAndCallbackRace() {
            // Given: Callback already processed (status changed to SUCCESS)
            testPayment.setStatus(PaymentStatus.SUCCESS);
            testPayment.setPaidAt(LocalDateTime.now().minusMinutes(1));
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Scheduler skips because not PENDING
            verify(paymentRepository, never()).save(any());
            verify(zaloPayClient, never()).queryOrderStatus(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("18. No duplicate OrderStatusHistory created")
        void shouldNotCreateDuplicateHistory() {
            // Given
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 2, "return_message", "fail"));
            when(orderItemRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Exactly one history record created
            verify(orderStatusHistoryRepository, times(1)).save(any(OrderStatusHistory.class));
        }

        @Test
        @DisplayName("19. Reservation cannot go negative")
        void shouldNotAllowNegativeReservation() {
            // Given: Reserved quantity is less than order item quantity
            testProduct.setReservedQuantity(5);
            testOrderItem.setQuantity(10); // Request more than reserved
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 2, "return_message", "fail"));
            when(orderItemRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Should use Math.max(0, ...) to prevent negative
            assertThat(testProduct.getReservedQuantity()).isGreaterThanOrEqualTo(0);
        }
    }

    // ===== Error Handling Tests =====

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("20. ZaloPay API timeout -> no state change")
        void shouldHandleZaloPayApiTimeout() {
            // Given
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenThrow(new ZaloPayException("Connection timeout"));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Payment still PENDING
            assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("21. Invalid/malformed response -> no state change")
        void shouldHandleMalformedResponse() {
            // Given: Missing return_code
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(new HashMap<>()); // Empty map, no return_code

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Payment still PENDING
            assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("22. Payment already SUCCESS before lock -> skip")
        void shouldSkipAlreadySuccessfulPayment() {
            // Given: Payment changed to SUCCESS between query and lock
            testPayment.setStatus(PaymentStatus.SUCCESS);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: No changes
            verify(zaloPayClient, never()).queryOrderStatus(anyLong(), anyString(), anyString());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("23. Payment already EXPIRED -> skip")
        void shouldSkipAlreadyExpiredPayment() {
            // Given: Payment already EXPIRED
            testPayment.setStatus(PaymentStatus.EXPIRED);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: No changes
            verify(zaloPayClient, never()).queryOrderStatus(anyLong(), anyString(), anyString());
            verify(paymentRepository, never()).save(any());
        }
    }

    // ===== Edge Cases =====

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Payment without appTransId -> mark as EXPIRED directly")
        void shouldExpirePaymentWithoutAppTransId() {
            // Given: No appTransId means ZaloPay was never initiated
            testPayment.setAppTransId(null);
            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(orderItemRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Expire directly without ZaloPay query
            assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
            verify(zaloPayClient, never()).queryOrderStatus(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("Multiple expired payments -> process all")
        void shouldProcessAllExpiredPayments() {
            // Given: Two expired payments
            Payment payment2 = new Payment();
            payment2.setId(2L);
            payment2.setOrder(testOrder);
            payment2.setPaymentMethod(PaymentMethod.ZALOPAY);
            payment2.setStatus(PaymentStatus.PENDING);
            payment2.setAppTransId("260922_200_XYZ789");
            payment2.setExpiredAt(LocalDateTime.now().minusMinutes(3));

            when(paymentRepository.findExpiredPaymentsWithLock(eq(PaymentStatus.PENDING), any()))
                    .thenReturn(List.of(testPayment, payment2));
            when(paymentRepository.findByIdWithLock(testPayment.getId()))
                    .thenReturn(Optional.of(testPayment));
            when(paymentRepository.findByIdWithLock(payment2.getId()))
                    .thenReturn(Optional.of(payment2));
            when(zaloPayConfig.getAppId()).thenReturn("2553");
            when(signatureService.createQueryOrderMac(anyString(), anyString())).thenReturn("mock_mac");
            when(zaloPayClient.queryOrderStatus(anyLong(), anyString(), anyString()))
                    .thenReturn(Map.of("return_code", 2, "return_message", "fail"));
            when(orderItemRepository.findByOrderId(testOrder.getId()))
                    .thenReturn(List.of(testOrderItem));

            // When
            paymentTimeoutService.processExpiredPayments();

            // Then: Both processed
            verify(zaloPayClient, times(2)).queryOrderStatus(anyLong(), anyString(), anyString());
        }
    }
}

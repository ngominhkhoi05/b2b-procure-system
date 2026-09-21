package com.b2bprocure.system.order;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus State Machine Transition Tests")
class OrderStatusTransitionTest {

    @Nested
    @DisplayName("1. Online Payment Transitions")
    class OnlinePaymentTransitions {

        @Test
        @DisplayName("PENDING_CONFIRMATION -> PAID is valid for online payment")
        void testPendingConfirmationToPaid_Valid() {
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.PAID, PaymentMethod.ZALOPAY)).isTrue();
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.PAID, PaymentMethod.MOMO)).isTrue();
        }

        @Test
        @DisplayName("PAID -> CONFIRMED is valid for online payment")
        void testPaidToConfirmed_Valid() {
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CONFIRMED, PaymentMethod.ZALOPAY)).isTrue();
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CONFIRMED, PaymentMethod.MOMO)).isTrue();
        }

        @Test
        @DisplayName("PENDING_CONFIRMATION -> CONFIRMED directly is ILLEGAL for online payment (Supplier cannot confirm unpaid online order)")
        void testPendingConfirmationToConfirmed_Direct_InvalidForOnline() {
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.CONFIRMED, PaymentMethod.ZALOPAY)).isFalse();
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.CONFIRMED, PaymentMethod.MOMO)).isFalse();
        }

        @Test
        @DisplayName("PAID -> REJECTED is valid (Supplier reject after paid -> refund)")
        void testPaidToRejected_Valid() {
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REJECTED, PaymentMethod.ZALOPAY)).isTrue();
        }

        @Test
        @DisplayName("PAID -> CANCELLED is valid (Buyer cancel after paid, before confirm -> refund)")
        void testPaidToCancelled_Valid() {
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED, PaymentMethod.ZALOPAY)).isTrue();
        }
    }

    @Nested
    @DisplayName("2. COD Transitions")
    class CodTransitions {

        @Test
        @DisplayName("PENDING_CONFIRMATION -> CONFIRMED directly is valid for COD")
        void testPendingConfirmationToConfirmed_ValidForCod() {
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.CONFIRMED, PaymentMethod.COD)).isTrue();
        }

        @Test
        @DisplayName("PENDING_CONFIRMATION -> PAID is ILLEGAL for COD (COD never enters PAID status)")
        void testPendingConfirmationToPaid_InvalidForCod() {
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.PAID, PaymentMethod.COD)).isFalse();
        }

        @Test
        @DisplayName("PENDING_CONFIRMATION -> REJECTED is valid for COD")
        void testPendingConfirmationToRejected_ValidForCod() {
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.REJECTED, PaymentMethod.COD)).isTrue();
        }

        @Test
        @DisplayName("PENDING_CONFIRMATION -> CANCELLED is valid for COD (Buyer cancel before confirm)")
        void testPendingConfirmationToCancelled_ValidForCod() {
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.CANCELLED, PaymentMethod.COD)).isTrue();
        }
    }

    @Nested
    @DisplayName("3. Fulfillment Progression (CONFIRMED -> PREPARING -> SHIPPING -> COMPLETED)")
    class FulfillmentProgression {

        @Test
        @DisplayName("CONFIRMED -> PREPARING is valid")
        void testConfirmedToPreparing_Valid() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PREPARING)).isTrue();
        }

        @Test
        @DisplayName("PREPARING -> SHIPPING is valid")
        void testPreparingToShipping_Valid() {
            assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.SHIPPING)).isTrue();
        }

        @Test
        @DisplayName("SHIPPING -> COMPLETED is valid")
        void testShippingToCompleted_Valid() {
            assertThat(OrderStatus.SHIPPING.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        }
    }

    @Nested
    @DisplayName("4. Illegal & Forbidden Transitions")
    class ForbiddenTransitions {

        @Test
        @DisplayName("PENDING_CONFIRMATION -> SHIPPING or COMPLETED is ILLEGAL")
        void testSkipToShippingOrCompleted_Invalid() {
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.SHIPPING)).isFalse();
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED -> CANCELLED is ILLEGAL (Buyer cannot cancel after confirmation)")
        void testConfirmedToCancelled_Invalid() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED -> REJECTED is ILLEGAL (Supplier cannot reject after confirmation)")
        void testConfirmedToRejected_Invalid() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.REJECTED)).isFalse();
        }

        @Test
        @DisplayName("Terminal statuses (COMPLETED, REJECTED, CANCELLED) cannot transition to anything")
        void testTerminalStatuses_CannotTransition() {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(OrderStatus.COMPLETED.canTransitionTo(target)).isFalse();
                assertThat(OrderStatus.REJECTED.canTransitionTo(target)).isFalse();
                assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
            }
        }

        @Test
        @DisplayName("Null target returns false safely")
        void testNullTarget_SafeFalse() {
            assertThat(OrderStatus.PENDING_CONFIRMATION.canTransitionTo(null)).isFalse();
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(null, PaymentMethod.COD)).isFalse();
        }
    }
}

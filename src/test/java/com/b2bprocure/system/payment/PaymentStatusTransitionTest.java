package com.b2bprocure.system.payment;

import com.b2bprocure.system.common.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentStatus State Machine Transition Tests")
class PaymentStatusTransitionTest {

    @Nested
    @DisplayName("1. Valid Transitions from PENDING")
    class PendingTransitions {

        @Test
        @DisplayName("PENDING -> SUCCESS is valid (payment completed)")
        void testPendingToSuccess() {
            assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SUCCESS)).isTrue();
        }

        @Test
        @DisplayName("PENDING -> FAILED is valid (gateway returned failure)")
        void testPendingToFailed() {
            assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        }

        @Test
        @DisplayName("PENDING -> EXPIRED is valid (15m timeout reached)")
        void testPendingToExpired() {
            assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.EXPIRED)).isTrue();
        }

        @Test
        @DisplayName("PENDING -> REFUND_PENDING or REFUNDED directly is ILLEGAL")
        void testPendingToRefund_Invalid() {
            assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REFUND_PENDING)).isFalse();
            assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
        }
    }

    @Nested
    @DisplayName("2. Refund Flow (SUCCESS -> REFUND_PENDING -> REFUNDED)")
    class RefundFlowTransitions {

        @Test
        @DisplayName("SUCCESS -> REFUND_PENDING is valid (refund initiated upon rejection/cancellation)")
        void testSuccessToRefundPending() {
            assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.REFUND_PENDING)).isTrue();
        }

        @Test
        @DisplayName("REFUND_PENDING -> REFUNDED is valid (refund completed by provider)")
        void testRefundPendingToRefunded() {
            assertThat(PaymentStatus.REFUND_PENDING.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("SUCCESS -> REFUNDED directly without REFUND_PENDING is ILLEGAL")
        void testSuccessToRefundedDirectly_Invalid() {
            assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
        }
    }

    @Nested
    @DisplayName("3. Illegal & Backward Transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("REFUNDED cannot transition to SUCCESS or any other status")
        void testRefundedToAny_Invalid() {
            for (PaymentStatus target : PaymentStatus.values()) {
                assertThat(PaymentStatus.REFUNDED.canTransitionTo(target)).isFalse();
            }
        }

        @Test
        @DisplayName("FAILED cannot transition to REFUNDED or SUCCESS")
        void testFailedToAny_Invalid() {
            assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
            assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
        }

        @Test
        @DisplayName("EXPIRED cannot transition to SUCCESS or REFUNDED")
        void testExpiredToAny_Invalid() {
            assertThat(PaymentStatus.EXPIRED.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
            assertThat(PaymentStatus.EXPIRED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
        }

        @Test
        @DisplayName("Null target returns false safely")
        void testNullTarget_SafeFalse() {
            assertThat(PaymentStatus.PENDING.canTransitionTo(null)).isFalse();
        }
    }
}

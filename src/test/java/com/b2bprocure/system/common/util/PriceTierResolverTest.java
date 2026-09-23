package com.b2bprocure.system.common.util;

import com.b2bprocure.system.product.entity.ProductPrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PriceTierResolver}.
 *
 * <p>Covers the business rule:
 * <ol>
 *   <li>Strict match: {@code min <= quantity <= max}</li>
 *   <li>Fallback to last tier when {@code quantity > maxQuantity of last tier}</li>
 *   <li>Returns empty for quantities below all tiers or in gaps</li>
 * </ol>
 */
@DisplayName("PriceTierResolver unit tests")
class PriceTierResolverTest {

    private static ProductPrice tier(int min, Integer max, String price) {
        return new ProductPrice(
                null, null, min, max,
                new BigDecimal(price),
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // Standard three-tier ladder: 1-10 → 100,000; 11-20 → 90,000; 21-30 → 80,000
    private static final List<ProductPrice> LADDER = List.of(
            tier(1, 10, "100000.00"),
            tier(11, 20, "90000.00"),
            tier(21, 30, "80000.00")
    );

    @Nested
    @DisplayName("Normal tier matching (no fallback)")
    class NormalMatching {

        @Test
        @DisplayName("quantity = 1 → tier 1 (100,000)")
        void quantity1() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 1))
                    .contains(new BigDecimal("100000.00"));
        }

        @Test
        @DisplayName("quantity = 10 → tier 1 (100,000)")
        void quantity10() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 10))
                    .contains(new BigDecimal("100000.00"));
        }

        @Test
        @DisplayName("quantity = 11 → tier 2 (90,000)")
        void quantity11() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 11))
                    .contains(new BigDecimal("90000.00"));
        }

        @Test
        @DisplayName("quantity = 20 → tier 2 (90,000)")
        void quantity20() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 20))
                    .contains(new BigDecimal("90000.00"));
        }

        @Test
        @DisplayName("quantity = 21 → tier 3 (80,000)")
        void quantity21() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 21))
                    .contains(new BigDecimal("80000.00"));
        }

        @Test
        @DisplayName("quantity = 30 → tier 3 (80,000)")
        void quantity30() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 30))
                    .contains(new BigDecimal("80000.00"));
        }
    }

    @Nested
    @DisplayName("Fallback to last tier when quantity exceeds last max_quantity")
    class FallbackToLastTier {

        @Test
        @DisplayName("quantity = 31 → last tier (80,000)")
        void quantity31() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 31))
                    .contains(new BigDecimal("80000.00"));
        }

        @Test
        @DisplayName("quantity = 41 → last tier (80,000)")
        void quantity41() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 41))
                    .contains(new BigDecimal("80000.00"));
        }

        @Test
        @DisplayName("quantity = 100 → last tier (80,000)")
        void quantity100() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 100))
                    .contains(new BigDecimal("80000.00"));
        }

        @Test
        @DisplayName("quantity = 9_999 → last tier (80,000)")
        void quantityVeryLarge() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 9_999))
                    .contains(new BigDecimal("80000.00"));
        }
    }

    @Nested
    @DisplayName("Empty result for invalid inputs")
    class EmptyCases {

        @Test
        @DisplayName("quantity = 0 → empty")
        void quantityZero() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, 0)).isEmpty();
        }

        @Test
        @DisplayName("quantity = null → empty")
        void quantityNull() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, null)).isEmpty();
        }

        @Test
        @DisplayName("quantity = -5 → empty")
        void quantityNegative() {
            assertThat(PriceTierResolver.resolveUnitPrice(LADDER, -5)).isEmpty();
        }

        @Test
        @DisplayName("empty tier list → empty")
        void emptyTiers() {
            assertThat(PriceTierResolver.resolveUnitPrice(Collections.emptyList(), 5))
                    .isEmpty();
        }

        @Test
        @DisplayName("null tier list → empty")
        void nullTiers() {
            assertThat(PriceTierResolver.resolveUnitPrice(null, 5)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Gap and below-all-tiers cases (must NOT use fallback)")
    class GapAndBelowCases {

        @Test
        @DisplayName("Tiers with a gap: quantity in gap → empty (no fallback)")
        void quantityInGap() {
            // Tiers: 1-10 → 100k; 20-30 → 80k. No tier covers 11-19.
            List<ProductPrice> withGap = List.of(
                    tier(1, 10, "100000.00"),
                    tier(20, 30, "80000.00")
            );

            // quantity = 15: NOT > 30 (last max), so no fallback. Returns empty.
            assertThat(PriceTierResolver.resolveUnitPrice(withGap, 15)).isEmpty();
        }

        @Test
        @DisplayName("Tier that starts at 10: quantity 5 (below all) → empty")
        void quantityBelowAllTiers() {
            List<ProductPrice> startsAt10 = List.of(
                    tier(10, 50, "100000.00")
            );
            // quantity = 5 < 10, NOT > 50 → no fallback
            assertThat(PriceTierResolver.resolveUnitPrice(startsAt10, 5)).isEmpty();
        }

        @Test
        @DisplayName("Tier that starts at 10: quantity 5 with quantity 5 below min → empty")
        void quantityBelowMinButFallbackWouldStillBeInvalid() {
            // Even if quantity > last max, fallback only applies if we can
            // identify a valid last tier. Here quantity = 1 < 10 (min) and 1 < 50
            // (max), so it falls within min-max range? No — min is 10 so quantity
            // does not satisfy exact match. 1 > 50 is false. So no fallback.
            List<ProductPrice> startsAt10 = List.of(
                    tier(10, 50, "100000.00")
            );
            assertThat(PriceTierResolver.resolveUnitPrice(startsAt10, 1)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Open upper bound (max_quantity = null) is treated as last tier with fallback")
    class OpenUpperBound {

        @Test
        @DisplayName("Tier with max=null is exact match for any quantity >= min")
        void openMaxIsExactMatch() {
            List<ProductPrice> openMax = List.of(
                    tier(1, null, "50000.00")
            );
            // quantity = 1 matches exact (1 <= quantity <= null is unbounded)
            assertThat(PriceTierResolver.resolveUnitPrice(openMax, 1))
                    .contains(new BigDecimal("50000.00"));
            // quantity = 999 also matches exact, so no fallback path needed.
            assertThat(PriceTierResolver.resolveUnitPrice(openMax, 999))
                    .contains(new BigDecimal("50000.00"));
        }
    }
}

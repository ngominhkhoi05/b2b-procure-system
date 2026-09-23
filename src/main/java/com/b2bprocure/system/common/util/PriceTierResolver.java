package com.b2bprocure.system.common.util;

import com.b2bprocure.system.product.entity.ProductPrice;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Shared resolver for Product Price Tier selection.
 *
 * <p>Business rule (AGENTS.md §11 + Step 3 update):
 * <ol>
 *   <li>Find the tier whose range strictly contains the quantity:
 *       {@code min_quantity <= quantity <= max_quantity}.</li>
 *   <li>If found, return that tier's unit price.</li>
 *   <li>If not found AND {@code quantity > max_quantity} of the LAST tier
 *       (the tier with the highest {@code min_quantity}):
 *       return the LAST tier's unit price as a fallback.</li>
 *   <li>Otherwise (e.g. quantity is below all tiers, or falls into a gap
 *       between tiers): return empty. Caller decides whether this is an
 *       error or simply "price not available".</li>
 * </ol>
 *
 * <p>NOTE: The {@code max_quantity} of the last tier is treated as a marker
 * for tier membership, NOT as a hard upper bound on the quantity that can
 * be purchased. Suppliers are NOT required to enter {@code NULL} for the
 * last tier's {@code max_quantity}.
 *
 * <p>This class is stateless; safe to be reused across Cart and Checkout
 * services.
 */
public final class PriceTierResolver {

    private PriceTierResolver() {
        // utility class
    }

    /**
     * Resolve the unit price for the given quantity using the supplied
     * (already sorted ascending by {@code minQuantity}) tier list.
     *
     * @param tiers    sorted list of {@link ProductPrice} for a single product;
     *                 may be empty or null (treated as empty).
     * @param quantity the requested quantity; must be {@code > 0}.
     * @return optional unit price. Empty when no tier matches and quantity
     *         does not exceed the last tier's max.
     */
    public static Optional<BigDecimal> resolveUnitPrice(List<ProductPrice> tiers, Integer quantity) {
        if (tiers == null || tiers.isEmpty() || quantity == null || quantity <= 0) {
            return Optional.empty();
        }

        // Step 1: exact tier match (min <= quantity <= max, with max nullable)
        Optional<ProductPrice> exactTier = tiers.stream()
                .filter(t -> t.getMinQuantity() != null && quantity >= t.getMinQuantity()
                        && (t.getMaxQuantity() == null || quantity <= t.getMaxQuantity()))
                .findFirst();

        if (exactTier.isPresent()) {
            return Optional.of(exactTier.get().getUnitPrice());
        }

        // Step 2: fallback to last tier only when quantity strictly exceeds
        // its max_quantity. Tier ordering is by minQuantity ASC, so the
        // "last" tier is the one with the highest minQuantity.
        ProductPrice lastTier = tiers.stream()
                .filter(t -> t.getMinQuantity() != null)
                .max(Comparator.comparing(ProductPrice::getMinQuantity))
                .orElse(null);

        if (lastTier != null && lastTier.getMaxQuantity() != null
                && quantity > lastTier.getMaxQuantity()) {
            return Optional.of(lastTier.getUnitPrice());
        }

        // quantity falls into a gap or below all tiers
        return Optional.empty();
    }
}

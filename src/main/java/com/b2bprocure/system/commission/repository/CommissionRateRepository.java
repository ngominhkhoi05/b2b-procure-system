package com.b2bprocure.system.commission.repository;

import com.b2bprocure.system.commission.entity.CommissionRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRateRepository extends JpaRepository<CommissionRate, Long> {

    /**
     * Find all commission rates that were effective at a given point in time.
     * Effective means: effective_from <= completedAt.
     * Results are ordered by effective_from descending (newest first).
     *
     * @param completedAt The timestamp to check effective rates against
     * @return List of rates ordered by effective_from DESC
     */
    @Query("""
        SELECT c FROM CommissionRate c
        WHERE c.effectiveFrom <= :completedAt
        ORDER BY c.effectiveFrom DESC
        """)
    List<CommissionRate> findActiveRatesAt(@Param("completedAt") LocalDateTime completedAt);

    /**
     * Find the active commission rate at a given point in time.
     * Returns the rate with the most recent effective_from that is still <= completedAt.
     *
     * @param completedAt The timestamp to check effective rates against
     * @return The active rate, or empty if no rate is effective at that time
     */
    default Optional<CommissionRate> findActiveRateAt(LocalDateTime completedAt) {
        List<CommissionRate> rates = findActiveRatesAt(completedAt);
        if (rates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rates.get(0));
    }

    /**
     * Step 8 — Admin Commission Rate list: all rates ordered by effectiveFrom DESC.
     * Simple paginated query — no date filter needed for admin view.
     */
    Page<CommissionRate> findAllByOrderByEffectiveFromDesc(Pageable pageable);
}

package com.b2bprocure.system.commission.service;

import com.b2bprocure.system.commission.entity.CommissionRate;
import com.b2bprocure.system.commission.repository.CommissionRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for querying commission rates.
 * Provides focused, read-only access to commission rate data.
 */
@Service
@RequiredArgsConstructor
public class CommissionRateQueryService {

    private final CommissionRateRepository commissionRateRepository;

    /**
     * Find the active commission rate at a given point in time.
     * Active means: effective_from <= completedAt.
     * Returns the rate with the most recent effective_from that is still <= completedAt.
     *
     * @param completedAt The timestamp to check effective rates against
     * @return The active rate, or empty if no rate is effective at that time
     */
    public Optional<CommissionRate> findActiveRateAt(LocalDateTime completedAt) {
        return commissionRateRepository.findActiveRateAt(completedAt);
    }
}

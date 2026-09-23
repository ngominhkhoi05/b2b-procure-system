package com.b2bprocure.system.admin.service;

import com.b2bprocure.system.admin.dto.CommissionRateResponse;
import com.b2bprocure.system.admin.dto.CreateCommissionRateRequest;
import com.b2bprocure.system.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Step 8 — Admin Commission Rate management.
 *
 * Provides list and create operations for commission rate records.
 * Historical rates are append-only — existing rates are not deleted or updated.
 */
public interface AdminCommissionRateService {

    /**
     * List all commission rates ordered by {@code effectiveFrom DESC}.
     * @param pageable pagination + sort (default to effectiveFrom DESC)
     */
    PageResponse<CommissionRateResponse> listRates(Pageable pageable);

    /**
     * Create a new commission rate. The current authenticated admin
     * is recorded as the creator.
     */
    CommissionRateResponse createRate(CreateCommissionRateRequest request);
}

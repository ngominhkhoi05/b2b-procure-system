package com.b2bprocure.system.admin.service;

import com.b2bprocure.system.admin.dto.AdminStatisticsResponse;

import java.time.LocalDate;

/**
 * Step 8 — Admin Statistics overview.
 */
public interface AdminStatisticsService {

    /**
     * Compute overview statistics over an optional date range.
     *
     * <p>Date semantics:
     * <ul>
     *   <li>{@code fromDate} inclusive (start-of-day)</li>
     *   <li>{@code toDate} inclusive — service converts to next-day-start exclusive</li>
     * </ul>
     *
     * <p>Financial values come from the snapshots stored on each Order
     * (subtotal, commission_amount). No live re-computation of commission.
     */
    AdminStatisticsResponse getOverview(LocalDate fromDate, LocalDate toDate);
}

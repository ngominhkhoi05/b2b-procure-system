package com.b2bprocure.system.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Step 8 — Admin Statistics overview response.
 *
 * Aggregates order counts and financial metrics from the orders table.
 * All monetary values come from pre-computed snapshots on Order entities
 * (no re-calculation or live commission computation).
 *
 * Date filter semantics: fromDate inclusive, toDate exclusive (next-day start).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminStatisticsResponse {

    // Order counts
    private long totalOrders;
    private long completedOrders;
    private long pendingConfirmationOrders;
    private long cancelledOrders;
    private long rejectedOrders;

    // Financial metrics (from ORDER snapshots — not recalculated)
    private BigDecimal totalOrderValue;
    private BigDecimal totalCommission;

    // Date range applied (echoed back)
    private LocalDate fromDate;
    private LocalDate toDate;
}

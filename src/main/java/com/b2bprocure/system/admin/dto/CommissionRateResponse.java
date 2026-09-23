package com.b2bprocure.system.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Step 8 — Commission Rate response for Admin view.
 *
 * Exposes the rate and its temporal metadata. Does not expose
 * sensitive data — only the admin-facing fields needed for rate management.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommissionRateResponse {

    private Long id;

    /**
     * Commission rate as a percentage value, e.g. 5 = 5%.
     */
    private BigDecimal rate;

    /**
     * The datetime from which this rate becomes effective.
     */
    private LocalDateTime effectiveFrom;

    private LocalDateTime createdAt;

    /**
     * ID of the admin user who created this rate.
     */
    private Long createdById;

    /**
     * Full name of the admin user who created this rate.
     */
    private String createdByFullName;
}

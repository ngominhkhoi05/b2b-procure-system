package com.b2bprocure.system.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Step 8 — Request to create a new commission rate.
 *
 * Rate convention: e.g. 5 means 5%, 7 means 7%.
 * No maximum enforcement — DB column is DECIMAL(5,2) which allows up to 999.99.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommissionRateRequest {

    /**
     * Commission rate as a percentage value, e.g. 5 = 5%.
     * Must be non-negative.
     */
    @NotNull(message = "Rate is required")
    @PositiveOrZero(message = "Rate must be zero or positive")
    private BigDecimal rate;

    /**
     * The datetime from which this rate becomes effective.
     * When an Order is COMPLETED, the active rate is determined by
     * finding the rate with the most recent effectiveFrom that is still <= order completion time.
     * Required.
     */
    @NotNull(message = "Effective from date is required")
    private LocalDateTime effectiveFrom;
}

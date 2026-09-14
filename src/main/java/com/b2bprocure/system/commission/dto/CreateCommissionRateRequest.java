package com.b2bprocure.system.commission.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommissionRateRequest {

    @NotNull(message = "Commission rate is required")
    @PositiveOrZero(message = "Commission rate must be zero or positive")
    private BigDecimal rate;

    @NotNull(message = "Effective from date is required")
    private LocalDateTime effectiveFrom;

}

package com.b2bprocure.system.commission.dto;

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
public class CommissionRateResponse {

    private Long id;
    private BigDecimal rate;
    private LocalDateTime effectiveFrom;
    private LocalDateTime createdAt;
    private Long createdBy;

}

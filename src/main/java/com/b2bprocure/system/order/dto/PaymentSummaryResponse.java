package com.b2bprocure.system.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment summary exposed in Order Detail response.
 *
 * Only exposes safe, non-sensitive payment information. Provider-specific data
 * such as {@code providerTransactionId} and {@code appTransId} are intentionally
 * NOT included here because they are not required by Order History/Query.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSummaryResponse {

    private String paymentMethod;
    private String paymentStatus;
    private BigDecimal amount;
    private LocalDateTime paidAt;

}

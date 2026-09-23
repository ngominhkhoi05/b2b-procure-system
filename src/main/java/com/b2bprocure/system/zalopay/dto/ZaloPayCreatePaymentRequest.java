package com.b2bprocure.system.zalopay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for initiating ZaloPay payment (Step 2 of two-step checkout).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request DTO for initiating ZaloPay payment")
public class ZaloPayCreatePaymentRequest {

    @NotNull(message = "Payment ID is required")
    @Schema(description = "ID of the payment record created during checkout", example = "1")
    private Long paymentId;

    @NotNull(message = "Order ID is required")
    @Schema(description = "ID of the order associated with this payment", example = "1")
    private Long orderId;
}

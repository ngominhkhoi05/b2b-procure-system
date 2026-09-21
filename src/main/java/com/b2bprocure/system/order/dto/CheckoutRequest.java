package com.b2bprocure.system.order.dto;

import com.b2bprocure.system.common.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request DTO for Checkout from Cart")
public class CheckoutRequest {

    @NotEmpty(message = "Cart item IDs must not be empty")
    @Schema(description = "List of selected CartItem IDs belonging to the same supplier", example = "[1, 2]")
    private List<Long> cartItemIds;

    @NotNull(message = "Payment method is required")
    @Schema(description = "Selected payment method: ZALOPAY, MOMO, or COD", example = "ZALOPAY")
    private PaymentMethod paymentMethod;

}

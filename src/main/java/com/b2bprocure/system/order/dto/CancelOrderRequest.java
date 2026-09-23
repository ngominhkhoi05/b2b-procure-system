package com.b2bprocure.system.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request DTO for Buyer Cancelling an Order")
public class CancelOrderRequest {

    @Size(max = 500, message = "Cancel reason must not exceed 500 characters")
    @Schema(description = "Optional reason explaining why the order was cancelled", example = "Thay đổi nhu cầu mua hàng")
    private String reason;
}

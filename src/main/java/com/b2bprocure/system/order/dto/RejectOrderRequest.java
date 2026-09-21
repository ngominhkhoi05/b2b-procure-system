package com.b2bprocure.system.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request DTO for Supplier Rejecting an Order")
public class RejectOrderRequest {

    @NotBlank(message = "Reject reason is required")
    @Size(max = 500, message = "Reject reason must not exceed 500 characters")
    @Schema(description = "Mandatory reason explaining why the order was rejected", example = "Sản phẩm tạm thời hết hàng hoặc không đủ quy cách")
    private String reason;
}

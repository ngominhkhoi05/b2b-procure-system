package com.b2bprocure.system.order.dto;

import com.b2bprocure.system.common.enums.OrderStatus;
import com.b2bprocure.system.common.enums.PaymentMethod;
import com.b2bprocure.system.common.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Response DTO for Checkout")
public class CheckoutResponse {

    @Schema(description = "ID of the created Order", example = "1")
    private Long orderId;

    @Schema(description = "Unique Order Code", example = "ORD-1726900000000-A1B2C3")
    private String orderCode;

    @Schema(description = "Selected payment method: COD, ZALOPAY, MOMO", example = "COD")
    private PaymentMethod paymentMethod;

    @Schema(description = "Current payment status", example = "PENDING")
    private PaymentStatus paymentStatus;

    @Schema(description = "Current order status", example = "PENDING_CONFIRMATION")
    private OrderStatus orderStatus;

    @Schema(description = "Order subtotal amount", example = "1800000.00")
    private BigDecimal subtotal;

    @Schema(description = "Total payable amount", example = "1800000.00")
    private BigDecimal totalAmount;

    @Schema(description = "ID of the created Payment record", example = "1")
    private Long paymentId;

    @Schema(description = "Unique Payment Code", example = "PAY-1726900000000-D4E5F6")
    private String paymentCode;

    @Schema(description = "Expiration timestamp for online payments (null for COD)", example = "2026-09-21T10:15:00")
    private LocalDateTime paymentExpiredAt;

    @Schema(description = "Payment URL for online payments (e.g., ZaloPay). For ZaloPay, call POST /api/v1/checkout/zalopay/create-payment to get the actual URL.", example = "https://...")
    private String paymentUrl;

    @Schema(description = "Timestamp when the order was created")
    private LocalDateTime createdAt;

}

package com.b2bprocure.system.order.controller;

import com.b2bprocure.system.common.response.ApiResponse;
import com.b2bprocure.system.order.dto.CheckoutRequest;
import com.b2bprocure.system.order.dto.CheckoutResponse;
import com.b2bprocure.system.order.service.CheckoutService;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreatePaymentRequest;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreatePaymentResponse;
import com.b2bprocure.system.zalopay.service.ZaloPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
@Tag(name = "Checkout", description = "Checkout and Order Creation APIs")
@PreAuthorize("hasRole('BUYER')")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final ZaloPayService zaloPayService;

    @Operation(
            summary = "Execute Checkout",
            description = "Process checkout for selected cart items belonging to a single supplier. " +
                    "Acquires pessimistic write locks on products, validates stock, reserves quantities, " +
                    "calculates tier pricing, snapshots order items and shipping info, and generates order and payment."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Checkout completed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failure, insufficient stock, mixed suppliers, or invalid tier price",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Only buyers can perform checkout",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cart item or product not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(@Valid @RequestBody CheckoutRequest request) {
        CheckoutResponse response = checkoutService.checkout(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Checkout completed successfully", response));
    }

    @Operation(
            summary = "Create ZaloPay Payment",
            description = "Initiates ZaloPay payment for an existing order. " +
                    "Creates ZaloPay order and returns payment URL for buyer to complete payment. " +
                    "Step 2 of the two-step ZaloPay checkout flow."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "ZaloPay payment initiated successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or ZaloPay order creation failed",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Order does not belong to current buyer",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Order or payment not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "ZaloPay API unavailable or returned error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PostMapping("/zalopay/create-payment")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<ZaloPayCreatePaymentResponse>> createZaloPayPayment(
            @Valid @RequestBody ZaloPayCreatePaymentRequest request
    ) {
        ZaloPayCreatePaymentResponse response = zaloPayService.initiatePayment(
                request.getPaymentId(), request.getOrderId());
        return ResponseEntity.ok(ApiResponse.success("ZaloPay payment initiated", response));
    }
}

package com.b2bprocure.system.order.controller;

import com.b2bprocure.system.common.response.ApiResponse;
import com.b2bprocure.system.order.dto.OrderResponse;
import com.b2bprocure.system.order.dto.RejectOrderRequest;
import com.b2bprocure.system.order.service.OrderLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supplier/orders")
@RequiredArgsConstructor
@Tag(name = "Supplier Orders", description = "Supplier Order Lifecycle Management APIs")
@PreAuthorize("hasRole('SUPPLIER')")
public class SupplierOrderController {

    private final OrderLifecycleService orderLifecycleService;

    @Operation(summary = "Confirm Order", description = "Supplier confirms an order (COD in PENDING_CONFIRMATION or Online in PAID). Atomically deducts stock and reservation.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order confirmed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid order or payment status",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Supplier does not own this order",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(@PathVariable Long orderId) {
        OrderResponse response = orderLifecycleService.confirmOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order confirmed successfully", response));
    }

    @Operation(summary = "Reject Order", description = "Supplier rejects an unconfirmed order with a mandatory reason. Releases reservation for COD or retains for Online Paid.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order rejected successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status or missing reject reason",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Supplier does not own this order",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody RejectOrderRequest request
    ) {
        OrderResponse response = orderLifecycleService.rejectOrder(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Order rejected successfully", response));
    }

    @Operation(summary = "Update Order to PREPARING", description = "Supplier transitions a CONFIRMED order to PREPARING.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order transitioned to PREPARING",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid state transition",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{orderId}/preparing")
    public ResponseEntity<ApiResponse<OrderResponse>> updateToPreparing(@PathVariable Long orderId) {
        OrderResponse response = orderLifecycleService.updateToPreparing(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order transitioned to PREPARING successfully", response));
    }

    @Operation(summary = "Update Order to SHIPPING", description = "Supplier transitions a PREPARING order to SHIPPING.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order transitioned to SHIPPING",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid state transition",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{orderId}/shipping")
    public ResponseEntity<ApiResponse<OrderResponse>> updateToShipping(@PathVariable Long orderId) {
        OrderResponse response = orderLifecycleService.updateToShipping(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order transitioned to SHIPPING successfully", response));
    }

    @Operation(summary = "Complete Order", description = "Supplier transitions a SHIPPING order to COMPLETED. Updates COD payment to SUCCESS.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order completed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid state transition",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{orderId}/complete")
    public ResponseEntity<ApiResponse<OrderResponse>> updateToCompleted(@PathVariable Long orderId) {
        OrderResponse response = orderLifecycleService.updateToCompleted(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order completed successfully", response));
    }
}

package com.b2bprocure.system.order.controller;

import com.b2bprocure.system.common.response.ApiResponse;
import com.b2bprocure.system.common.response.PageResponse;
import com.b2bprocure.system.order.dto.CancelOrderRequest;
import com.b2bprocure.system.order.dto.OrderDetailResponse;
import com.b2bprocure.system.order.dto.OrderResponse;
import com.b2bprocure.system.order.dto.OrderStatusHistoryResponse;
import com.b2bprocure.system.order.service.OrderLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Buyer and Shared Order Management APIs")
public class OrderController {

    private final OrderLifecycleService orderLifecycleService;

    @Operation(
            summary = "List Orders (Step 7 — Order History / Query)",
            description = "Retrieve a paginated, filtered list of Orders visible to the current user. " +
                    "Buyer sees only their own orders. Supplier sees only orders containing products from their company. " +
                    "Admin sees all. Default sort: createdAt DESC. Page size is clamped to 100."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Orders retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameter",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('BUYER', 'SUPPLIER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @Parameter(description = "Filter by Order status, e.g. PENDING_CONFIRMATION, SHIPPING, COMPLETED")
            @RequestParam(required = false) String status,

            @Parameter(description = "Filter by payment method, e.g. COD, ZALOPAY")
            @RequestParam(required = false) String paymentMethod,

            @Parameter(description = "Filter by payment status, e.g. PENDING, SUCCESS, REFUND_PENDING")
            @RequestParam(required = false) String paymentStatus,

            @Parameter(description = "Inclusive lower bound on order.createdAt (yyyy-MM-dd)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "Inclusive upper bound on order.createdAt (yyyy-MM-dd). Internally converted to exclusive next-day start.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<OrderResponse> response = orderLifecycleService.getOrders(
                status, paymentMethod, paymentStatus, fromDate, toDate, pageable);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", response));
    }

    @Operation(summary = "Cancel Order", description = "Buyer cancels an unconfirmed order. Releases reservation for COD or retains for Online Paid.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order cancelled successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status for cancellation",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Buyer does not own this order",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) CancelOrderRequest request
    ) {
        OrderResponse response = orderLifecycleService.cancelOrder(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", response));
    }

    @Operation(summary = "Get Order Detail", description = "Retrieve order details including items, payment summary and status history. " +
            "Accessible by Buyer owner, Supplier owner, or Admin. Unauthorized access returns 404 (does not leak existence).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order detail retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found (also returned when access is denied)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('BUYER', 'SUPPLIER', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderDetail(@PathVariable Long orderId) {
        OrderDetailResponse response = orderLifecycleService.getOrderDetail(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order detail retrieved successfully", response));
    }

    @Operation(summary = "Get Order Status History", description = "Retrieve audit status history for an order.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order status history retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/{orderId}/history")
    @PreAuthorize("hasAnyRole('BUYER', 'SUPPLIER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getOrderStatusHistory(@PathVariable Long orderId) {
        List<OrderStatusHistoryResponse> response = orderLifecycleService.getOrderStatusHistory(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order status history retrieved successfully", response));
    }
}

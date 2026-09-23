package com.b2bprocure.system.zalopay.controller;

import com.b2bprocure.system.common.response.ApiResponse;
import com.b2bprocure.system.zalopay.dto.ZaloPayCallbackRequest;
import com.b2bprocure.system.zalopay.service.ZaloPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ZaloPay callback endpoint.
 * NO JWT authentication required - protected by ZaloPay MAC signature verification.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/zalopay")
@RequiredArgsConstructor
@Tag(name = "ZaloPay", description = "ZaloPay Payment Integration")
public class ZaloPayCallbackController {

    private final ZaloPayService zaloPayService;

    @Operation(
            summary = "ZaloPay Payment Callback",
            description = "Receives payment callback from ZaloPay server. " +
                    "No JWT authentication required - protected by ZaloPay MAC signature verification. " +
                    "Processes successful payments and updates order status."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Callback processed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid callback data",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handleCallback(@RequestBody ZaloPayCallbackRequest callbackRequest) {
        log.info("Received ZaloPay callback");
        return zaloPayService.handleCallback(callbackRequest);
    }

}

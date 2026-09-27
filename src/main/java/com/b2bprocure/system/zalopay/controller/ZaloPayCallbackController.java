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
 *
 * Two paths are exposed for compatibility:
 *   1. POST /api/v1/payments/zalopay/callback — production path (configurable
 *      via ZALOPAY_CALLBACK_URL).
 *   2. POST /callback — ZaloPay sandbox always posts here regardless of the
 *      registered callback URL. We accept both so dev/sandbox works without
 *      environment tweaks.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "ZaloPay", description = "ZaloPay Payment Integration")
public class ZaloPayCallbackController {

    private final ZaloPayService zaloPayService;

    @Operation(
            summary = "ZaloPay Payment Callback (production path)",
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
    @PostMapping(value = "/api/v1/payments/zalopay/callback",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handleCallback(@RequestBody ZaloPayCallbackRequest callbackRequest) {
        log.info("Received ZaloPay callback (production path)");
        return zaloPayService.handleCallback(callbackRequest);
    }

    /**
     * Sandbox-only alias. ZaloPay's sandbox environment strips the path from
     * the registered callback URL and always posts to <host>/callback. This
     * route simply re-uses the same handler so sandbox tests work without
     * environment changes.
     */
    @Operation(
            summary = "ZaloPay Payment Callback (sandbox alias)",
            description = "Alias of POST /api/v1/payments/zalopay/callback for ZaloPay sandbox, " +
                    "which always posts to <host>/callback regardless of the registered URL."
    )
    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handleCallbackSandbox(@RequestBody ZaloPayCallbackRequest callbackRequest) {
        log.info("Received ZaloPay callback (sandbox path /callback)");
        return zaloPayService.handleCallback(callbackRequest);
    }

}

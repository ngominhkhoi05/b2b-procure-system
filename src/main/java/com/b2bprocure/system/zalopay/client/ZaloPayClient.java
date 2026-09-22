package com.b2bprocure.system.zalopay.client;

import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreateOrderResponse;
import com.b2bprocure.system.zalopay.service.ZaloPaySignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for communicating with ZaloPay Sandbox API.
 * Uses Spring RestClient.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZaloPayClient {

    private final ZaloPayConfig zaloPayConfig;
    private final ZaloPaySignatureService signatureService;

    /**
     * Create a ZaloPay order.
     *
     * @param appTransId Unique transaction ID (format: yyMMdd_xxx, max 40 chars)
     * @param appUser   Username/identifier of the user
     * @param amount    Amount in VND (long)
     * @param item      JSON array string of items
     * @param embedData JSON string of extra data
     * @param description Description of the order
     * @return ZaloPayCreateOrderResponse
     * @throws ZaloPayException if the API call fails
     */
    public ZaloPayCreateOrderResponse createOrder(String appTransId, String appUser,
                                                  long amount, String item,
                                                  String embedData, String description) {
        long appTime = System.currentTimeMillis();

        // Build request params
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("app_id", zaloPayConfig.getAppId());
        params.put("app_trans_id", appTransId);
        params.put("app_user", appUser);
        params.put("app_time", appTime);
        params.put("amount", amount);
        params.put("item", item);
        params.put("embed_data", embedData);
        params.put("description", description);
        params.put("bank_code", "zalopayapp");
        params.put("callback_url", zaloPayConfig.getCallbackUrl());

        // Create MAC signature using key1
        String mac = signatureService.createOrderMac(
                zaloPayConfig.getAppId(),
                appTransId,
                appUser,
                amount,
                appTime,
                embedData,
                item
        );
        params.put("mac", mac);

        log.info("Creating ZaloPay order: appTransId={}, amount={}", appTransId, amount);

        try {
            RestClient restClient = RestClient.create();

            // ZaloPay expects application/x-www-form-urlencoded
            String response = restClient.post()
                    .uri(zaloPayConfig.getEndpoint())
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildFormUrlEncoded(params))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response1) -> {
                                throw new ZaloPayException(
                                        "ZaloPay API client error: " + response1.getStatusCode());
                            })
                    .onStatus(HttpStatusCode::is5xxServerError,
                            (request, response1) -> {
                                throw new ZaloPayException(
                                        "ZaloPay API server error: " + response1.getStatusCode());
                            })
                    .body(String.class);

            log.info("ZaloPay create order response: {}", response);

            ZaloPayCreateOrderResponse parsed = parseResponse(response);
            return parsed;

        } catch (HttpClientErrorException e) {
            log.error("ZaloPay API HTTP client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZaloPayException("ZaloPay API HTTP client error: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.error("ZaloPay API HTTP server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZaloPayException("ZaloPay API HTTP server error: " + e.getMessage(), e);
        } catch (ZaloPayException e) {
            throw e;
        } catch (Exception e) {
            log.error("ZaloPay API call failed: {}", e.getMessage(), e);
            throw new ZaloPayException("ZaloPay API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build application/x-www-form-urlencoded body string.
     */
    private String buildFormUrlEncoded(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(urlEncode(key)).append("=").append(urlEncode(String.valueOf(value)));
        });
        return sb.toString();
    }

    /**
     * Simple URL encoding for form data.
     */
    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Query ZaloPay order status.
     * Uses the query API endpoint to check transaction status.
     *
     * @param appId ZaloPay App ID
     * @param appTransId Transaction ID used when creating the order
     * @param mac HMAC-SHA256 signature for the query request
     * @return Map containing ZaloPay response data
     * @throws ZaloPayException if the API call fails
     */
    public Map<String, Object> queryOrderStatus(long appId, String appTransId, String mac) {
        log.info("Querying ZaloPay order status: appTransId={}", appTransId);

        try {
            RestClient restClient = RestClient.create();

            String response = restClient.post()
                    .uri(zaloPayConfig.getQueryEndpoint())
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildFormUrlEncoded(Map.of(
                            "app_id", appId,
                            "app_trans_id", appTransId,
                            "mac", mac
                    )))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, resp) -> {
                                throw new ZaloPayException(
                                        "ZaloPay query API client error: " + resp.getStatusCode());
                            })
                    .onStatus(HttpStatusCode::is5xxServerError,
                            (request, resp) -> {
                                throw new ZaloPayException(
                                        "ZaloPay query API server error: " + resp.getStatusCode());
                            })
                    .body(String.class);

            log.info("ZaloPay query order status response: {}", response);

            return parseQueryResponse(response);

        } catch (HttpClientErrorException e) {
            log.error("ZaloPay query API HTTP client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZaloPayException("ZaloPay query API HTTP client error: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.error("ZaloPay query API HTTP server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZaloPayException("ZaloPay query API HTTP server error: " + e.getMessage(), e);
        } catch (ZaloPayException e) {
            throw e;
        } catch (Exception e) {
            log.error("ZaloPay query API call failed: {}", e.getMessage(), e);
            throw new ZaloPayException("ZaloPay query API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse query response into a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseQueryResponse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse ZaloPay query response: {}", json, e);
            throw new ZaloPayException("Failed to parse ZaloPay query response: " + e.getMessage(), e);
        }
    }

    /**
     * Process ZaloPay refund request.
     * Uses the refund API endpoint to request refund for a paid transaction.
     *
     * @param appId ZaloPay App ID
     * @param zpTransId ZaloPay transaction ID (zp_trans_id)
     * @param amount Refund amount in VND
     * @param description Reason for refund
     * @param timestamp Request timestamp in milliseconds
     * @param mac HMAC-SHA256 signature for the refund request
     * @return Map containing ZaloPay response data
     * @throws ZaloPayException if the API call fails
     */
    public Map<String, Object> refund(long appId, String zpTransId, long amount,
                                       String description, long timestamp, String mac) {
        log.info("Processing ZaloPay refund: zpTransId={}, amount={}", zpTransId, amount);

        try {
            RestClient restClient = RestClient.create();

            String response = restClient.post()
                    .uri(zaloPayConfig.getRefundEndpoint())
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildFormUrlEncoded(Map.of(
                            "app_id", appId,
                            "zp_trans_id", zpTransId,
                            "amount", amount,
                            "description", description,
                            "timestamp", timestamp,
                            "mac", mac
                    )))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, resp) -> {
                                throw new ZaloPayException(
                                        "ZaloPay refund API client error: " + resp.getStatusCode());
                            })
                    .onStatus(HttpStatusCode::is5xxServerError,
                            (request, resp) -> {
                                throw new ZaloPayException(
                                        "ZaloPay refund API server error: " + resp.getStatusCode());
                            })
                    .body(String.class);

            log.info("ZaloPay refund response: {}", response);

            return parseRefundResponse(response);

        } catch (HttpClientErrorException e) {
            log.error("ZaloPay refund API HTTP client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZaloPayException("ZaloPay refund API HTTP client error: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.error("ZaloPay refund API HTTP server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZaloPayException("ZaloPay refund API HTTP server error: " + e.getMessage(), e);
        } catch (ZaloPayException e) {
            throw e;
        } catch (Exception e) {
            log.error("ZaloPay refund API call failed: {}", e.getMessage(), e);
            throw new ZaloPayException("ZaloPay refund API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse refund response into a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRefundResponse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse ZaloPay refund response: {}", json, e);
            throw new ZaloPayException("Failed to parse ZaloPay refund response: " + e.getMessage(), e);
        }
    }

    /**
     * Parse ZaloPay JSON response (create order).
     */
    private ZaloPayCreateOrderResponse parseResponse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, ZaloPayCreateOrderResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse ZaloPay response: {}", json, e);
            throw new ZaloPayException("Failed to parse ZaloPay API response: " + e.getMessage(), e);
        }
    }
}

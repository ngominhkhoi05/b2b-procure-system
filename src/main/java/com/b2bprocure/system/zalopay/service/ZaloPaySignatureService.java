package com.b2bprocure.system.zalopay.service;

import com.b2bprocure.system.zalopay.config.ZaloPayConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZaloPaySignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ZaloPayConfig zaloPayConfig;

    /**
     * Create MAC for ZaloPay create order request.
     * Input string format: app_id|app_trans_id|app_user|amount|app_time|embed_data|item
     * Uses key1 for signing.
     */
    public String createOrderMac(String appId, String appTransId, String appUser,
                                long amount, long appTime, String embedData, String item) {
        String data = String.format("%s|%s|%s|%d|%d|%s|%s",
                appId, appTransId, appUser, amount, appTime, embedData, item);
        return computeMac(data, zaloPayConfig.getKey1());
    }

    /**
     * Verify callback MAC using key2.
     * Compares HMAC-SHA256(key2, callbackData) with providedMac.
     */
    public boolean verifyCallbackMac(String callbackData, String providedMac) {
        if (callbackData == null || providedMac == null) {
            return false;
        }
        String computedMac = computeMac(callbackData, zaloPayConfig.getKey2());
        boolean isValid = computedMac.equalsIgnoreCase(providedMac);
        if (!isValid) {
            log.warn("ZaloPay callback MAC verification failed. Expected: {}, Got: {}",
                    computedMac, providedMac);
        }
        return isValid;
    }

    /**
     * Create MAC for ZaloPay query order request.
     * Input string format: app_id|app_trans_id
     * Uses key2 for signing.
     */
    public String createQueryOrderMac(String appId, String appTransId) {
        String data = String.format("%s|%s", appId, appTransId);
        return computeMac(data, zaloPayConfig.getKey2());
    }

    /**
     * Compute HMAC-SHA256 hex string from data and key.
     */
    private String computeMac(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA256: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

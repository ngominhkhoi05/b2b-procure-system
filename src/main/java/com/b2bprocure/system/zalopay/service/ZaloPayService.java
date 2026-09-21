package com.b2bprocure.system.zalopay.service;

import com.b2bprocure.system.zalopay.dto.ZaloPayCallbackRequest;
import com.b2bprocure.system.zalopay.dto.ZaloPayCreatePaymentResponse;

import java.util.Map;

public interface ZaloPayService {

    /**
     * Initiate ZaloPay payment for an existing order.
     * Creates ZaloPay order and updates Payment record.
     *
     * @param paymentId the payment ID
     * @param orderId   the order ID
     * @return response containing paymentUrl and paymentId
     */
    ZaloPayCreatePaymentResponse initiatePayment(Long paymentId, Long orderId);

    /**
     * Handle ZaloPay callback.
     * Verifies signature, updates Payment and Order status.
     *
     * @param callbackRequest the raw callback request from ZaloPay
     * @return response to return to ZaloPay server
     */
    Map<String, Object> handleCallback(ZaloPayCallbackRequest callbackRequest);
}

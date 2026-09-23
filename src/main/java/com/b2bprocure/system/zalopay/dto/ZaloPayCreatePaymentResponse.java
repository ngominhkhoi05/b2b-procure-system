package com.b2bprocure.system.zalopay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response to frontend after initiating ZaloPay payment.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZaloPayCreatePaymentResponse {

    private Long paymentId;
    private String paymentUrl;
    private String orderCode;
}

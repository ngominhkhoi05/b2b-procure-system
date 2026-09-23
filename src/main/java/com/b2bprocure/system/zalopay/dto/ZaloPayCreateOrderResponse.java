package com.b2bprocure.system.zalopay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response from ZaloPay Sandbox Create Order API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZaloPayCreateOrderResponse {

    @JsonProperty("return_code")
    private int returnCode;

    @JsonProperty("return_message")
    private String returnMessage;

    @JsonProperty("sub_return_code")
    private int subReturnCode;

    @JsonProperty("sub_return_message")
    private String subReturnMessage;

    @JsonProperty("order_url")
    private String orderUrl;

    @JsonProperty("zp_trans_token")
    private String zpTransToken;

    @JsonProperty("order_token")
    private String orderToken;

    @JsonProperty("qr_code")
    private String qrCode;

    public boolean isSuccess() {
        return returnCode == 1 && subReturnCode == 1;
    }
}

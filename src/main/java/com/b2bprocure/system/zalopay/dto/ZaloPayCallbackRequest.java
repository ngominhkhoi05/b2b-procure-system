package com.b2bprocure.system.zalopay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Raw callback request from ZaloPay server.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZaloPayCallbackRequest {

    @JsonProperty("data")
    private String data;

    @JsonProperty("mac")
    private String mac;

    @JsonProperty("type")
    private int type;
}

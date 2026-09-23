package com.b2bprocure.system.zalopay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parsed callback data from ZaloPay.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZaloPayCallbackData {

    @JsonProperty("app_id")
    private long appId;

    @JsonProperty("app_trans_id")
    private String appTransId;

    @JsonProperty("app_time")
    private long appTime;

    @JsonProperty("app_user")
    private String appUser;

    @JsonProperty("amount")
    private long amount;

    @JsonProperty("embed_data")
    private String embedData;

    @JsonProperty("item")
    private String item;

    @JsonProperty("zp_trans_id")
    private long zpTransId;

    @JsonProperty("server_time")
    private long serverTime;

    @JsonProperty("channel")
    private int channel;

    @JsonProperty("merchant_user_id")
    private String merchantUserId;

    @JsonProperty("user_fee_amount")
    private long userFeeAmount;

    @JsonProperty("discount_amount")
    private long discountAmount;
}

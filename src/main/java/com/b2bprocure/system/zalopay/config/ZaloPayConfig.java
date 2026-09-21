package com.b2bprocure.system.zalopay.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "zalopay")
@Getter
@Setter
public class ZaloPayConfig {

    private String appId;
    private String key1;
    private String key2;
    private String endpoint;
    private String callbackUrl;
}

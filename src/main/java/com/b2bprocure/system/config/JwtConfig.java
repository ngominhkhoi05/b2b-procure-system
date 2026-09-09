package com.b2bprocure.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    private String secret = "defaultSecretKeyMustBeAtLeast256BitsLongForHS256AlgorithmSecurityNeeds!";
    private long expirationMs = 86400000L; // 24 hours
    private String header = "Authorization";
    private String prefix = "Bearer ";

}

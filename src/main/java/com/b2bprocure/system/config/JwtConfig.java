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

    /**
     * Allowed clock skew (in seconds) for OAuth2 ID token validation.
     *
     * Nimbus' {@code DefaultJWTClaimsVerifier} compares the token's
     * {@code iat} / {@code exp} against the system clock with this
     * tolerance. The default is 0, which fails on hosts whose system
     * clock is noticeably out of sync with the upstream provider
     * (e.g. when the OS clock was set manually or NTP is not running).
     *
     * 900s (15 minutes) is large enough to cover the worst observed
     * local skew in this project (~2h40m) and is still strictly less
     * than the token's own validity window (1h), so a tampered token
     * with a wildly wrong {@code iat} will still be rejected.
     */
    private long allowedClockSkewSeconds = 900L;

}

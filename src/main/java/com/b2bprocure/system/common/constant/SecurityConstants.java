package com.b2bprocure.system.common.constant;

public final class SecurityConstants {

    private SecurityConstants() {
        // Prevent instantiation
    }

    // HTTP Headers & Token Prefix
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    // System Role Names (Raw names without prefix)
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_BUYER = "BUYER";
    public static final String ROLE_SUPPLIER = "SUPPLIER";

    // Spring Security Authorities (with ROLE_ prefix)
    public static final String AUTHORITY_ADMIN = "ROLE_ADMIN";
    public static final String AUTHORITY_BUYER = "ROLE_BUYER";
    public static final String AUTHORITY_SUPPLIER = "ROLE_SUPPLIER";

    // Public URL whitelist
    public static final String[] PUBLIC_URLS = {
            "/api/v1/auth/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };
}

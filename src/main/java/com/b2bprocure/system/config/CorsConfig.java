package com.b2bprocure.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * CORS configuration for B2B Procure System.
 *
 * Origins are read from `app.cors.allowed-origins` in application*.yml,
 * with environment variable override via CORS_ALLOWED_ORIGINS (comma-separated).
 *
 * Examples:
 *   application-dev.yml:
 *     app:
 *       cors:
 *         allowed-origins:
 *           - http://localhost:3000
 *           - http://localhost:5173
 *
 *   Production via env:
 *     CORS_ALLOWED_ORIGINS=https://app.example.com,https://www.example.com
 *
 *   application.yml (default empty):
 *     app:
 *       cors:
 *         allowed-origins:
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.cors")
public class CorsConfig {

    /**
     * Allowed origins as a comma-separated string. Empty = no CORS (browser will block cross-origin requests).
     * Examples:
     *   - YAML list (Spring Boot splits automatically): - http://localhost:3000
     *   - Env var (comma-separated): CORS_ALLOWED_ORIGINS=http://a.com,http://b.com
     *
     * Use explicit origins. NEVER use "*" together with allowCredentials=true.
     */
    private List<String> allowedOrigins = Collections.emptyList();

    /**
     * Comma-separated string of allowed HTTP methods. Defaults to GET/POST/PUT/PATCH/DELETE/OPTIONS.
     */
    private String allowedMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS";

    /**
     * Comma-separated string of allowed request headers. "*" = all.
     */
    private String allowedHeaders = "*";

    /**
     * Comma-separated string of headers exposed to the browser.
     */
    private String exposedHeaders = "Authorization,Content-Type,X-Requested-With";

    /**
     * Whether to allow credentials (cookies, authorization headers).
     */
    private boolean allowCredentials = true;

    /**
     * Preflight cache duration in seconds.
     */
    private long maxAge = 3600L;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Resolve origins: prefer list, but allow comma-separated String for env-driven config.
        List<String> origins = resolveOrigins();
        if (!origins.isEmpty()) {
            // Use setAllowedOrigins (not addAllowedOriginPattern) to keep strict allow-list semantics.
            config.setAllowedOrigins(origins);
        }

        config.setAllowedMethods(splitCsv(allowedMethods));
        config.setAllowedHeaders(splitCsv(allowedHeaders));
        config.setExposedHeaders(splitCsv(exposedHeaders));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply CORS to all API paths.
        source.registerCorsConfiguration("/api/**", config);
        // Also cover Swagger / OpenAPI if served from a different origin (rare, but safe).
        source.registerCorsConfiguration("/v3/api-docs/**", config);
        source.registerCorsConfiguration("/swagger-ui/**", config);

        return source;
    }

    /**
     * Spring Boot can bind `allowed-origins` in two ways:
     *   1. As a YAML list (auto-split into List<String>).
     *   2. As a single comma-separated string from env (CORS_ALLOWED_ORIGINS).
     *
     * If list binding produced a single element that contains a comma, split it further.
     * If binding produced an empty list, return empty.
     */
    private List<String> resolveOrigins() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new java.util.ArrayList<>();
        for (String entry : allowedOrigins) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            // Split on comma in case the value came from a single env-var string.
            if (trimmed.contains(",")) {
                for (String part : trimmed.split(",")) {
                    String p = part.trim();
                    if (!p.isEmpty()) result.add(p);
                }
            } else {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

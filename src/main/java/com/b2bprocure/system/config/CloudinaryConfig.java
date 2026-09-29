package com.b2bprocure.system.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Provides a singleton {@link Cloudinary} client built from environment variables.
 *
 * Credentials are injected via {@link Value} so Spring resolves
 * {@code ${CLOUDINARY_CLOUD_NAME}} / {@code ${CLOUDINARY_API_KEY}} /
 * {@code ${CLOUDINARY_API_SECRET}} through the property placeholder
 * resolver at startup. If any of them is missing, the application context
 * fails fast with a clear "Could not resolve placeholder" message instead
 * of letting an unresolved placeholder literal flow into the Cloudinary
 * SDK and surface as a cryptic URI parse error at first upload.
 *
 * Per-profile folder override and the upload size cap live in
 * {@link CloudinaryProperties} (non-credential, no placeholder concerns).
 */
@Configuration
public class CloudinaryConfig {

    @Value("${CLOUDINARY_CLOUD_NAME}")
    private String cloudName;

    @Value("${CLOUDINARY_API_KEY}")
    private String apiKey;

    @Value("${CLOUDINARY_API_SECRET}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        // @Value already guarantees these are non-null. Trim guards against
        // accidental whitespace coming from an .env file or shell paste.
        String name    = requireNonBlank(cloudName, "CLOUDINARY_CLOUD_NAME").trim();
        String key     = requireNonBlank(apiKey,    "CLOUDINARY_API_KEY").trim();
        String secret  = requireNonBlank(apiSecret, "CLOUDINARY_API_SECRET").trim();

        return new Cloudinary(Map.of(
            "cloud_name", name,
            "api_key",    key,
            "api_secret", secret,
            // Always request HTTPS URLs from Cloudinary.
            "secure",     true
        ));
    }

    private static String requireNonBlank(String value, String envVar) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Cloudinary credential '" + envVar + "' is not configured. "
              + "Set the CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET environment variables.");
        }
        return value;
    }
}
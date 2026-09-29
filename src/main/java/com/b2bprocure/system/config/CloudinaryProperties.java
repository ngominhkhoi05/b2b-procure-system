package com.b2bprocure.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds non-credential Cloudinary knobs from the {@code app.cloudinary.*}
 * block in application.yml.
 *
 * Credentials (cloud name, API key, API secret) are intentionally NOT
 * bound here — they are injected into {@link CloudinaryConfig} via
 * {@code @Value("${...}")} so Spring resolves the environment-variable
 * placeholders at startup and fails fast if any credential is missing.
 * {@code @ConfigurationProperties} does not resolve placeholders, so
 * putting credentials here would let a placeholder literal flow into
 * the Cloudinary SDK and surface as a cryptic URI parse error at first
 * upload.
 *
 * Required env var (with safe default):
 *   CLOUDINARY_FOLDER         default "b2b-procure"
 *
 * Optional YAML key (has sensible default):
 *   app.cloudinary.max-bytes  default 5 MB
 */
@Configuration
@ConfigurationProperties(prefix = "app.cloudinary")
@Getter
@Setter
public class CloudinaryProperties {

    /**
     * Folder under the Cloudinary cloud where uploads are stored.
     * Per-profile YAML can override to e.g. "b2b-procure/dev" so dev
     * uploads don't pollute prod.
     */
    private String folder = "b2b-procure";

    /**
     * Hard cap enforced server-side. Default 5 MB.
     * Spring's spring.servlet.multipart.max-file-size must be set >= this value.
     */
    private long maxBytes = 5_242_880L;
}
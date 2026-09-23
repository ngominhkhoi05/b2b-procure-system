package com.b2bprocure.system.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

/**
 * OAuth2 / OIDC JWT decoder configuration.
 *
 * <p>Spring Boot 4 / Spring Security 7 auto-configures an
 * {@link NimbusJwtDecoder} per {@link ClientRegistration} for the
 * {@code oauth2Login()} flow. By default the decoder enforces
 * {@code exp} and {@code iat} against the system clock with a tolerance
 * of <strong>0 seconds</strong>. Whenever the local clock is noticeably
 * out of sync with the upstream provider (e.g. a host whose OS clock
 * was set manually, or where NTP is not running), Nimbus rejects the
 * Google ID token with:</p>
 *
 * <pre>
 * invalid_id_token: The ID Token contains invalid claims: {iat=...}
 * </pre>
 *
 * <p>The user is bounced to the generic OAuth2 failure path, which
 * manifests on the frontend as a misleading "Full authentication is
 * required to access this resource" — actually coming from the
 * application's {@code AuthenticationEntryPoint} for the subsequent
 * request after the redirect.</p>
 *
 * <p>This configuration replaces Spring Boot's default
 * {@link JwtDecoderFactory} with one that wires the same Nimbus
 * decoders with a relaxed {@link JwtTimestampValidator} clock skew
 * (configurable via {@code jwt.allowedClockSkewSeconds}, default 15
 * minutes). The skew stays strictly less than the token's 1-hour
 * validity window so a tampered token with wildly incorrect
 * {@code iat} values is still rejected by the signature verification
 * step.</p>
 *
 * <p>Application JWTs (issued by
 * {@link com.b2bprocure.system.security.JwtTokenProvider}) are NOT
 * affected by this decoder — they are validated by
 * {@link com.b2bprocure.system.security.JwtAuthenticationFilter} using
 * the application secret directly.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OAuth2JwtDecoderConfig {

    private final JwtConfig jwtConfig;

    /**
     * Replaces Spring Boot's default {@link JwtDecoderFactory} bean.
     */
    @Bean
    public JwtDecoderFactory<ClientRegistration> jwtDecoderFactory() {
        return new RelaxedJwtDecoderFactory(jwtConfig);
    }

    /**
     * Custom factory implementation.
     *
     * <p>For each {@link ClientRegistration} we:
     * <ol>
     *   <li>Build the standard Nimbus decoder via {@code withJwkSetUri}.</li>
     *   <li>Replace the default validator chain with one that uses
     *       {@link JwtValidators#createDefault()} (issuer + audience)
     *       and a {@link JwtTimestampValidator} configured with the
     *       relaxed {@code allowedClockSkewSeconds} clock skew.</li>
     * </ol>
     */
    static final class RelaxedJwtDecoderFactory implements JwtDecoderFactory<ClientRegistration> {

        private final long allowedClockSkewSeconds;

        RelaxedJwtDecoderFactory(JwtConfig jwtConfig) {
            this.allowedClockSkewSeconds = jwtConfig.getAllowedClockSkewSeconds();
            log.info("Configured OAuth2 JwtDecoderFactory with allowedClockSkewSeconds={}",
                    allowedClockSkewSeconds);
        }

        @Override
        public JwtDecoder createDecoder(ClientRegistration registration) {
            String jwkSetUri = registration.getProviderDetails().getJwkSetUri();

            NimbusJwtDecoder decoder;
            if (jwkSetUri == null || jwkSetUri.isBlank()) {
                log.warn("Client registration {} has no jwkSetUri; falling back to issuer-based decoder",
                        registration.getRegistrationId());
                decoder = NimbusJwtDecoder
                        .withIssuerLocation(registration.getProviderDetails().getIssuerUri())
                        .build();
            } else {
                decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            }

            // Replace the default validator chain (issuer + expiration) with one
            // that accepts a relaxed clock skew so an out-of-sync host clock
            // does not reject otherwise valid Google ID tokens.
            OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefault();
            OAuth2TokenValidator<Jwt> timestampValidator =
                    new JwtTimestampValidator(java.time.Duration.ofSeconds(allowedClockSkewSeconds));

            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    List.of(issuerValidator, timestampValidator)));

            return decoder;
        }
    }
}

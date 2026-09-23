package com.b2bprocure.system.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 failure handler.
 *
 * Spring Security's {@code oauth2Login()} DSL invokes the configured failure
 * handler when the OAuth2 Authentication Provider rejects the
 * authorization-code/id-token exchange. The default handler redirects the
 * browser to {@code /login?error}, which leaves the user on the backend's
 * port (e.g. 8080) instead of the frontend application.
 *
 * This handler mirrors the success handler: it redirects the browser to
 * the application's {@code OAUTH2_REDIRECT_URI} with a
 * {@code status=ERROR} query parameter carrying the sanitised error
 * code/message. The frontend {@code OAuth2RedirectView} recognises that
 * status and shows a friendly toast before routing the user back to
 * {@code /login}.
 */
@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.authorized-redirect-uri:http://localhost:3000/oauth2/redirect}")
    private String redirectUri;

    private String getSanitizedRedirectUri() {
        if (redirectUri == null) {
            return "http://localhost:3000/oauth2/redirect";
        }
        String clean = redirectUri.replaceAll("[\\r\\n]", "").trim();
        if (clean.isBlank() || clean.contains("/login/oauth2/code/")) {
            return "http://localhost:3000/oauth2/redirect";
        }
        return clean;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to target URL.");
            return;
        }

        // The exception.getMessage() typically contains the upstream OAuth2 error
        // description (e.g. "invalid_id_token"). Sanitise to avoid header injection
        // by stripping CR/LF before redirecting.
        String rawMessage = exception.getMessage();
        String safeMessage = rawMessage != null
                ? rawMessage.replaceAll("[\\r\\n]", " ")
                : "oauth2_authentication_failed";
        // Avoid absurdly long query strings.
        if (safeMessage.length() > 256) {
            safeMessage = safeMessage.substring(0, 256);
        }

        log.warn("OAuth2 authentication failed: {}", safeMessage);

        String targetUrl = UriComponentsBuilder.fromUriString(getSanitizedRedirectUri())
                .queryParam("status", "ERROR")
                .queryParam("error", safeMessage)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

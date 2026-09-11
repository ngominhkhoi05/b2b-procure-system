package com.b2bprocure.system.security;

import com.b2bprocure.system.authaccount.entity.AuthAccount;
import com.b2bprocure.system.authaccount.repository.AuthAccountRepository;
import com.b2bprocure.system.common.enums.AuthProvider;
import com.b2bprocure.system.config.JwtConfig;
import com.b2bprocure.system.user.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthAccountRepository authAccountRepository;
    private final JwtConfig jwtConfig;
    private final OAuth2LinkStateStore oauth2LinkStateStore;

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
    @org.springframework.transaction.annotation.Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to target URL.");
            return;
        }

        try {
            String targetUrl = determineTargetUrl(request, response, authentication);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } catch (Exception ex) {
            log.error("Exception occurred during OAuth2 authentication success handling: ", ex);
            String errorMsg = ex.getMessage() != null ? ex.getMessage().replaceAll("[\\r\\n]", " ") : "internal_error";
            String fallbackUrl = UriComponentsBuilder.fromUriString(getSanitizedRedirectUri())
                    .queryParam("status", "ERROR")
                    .queryParam("error", errorMsg)
                    .build().toUriString();
            getRedirectStrategy().sendRedirect(request, response, fallbackUrl);
        }
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        String baseRedirectUri = getSanitizedRedirectUri();
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            log.warn("Authentication is not an instance of OAuth2AuthenticationToken: {}", authentication.getClass());
            return UriComponentsBuilder.fromUriString(baseRedirectUri)
                    .queryParam("error", "unsupported_authentication_type")
                    .build().toUriString();
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        AuthProvider authProvider;
        try {
            authProvider = AuthProvider.valueOf(registrationId.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Unknown or unsupported OAuth2 provider registration ID: {}", registrationId);
            return UriComponentsBuilder.fromUriString(baseRedirectUri)
                    .queryParam("error", "unsupported_provider")
                    .queryParam("provider", registrationId)
                    .build().toUriString();
        }

        // Section 14: providerUserId MUST come from "sub" claim of Google, NOT email
        String providerUserId = oAuth2User.getAttribute("sub");
        if (providerUserId == null || providerUserId.isBlank()) {
            providerUserId = oAuth2User.getName();
        }

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String avatarUrl = oAuth2User.getAttribute("picture");

        // Check if this OAuth2 flow is an account linking operation
        String state = request.getParameter("state");
        Long linkingUserId = (state != null) ? oauth2LinkStateStore.getAndRemovePendingOAuth2State(state) : null;

        if (linkingUserId != null) {
            log.info("Processing OAuth2 link callback for user id: {}, provider: {}, providerUserId: {}",
                    linkingUserId, authProvider, providerUserId);

            // Check if Google account is already linked to ANY user
            Optional<AuthAccount> existingAccountOpt = authAccountRepository
                    .findByProviderAndProviderUserId(authProvider, providerUserId);
            if (existingAccountOpt.isPresent()) {
                User linkedUser = existingAccountOpt.get().getUser();
                if (linkedUser != null && linkedUser.getId().equals(linkingUserId)) {
                    log.info("Google account {} is already linked to user id: {}", providerUserId, linkingUserId);
                    return UriComponentsBuilder.fromUriString(baseRedirectUri)
                            .queryParam("status", "ALREADY_LINKED")
                            .build().toUriString();
                } else {
                    log.warn("Google account {} is already linked to a different user id: {}", providerUserId,
                            linkedUser != null ? linkedUser.getId() : "unknown");
                    return UriComponentsBuilder.fromUriString(baseRedirectUri)
                            .queryParam("status", "LINK_ERROR")
                            .queryParam("error", "account_already_linked_to_another_user")
                            .build().toUriString();
                }
            }

            // Check if this user is already linked to this provider
            Optional<AuthAccount> userGoogleAccountOpt = authAccountRepository
                    .findByUserIdAndProvider(linkingUserId, authProvider);
            if (userGoogleAccountOpt.isPresent()) {
                log.warn("User id {} is already linked to a Google account", linkingUserId);
                return UriComponentsBuilder.fromUriString(baseRedirectUri)
                        .queryParam("status", "LINK_ERROR")
                        .queryParam("error", "user_already_linked_to_different_account")
                        .build().toUriString();
            }

            // Record verified identity in server store & issue linkToken
            oauth2LinkStateStore.recordVerifiedIdentity(linkingUserId, authProvider, providerUserId, email);
            String linkToken = jwtTokenProvider.generateLinkToken(authProvider.name(), providerUserId, email, linkingUserId);

            log.info("Google identity verified for linking to user id: {}. Redirecting with status LINK_READY.", linkingUserId);
            return UriComponentsBuilder.fromUriString(baseRedirectUri)
                    .queryParam("status", "LINK_READY")
                    .queryParam("linkToken", linkToken)
                    .build().toUriString();
        }

        // Standard login / registration flow
        log.info("Processing OAuth2 login for provider: {}, providerUserId: {}, email: {}",
                authProvider, providerUserId, email);

        Optional<AuthAccount> authAccountOpt = authAccountRepository
                .findByProviderAndProviderUserId(authProvider, providerUserId);

        if (authAccountOpt.isPresent()) {
            User user = authAccountOpt.get().getUser();
            log.info("Found existing AuthAccount linked to User id: {}, username: {}", user.getId(), user.getUsername());

            UserPrincipal userPrincipal = UserPrincipal.create(user);
            String accessToken = jwtTokenProvider.generateToken(userPrincipal);

            return UriComponentsBuilder.fromUriString(baseRedirectUri)
                    .queryParam("status", "SUCCESS")
                    .queryParam("accessToken", accessToken)
                    .queryParam("tokenType", "Bearer")
                    .queryParam("userId", user.getId())
                    .queryParam("username", user.getUsername())
                    .queryParam("role", userPrincipal.getRole())
                    .queryParam("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "")
                    .queryParam("expiresIn", jwtConfig.getExpirationMs())
                    .build().toUriString();
        } else {
            log.info("No AuthAccount found for provider: {} and providerUserId: {}. Generating temporary registration token and redirecting with NEED_REGISTER status.",
                    authProvider, providerUserId);

            String registrationToken = jwtTokenProvider.generateRegistrationToken(
                    authProvider.name(),
                    providerUserId,
                    email,
                    name
            );

            return UriComponentsBuilder.fromUriString(baseRedirectUri)
                    .queryParam("status", "NEED_REGISTER")
                    .queryParam("registrationToken", registrationToken)
                    .build().toUriString();
        }
    }

}

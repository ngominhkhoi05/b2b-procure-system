package com.b2bprocure.system.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.StringUtils;

@Slf4j
public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;
    private final OAuth2LinkStateStore linkStateStore;
    private final JwtTokenProvider jwtTokenProvider;

    public CustomOAuth2AuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2LinkStateStore linkStateStore,
            JwtTokenProvider jwtTokenProvider) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
        this.linkStateStore = linkStateStore;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authRequest = defaultResolver.resolve(request);
        return process(authRequest, request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authRequest = defaultResolver.resolve(request, clientRegistrationId);
        return process(authRequest, request);
    }

    private OAuth2AuthorizationRequest process(OAuth2AuthorizationRequest authRequest, HttpServletRequest request) {
        if (authRequest == null) {
            return null;
        }

        // Spring Security's DefaultOAuth2AuthorizationRequestResolver expands {action} from request.getParameter("action").
        // If action=link was passed, {action} became "link" (/link/oauth2/code/google), causing redirect_uri_mismatch.
        // We ensure the redirectUri always uses /login/oauth2/code/ matching Google Cloud Console.
        if (authRequest.getRedirectUri() != null && authRequest.getRedirectUri().contains("/link/oauth2/code/")) {
            String fixedRedirectUri = authRequest.getRedirectUri().replace("/link/oauth2/code/", "/login/oauth2/code/");
            authRequest = OAuth2AuthorizationRequest.from(authRequest)
                    .redirectUri(fixedRedirectUri)
                    .build();
        }

        String action = request.getParameter("action");
        String flow = request.getParameter("flow");
        if ("link".equalsIgnoreCase(action) || "link".equalsIgnoreCase(flow)) {
            String token = request.getParameter("token");
            if (!StringUtils.hasText(token)) {
                String authHeader = request.getHeader("Authorization");
                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)
                    && !jwtTokenProvider.isRegistrationToken(token) && !jwtTokenProvider.isLinkToken(token)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                if (userId != null) {
                    log.info("Binding OAuth2 state {} to linking user id: {}", authRequest.getState(), userId);
                    linkStateStore.recordPendingOAuth2State(authRequest.getState(), userId);
                }
            } else {
                log.warn("OAuth2 authorization initiated with action=link but without valid application token");
            }
        }

        return authRequest;
    }

}

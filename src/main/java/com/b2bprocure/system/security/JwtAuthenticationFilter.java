package com.b2bprocure.system.security;

import com.b2bprocure.system.config.JwtConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtConfig jwtConfig;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                if (jwtTokenProvider.isRegistrationToken(jwt) || jwtTokenProvider.isLinkToken(jwt)) {
                    log.debug("Temporary token cannot be used for application authentication");
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtTokenProvider.getUsernameFromToken(jwt);

                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                // Block requests when the user's company is INACTIVE. This is
                // the runtime enforcement of the same check that
                // AuthServiceImpl.login() does at sign-in: if an admin sets a
                // company to INACTIVE while the user still holds a valid JWT,
                // every subsequent request will be rejected until the user
                // signs in again with an ACTIVE company.
                if (userDetails instanceof UserPrincipal principal && !principal.isCompanyActive()) {
                    log.warn("Blocking request: user {} has inactive company (companyId={})",
                            principal.getId(), principal.getCompanyId());
                    writeForbidden(response);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtConfig.getHeader());
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(jwtConfig.getPrefix())) {
            return bearerToken.substring(jwtConfig.getPrefix().length());
        }
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }

    /**
     * Write a 403 response with the standard API envelope used elsewhere
     * ({@code {"success": false, "message": ..., "data": null}}). Keeps the
     * response shape consistent with {@code GlobalExceptionHandler} so the
     * frontend can handle it the same way.
     */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Your company is inactive. Please contact platform administrator.\",\"data\":null}"
        );
    }

}

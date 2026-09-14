package com.b2bprocure.system.common.util;

import com.b2bprocure.system.common.constant.SecurityConstants;
import com.b2bprocure.system.common.exception.UnauthorizedException;
import com.b2bprocure.system.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public final class SecurityUtil {

    private static final String ANONYMOUS_USER = "anonymousUser";

    private SecurityUtil() {
        // Prevent instantiation
    }

    /**
     * Get the current authentication object from SecurityContext.
     */
    public static Optional<Authentication> getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || ANONYMOUS_USER.equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        return Optional.of(authentication);
    }

    /**
     * Check if the current user is authenticated.
     */
    public static boolean isAuthenticated() {
        return getAuthentication().isPresent();
    }

    /**
     * Get the username of the currently logged-in user.
     */
    public static Optional<String> getCurrentUsername() {
        return getAuthentication().map(authentication -> {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                return userDetails.getUsername();
            } else if (principal instanceof String principalString) {
                return principalString;
            }
            return null;
        });
    }

    /**
     * Get the username of the currently logged-in user or throw UnauthorizedException.
     */
    public static String getCurrentUsernameOrThrow() {
        return getCurrentUsername()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
    }

    /**
     * Get the UserDetails of the currently logged-in user.
     */
    public static Optional<UserDetails> getCurrentUserDetails() {
        return getAuthentication()
                .map(Authentication::getPrincipal)
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast);
    }

    /**
     * Get the ID of the currently logged-in user.
     */
    public static Optional<Long> getCurrentUserId() {
        return getCurrentUserDetails()
                .filter(UserPrincipal.class::isInstance)
                .map(UserPrincipal.class::cast)
                .map(UserPrincipal::getId);
    }

    /**
     * Get the ID of the currently logged-in user or throw UnauthorizedException.
     */
    public static Long getCurrentUserIdOrThrow() {
        return getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
    }

    /**
     * Check if the current user has a specific role or authority.
     */
    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }

        String targetRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getAuthentication()
                .map(Authentication::getAuthorities)
                .map(authorities -> authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(authority -> authority.equalsIgnoreCase(targetRole) || authority.equalsIgnoreCase(role)))
                .orElse(false);
    }

    /**
     * Check if the current user is an Admin.
     */
    public static boolean isAdmin() {
        return hasRole(SecurityConstants.ROLE_ADMIN);
    }

    /**
     * Check if the current user is a Buyer.
     */
    public static boolean isBuyer() {
        return hasRole(SecurityConstants.ROLE_BUYER);
    }

    /**
     * Check if the current user is a Supplier.
     */
    public static boolean isSupplier() {
        return hasRole(SecurityConstants.ROLE_SUPPLIER);
    }
}

package com.b2bprocure.system.security;

import com.b2bprocure.system.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;

    @JsonIgnore
    private final String password;

    private final String role;
    private final String avatarUrl;
    private final String status;

    /**
     * Company id of the user, or {@code null} for ADMIN users without a company.
     * Loaded via {@code LEFT JOIN FETCH} in {@code UserRepository.findByUsernameOrEmailWithCompany}.
     */
    private final Long companyId;

    /**
     * Company status of the user (e.g. {@code "ACTIVE"}, {@code "INACTIVE"}),
     * or {@code null} for ADMIN users without a company.
     */
    private final String companyStatus;

    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : "BUYER";
        String authorityName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(authorityName));

        Long companyId = null;
        String companyStatus = null;
        if (user.getCompany() != null) {
            companyId = user.getCompany().getId();
            companyStatus = user.getCompany().getStatus();
        }

        return UserPrincipal.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .password(user.getPassword())
                .role(roleName)
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .companyId(companyId)
                .companyStatus(companyStatus)
                .authorities(authorities)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == null || !"BLOCKED".equalsIgnoreCase(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == null || "ACTIVE".equalsIgnoreCase(status);
    }

    /**
     * Application-level check (NOT part of Spring Security's {@link UserDetails}
     * contract). Returns {@code true} when the user has no company (typical for
     * ADMIN) OR when the user's company is in {@code ACTIVE} status.
     *
     * <p>Used by:
     * <ul>
     *   <li>{@code AuthServiceImpl.login()} — block traditional login.</li>
     *   <li>{@code OAuth2AuthenticationSuccessHandler} — block Google login.</li>
     *   <li>{@code JwtAuthenticationFilter} — block every JWT-authenticated request
     *       so existing sessions are invalidated immediately when admin sets
     *       a company to {@code INACTIVE}.</li>
     * </ul>
     *
     * <p>Fail-closed: a {@code null} status is treated as INACTIVE.
     */
    public boolean isCompanyActive() {
        if (companyId == null) {
            return true; // ADMIN without a company
        }
        return "ACTIVE".equalsIgnoreCase(companyStatus);
    }

}

package com.b2bprocure.system.security;

import com.b2bprocure.system.common.exception.BusinessException;
import com.b2bprocure.system.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REGISTRATION = "OAUTH2_REGISTRATION";
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String CLAIM_PROVIDER = "provider";
    public static final String CLAIM_PROVIDER_USER_ID = "providerUserId";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";

    public static final long REGISTRATION_TOKEN_EXPIRATION_MS = 600_000L; // 10 minutes

    private final JwtConfig jwtConfig;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserPrincipal userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getExpirationMs());

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim("userId", userPrincipal.getId())
                .claim("role", userPrincipal.getRole())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateToken(UserDetails userDetails) {
        if (userDetails instanceof UserPrincipal userPrincipal) {
            return generateToken(userPrincipal);
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getExpirationMs());

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRegistrationToken(String provider, String providerUserId, String email, String name) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + REGISTRATION_TOKEN_EXPIRATION_MS);

        return Jwts.builder()
                .subject(providerUserId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REGISTRATION)
                .claim(CLAIM_PROVIDER, provider)
                .claim(CLAIM_PROVIDER_USER_ID, providerUserId)
                .claim(CLAIM_EMAIL, email != null ? email : "")
                .claim(CLAIM_NAME, name != null ? name : "")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isRegistrationToken(String token) {
        try {
            Claims claims = getClaims(token);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return TOKEN_TYPE_REGISTRATION.equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims validateAndGetRegistrationClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("Registration token is missing or empty", HttpStatus.UNAUTHORIZED);
        }
        try {
            Claims claims = getClaims(token);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!TOKEN_TYPE_REGISTRATION.equals(tokenType)) {
                throw new BusinessException("Invalid token type for registration", HttpStatus.BAD_REQUEST);
            }
            return claims;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new BusinessException("Registration token has expired", HttpStatus.UNAUTHORIZED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException("Invalid registration token: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Object userIdObj = getClaims(token).get("userId");
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    public long getExpirationMs() {
        return jwtConfig.getExpirationMs();
    }

}

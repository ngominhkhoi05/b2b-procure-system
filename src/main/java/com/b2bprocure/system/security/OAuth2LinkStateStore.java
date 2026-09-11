package com.b2bprocure.system.security;

import com.b2bprocure.system.common.enums.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuth2LinkStateStore {

    private static final long EXPIRATION_SECONDS = 600; // 10 minutes

    @Getter
    @AllArgsConstructor
    public static class VerifiedGoogleIdentity {
        private final AuthProvider provider;
        private final String providerUserId;
        private final String email;
        private final Instant expiry;
    }

    @Getter
    @AllArgsConstructor
    public static class PendingOAuth2State {
        private final Long userId;
        private final Instant expiry;
    }

    private final Map<String, PendingOAuth2State> stateToUserId = new ConcurrentHashMap<>();
    private final Map<Long, VerifiedGoogleIdentity> userIdToVerifiedIdentity = new ConcurrentHashMap<>();

    public void recordPendingOAuth2State(String state, Long userId) {
        cleanExpired();
        if (state != null && userId != null) {
            stateToUserId.put(state, new PendingOAuth2State(userId, Instant.now().plusSeconds(EXPIRATION_SECONDS)));
        }
    }

    public Long getAndRemovePendingOAuth2State(String state) {
        if (state == null) {
            return null;
        }
        PendingOAuth2State pending = stateToUserId.remove(state);
        if (pending != null && pending.getExpiry().isAfter(Instant.now())) {
            return pending.getUserId();
        }
        return null;
    }

    public void recordVerifiedIdentity(Long userId, AuthProvider provider, String providerUserId, String email) {
        cleanExpired();
        if (userId != null && provider != null && providerUserId != null) {
            userIdToVerifiedIdentity.put(userId, new VerifiedGoogleIdentity(
                    provider, providerUserId, email, Instant.now().plusSeconds(EXPIRATION_SECONDS)
            ));
        }
    }

    public VerifiedGoogleIdentity getAndRemoveVerifiedIdentity(Long userId) {
        if (userId == null) {
            return null;
        }
        VerifiedGoogleIdentity identity = userIdToVerifiedIdentity.remove(userId);
        if (identity != null && identity.getExpiry().isAfter(Instant.now())) {
            return identity;
        }
        return null;
    }

    public VerifiedGoogleIdentity getVerifiedIdentity(Long userId) {
        if (userId == null) {
            return null;
        }
        VerifiedGoogleIdentity identity = userIdToVerifiedIdentity.get(userId);
        if (identity != null && identity.getExpiry().isAfter(Instant.now())) {
            return identity;
        }
        userIdToVerifiedIdentity.remove(userId);
        return null;
    }

    public void clearAll() {
        stateToUserId.clear();
        userIdToVerifiedIdentity.clear();
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        stateToUserId.entrySet().removeIf(entry -> entry.getValue().getExpiry().isBefore(now));
        userIdToVerifiedIdentity.entrySet().removeIf(entry -> entry.getValue().getExpiry().isBefore(now));
    }

}

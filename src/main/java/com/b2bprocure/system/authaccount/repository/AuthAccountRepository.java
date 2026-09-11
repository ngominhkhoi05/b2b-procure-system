package com.b2bprocure.system.authaccount.repository;

import com.b2bprocure.system.authaccount.entity.AuthAccount;
import com.b2bprocure.system.common.enums.AuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    @EntityGraph(attributePaths = {"user", "user.role"})
    Optional<AuthAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    List<AuthAccount> findByUserId(Long userId);

    Optional<AuthAccount> findByUserIdAndProvider(Long userId, AuthProvider provider);

}

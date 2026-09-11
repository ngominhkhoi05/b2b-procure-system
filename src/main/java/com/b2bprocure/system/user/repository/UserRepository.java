package com.b2bprocure.system.user.repository;

import com.b2bprocure.system.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.username = :identifier OR u.email = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    @Query("SELECT u FROM User u JOIN FETCH u.role LEFT JOIN FETCH u.company WHERE u.email = :email")
    Optional<User> findByEmailWithRoleAndCompany(@Param("email") String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

}

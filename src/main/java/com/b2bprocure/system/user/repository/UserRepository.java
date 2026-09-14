package com.b2bprocure.system.user.repository;

import com.b2bprocure.system.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT u FROM User u JOIN FETCH u.role LEFT JOIN FETCH u.company WHERE u.id = :id")
    Optional<User> findByIdWithRoleAndCompany(@Param("id") Long id);

    @Query("SELECT u FROM User u JOIN FETCH u.role LEFT JOIN FETCH u.company WHERE u.username = :username")
    Optional<User> findByUsernameWithRoleAndCompany(@Param("username") String username);

    @Query(value = "SELECT u FROM User u JOIN FETCH u.role LEFT JOIN FETCH u.company WHERE " +
            "(cast(:role as string) IS NULL OR UPPER(u.role.name) = :role) AND " +
            "(cast(:status as string) IS NULL OR UPPER(u.status) = :status) AND " +
            "(cast(:pattern as string) IS NULL OR LOWER(u.username) LIKE :pattern OR LOWER(u.fullName) LIKE :pattern OR LOWER(u.email) LIKE :pattern)",
           countQuery = "SELECT count(u) FROM User u WHERE " +
            "(cast(:role as string) IS NULL OR UPPER(u.role.name) = :role) AND " +
            "(cast(:status as string) IS NULL OR UPPER(u.status) = :status) AND " +
            "(cast(:pattern as string) IS NULL OR LOWER(u.username) LIKE :pattern OR LOWER(u.fullName) LIKE :pattern OR LOWER(u.email) LIKE :pattern)")
    Page<User> searchUsers(
            @Param("role") String role,
            @Param("status") String status,
            @Param("pattern") String pattern,
            Pageable pageable
    );

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

}

package com.example.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.authservice.model.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * A powerful custom JPQL query to instantly revoke all active tokens for a given user.
     * Useful for password resets or "Sign out of all other sessions" functionality.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    void revokeAllUserTokens(@Param("userId") Long userId);
}
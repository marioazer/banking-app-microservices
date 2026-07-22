package com.example.authservice.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.authservice.model.BlacklistedToken;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, String> {

    // Spring Data JPA automatically provides existsById(String jti)
    // which our JwtAuthenticationFilter is already calling!

    /**
     * Purges tokens from the database that have naturally expired.
     * Since an expired JWT will be rejected by the JwtService math anyway,
     * we no longer need to waste database space storing its blacklist status!
     */
    @Modifying
    @Query("DELETE FROM BlacklistedToken b WHERE b.expiresAt < :now")
    void deleteAllExpiredTokensSince(LocalDateTime now);
}
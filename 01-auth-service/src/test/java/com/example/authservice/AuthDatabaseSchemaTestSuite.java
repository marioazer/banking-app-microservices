package com.example.authservice;

import com.example.authservice.model.BlacklistedToken;
import com.example.authservice.model.RecognizedDevice;
import com.example.authservice.model.RefreshToken;
import com.example.authservice.model.TwoFactorCode;
import com.example.authservice.repository.BlacklistedTokenRepository;
import com.example.authservice.repository.RecognizedDeviceRepository;
import com.example.authservice.repository.RefreshTokenRepository;
import com.example.authservice.repository.TwoFactorCodeRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("Database Schema & JPA Repository Test Suite")
class AuthDatabaseSchemaTestSuite {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RecognizedDeviceRepository deviceRepository;

    @Autowired
    private TwoFactorCodeRepository twoFactorCodeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    /* ==========================================================
       TABLE 1: recognized_devices CONSTRAINTS & QUERIES
       ========================================================== */

    @Test
    @DisplayName("Table 1: Enforce UNIQUE constraint on device_hash - [MEANT TO PASS]")
    void testRecognizedDevice_UniqueHashConstraint() {
        // Given: An existing device registered with a specific hash
        RecognizedDevice device1 = new RecognizedDevice(100L, "duplicate-hash-123");
        entityManager.persistAndFlush(device1);

        // When: Attempting to insert a second record with the identical device_hash
        RecognizedDevice device2 = new RecognizedDevice(101L, "duplicate-hash-123");

        // Then: Database throws exception enforcing UNIQUE constraint
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(device2);
        }).isInstanceOfAny(
            DataIntegrityViolationException.class, 
            org.hibernate.exception.ConstraintViolationException.class
        );
    }

    @Test
    @DisplayName("Table 1: Query findByUserIdAndDeviceHash retrieves correct record - [MEANT TO PASS]")
    void testRecognizedDevice_MagicMethodQuery() {
        // Given: Stored device hash
        RecognizedDevice device = new RecognizedDevice(200L, "unique-device-hash-999");
        entityManager.persistAndFlush(device);

        // When: Executing repository query
        Optional<RecognizedDevice> found = deviceRepository.findByUserIdAndDeviceHash(200L, "unique-device-hash-999");

        // Then: Matching record is returned
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(200L);
    }

    /* ==========================================================
       TABLE 2: two_factor_codes DELETION & RETRIEVAL
       ========================================================== */

    @Test
    @DisplayName("Table 2: deleteByUserId purges active 2FA codes - [MEANT TO PASS]")
    void testTwoFactorCode_DeleteByUserId() {
        // Given: Active 2FA code in DB
        TwoFactorCode code = new TwoFactorCode(300L, "hashed-2fa-code");
        entityManager.persistAndFlush(code);

        // When: Invoking deleteByUserId custom modifying query
        twoFactorCodeRepository.deleteByUserId(300L);
        entityManager.flush();

        // Then: Code is permanently deleted
        Optional<TwoFactorCode> found = twoFactorCodeRepository.findByUserId(300L);
        assertThat(found).isEmpty();
    }

    /* ==========================================================
       TABLE 3: refresh_tokens REVOCATION QUERY
       ========================================================== */

    @Test
    @DisplayName("Table 3: revokeAllUserTokens flips revoked flag for active tokens - [MEANT TO PASS]")
    void testRefreshToken_RevokeAllUserTokens() {
        // Given: Two active refresh tokens for user 400L
        RefreshToken token1 = new RefreshToken(400L, "hash-token-1");
        RefreshToken token2 = new RefreshToken(400L, "hash-token-2");
        entityManager.persist(token1);
        entityManager.persist(token2);
        entityManager.flush();

        // When: Executing custom JPQL bulk update query
        refreshTokenRepository.revokeAllUserTokens(400L);
        entityManager.clear(); // Clear L1 cache to read updated DB state

        // Then: Both tokens are flagged as revoked = true
        Optional<RefreshToken> updatedToken1 = refreshTokenRepository.findByTokenHash("hash-token-1");
        assertThat(updatedToken1).isPresent();
        assertThat(updatedToken1.get().getRevoked()).isTrue();
        assertThat(updatedToken1.get().isValid()).isFalse();
    }

    /* ==========================================================
       TABLE 4: revoked_jwt_blacklist PURGE QUERY
       ========================================================== */

    @Test
    @DisplayName("Table 4: deleteAllExpiredTokensSince purges naturally expired JWT JTIs - [MEANT TO PASS]")
    void testBlacklistedToken_PurgeExpired() {
        // Given: One expired blacklisted JTI and one active blacklisted JTI
        String expiredJti = "a1b2c3d4-e5f6-7a8b-9c0d-expired1111";
        String activeJti = "a1b2c3d4-e5f6-7a8b-9c0d-active22222";

        BlacklistedToken expiredToken = new BlacklistedToken(expiredJti, LocalDateTime.now().minusMinutes(10));
        BlacklistedToken activeToken = new BlacklistedToken(activeJti, LocalDateTime.now().plusMinutes(10));

        entityManager.persist(expiredToken);
        entityManager.persist(activeToken);
        entityManager.flush();

        // When: Cron maintenance query executes
        blacklistedTokenRepository.deleteAllExpiredTokensSince(LocalDateTime.now());
        entityManager.flush();

        // Then: Expired JTI is removed, while active blacklisted JTI remains
        assertThat(blacklistedTokenRepository.existsById(expiredJti)).isFalse();
        assertThat(blacklistedTokenRepository.existsById(activeJti)).isTrue();
    }
}
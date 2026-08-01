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

    // this one is testing the unique constraint on the device_hash column
    // first I save a device with a hash so there is already a row sitting in the table
    // then I make a second device object using that exact same hash string
    // when I try to save that second one the database should reject it
    // spring wraps the raw sql constraint error so I check for either the generic
    // data integrity exception or the hibernate specific constraint one, since it
    // can come back as either depending on the driver
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

    // this one checks the custom repository method findbyuseridanddevicehash
    // I save one device tied to a user id and a hash value
    // then call the repository method with that same user id and hash
    // it should find the row and come back wrapped in a non empty optional
    // and the user id on the entity that comes back should match what I saved
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

    // testing the deletebyuserid query on the two factor code table
    // I persist one active 2fa code for a user first
    // then call deletebyuserid which is a custom modifying query, not a default jpa method
    // after flushing I look the code up again by that same user id
    // it should come back empty since the row was actually removed from the db and
    // not just marked as something else
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

    // this one is for the bulk revoke query on refresh tokens
    // I create two refresh tokens for the same user and persist both of them
    // then call revokealluserstokens which should flip the revoked flag on both rows at once
    // I clear the entity manager after that so I am not reading a cached copy out of the l1 cache
    // then pull one of the tokens back up by its hash and confirm revoked is true
    // and that isvalid() also reports false, since a revoked token should never read as valid
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

    // this test is for the cron style cleanup query that purges expired blacklist entries
    // I make one token that already expired ten minutes ago and one that is still good for ten more minutes
    // both get persisted so the table has one of each kind sitting in it
    // then I call deleteallexpiredtokenssince with the current time, which should only touch the expired one
    // after that I check that the expired jti no longer exists but the still active one is untouched
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
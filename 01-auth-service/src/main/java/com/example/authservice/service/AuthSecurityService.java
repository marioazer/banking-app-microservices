package com.example.authservice.service;

import com.example.authservice.model.BlacklistedToken;
import com.example.authservice.model.RecognizedDevice;
import com.example.authservice.model.RefreshToken;
import com.example.authservice.model.TwoFactorCode;
import com.example.authservice.repository.BlacklistedTokenRepository;
import com.example.authservice.repository.RecognizedDeviceRepository;
import com.example.authservice.repository.RefreshTokenRepository;
import com.example.authservice.repository.TwoFactorCodeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// @Service marks this as a spring managed bean in the business logic layer, functionally almost
// the same as @Component, just a more specific name so the intent of the class is clear at a glance
@Service
public class AuthSecurityService {

    private final RecognizedDeviceRepository deviceRepository;
    private final TwoFactorCodeRepository twoFactorCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AuthSecurityService(RecognizedDeviceRepository deviceRepository,
                               TwoFactorCodeRepository twoFactorCodeRepository,
                               RefreshTokenRepository refreshTokenRepository,
                               BlacklistedTokenRepository blacklistedTokenRepository,
                               KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.twoFactorCodeRepository = twoFactorCodeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // ==========================================
    // 1. Device Fingerprinting
    // ==========================================

    public boolean isDeviceRecognized(Long userId, String rawDeviceCookie) {
        if (rawDeviceCookie == null) return false;
        if (rawDeviceCookie.isBlank()) return false;
        String hashedCookie = hashString(rawDeviceCookie);
        return deviceRepository.findByUserIdAndDeviceHash(userId, hashedCookie).isPresent();
    }

    // @Transactional wraps this whole method in one database transaction, if anything after the
    // save throws, the save gets rolled back too, so the db never ends up in a half done state
    @Transactional
    public String registerNewDevice(Long userId) {
        String rawDeviceId = UUID.randomUUID().toString();
        String hashedId = hashString(rawDeviceId);
        deviceRepository.save(new RecognizedDevice(userId, hashedId));
        return rawDeviceId; // Return raw so the Controller can send it as a Set-Cookie header
    }

    // ==========================================
    // 2. SMS 2FA Orchestration
    // ==========================================

    @Transactional
    public void triggerSms2fa(Long userId, String phoneNumber) {
        String code = generateAndStoreCode(userId);
        publishSmsEvent(phoneNumber, code);
    }

    private String generateAndStoreCode(Long userId) {
        // 1. Clear out any old codes stuck in the database
        twoFactorCodeRepository.deleteByUserId(userId);

        // 2. Generate a highly secure 6-digit random code
        SecureRandom random = new SecureRandom();
        String code = String.format("%06d", random.nextInt(999999));

        twoFactorCodeRepository.save(new TwoFactorCode(userId, hashString(code)));

        return code;
    }

    private void publishSmsEvent(String phoneNumber, String code) {
        // 4. Fire the Kafka Event
        try {
            Map<String, String> event = new HashMap<>();
            event.put("action", "SMS_2FA_REQUESTED");
            event.put("phoneNumber", phoneNumber);
            event.put("code", code); // The notification service needs the raw code to send it via Twilio

            kafkaTemplate.send("notification-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            // Because of @Transactional, throwing an exception here instantly rolls back the DB save!
            throw new RuntimeException("Failed to publish SMS event to Kafka", e);
        }
    }

    @Transactional
    public boolean verifySms2fa(Long userId, String providedCode) {
        TwoFactorCode storedCode = twoFactorCodeRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No active 2FA code found."));

        if (storedCode.isExpired()) {
            twoFactorCodeRepository.delete(storedCode);
            throw new RuntimeException("Code has expired.");
        }

        if (storedCode.getAttempts() >= 3) {
            twoFactorCodeRepository.delete(storedCode);
            throw new RuntimeException("Too many failed attempts. Login locked.");
        }

        if (!storedCode.getCodeHash().equals(hashString(providedCode))) {
            storedCode.incrementAttempts();
            twoFactorCodeRepository.save(storedCode);
            return false;
        }

        // Success! Clean up the code so it cannot be reused.
        twoFactorCodeRepository.delete(storedCode);
        return true;
    }

    // ==========================================
    // 3. Session Revocation & Blacklisting
    // ==========================================

    @Transactional
    public void logoutUserSession(Long userId, String jwtJti, Date jwtExpiration) {
        // 1. Revoke the long-lived refresh tokens in the DB
        refreshTokenRepository.revokeAllUserTokens(userId);

        // 2. Add the current short-lived JWT to the blacklist so it cannot be reused
        LocalDateTime expiresAt = LocalDateTime.ofInstant(jwtExpiration.toInstant(), ZoneId.systemDefault());
        blacklistedTokenRepository.save(new BlacklistedToken(jwtJti, expiresAt));
    }

    // ==========================================
    // 4. Automated Maintenance
    // ==========================================

    // learned @Scheduled just needs spring's scheduling support turned on somewhere with
    // @EnableScheduling, then it runs this method automatically on its own background thread
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredBlacklistTokens() {
        blacklistedTokenRepository.deleteAllExpiredTokensSince(LocalDateTime.now());
    }

    // ==========================================
    // Internal Cryptography Helpers
    // ==========================================

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash string", e);
        }
    }
}
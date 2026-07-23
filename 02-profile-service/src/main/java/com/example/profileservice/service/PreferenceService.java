package com.example.profileservice.service;

import com.example.profileservice.model.UserPreferenceEntity;
import com.example.profileservice.repository.PreferenceRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.ZoneId;

@Service
public class PreferenceService {

    // Mirrors the column defaults in V3__Update_Preferences_Schema.sql, so a brand-new user's
    // untouched fields end up with the same values whether created lazily here (first PUT to
    // either endpoint) or seen for the first time by the Notification Service.
    private static final BigDecimal DEFAULT_ALERT_THRESHOLD = new BigDecimal("100.00");
    private static final boolean DEFAULT_DAILY_SUMMARY_ENABLED = false;
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final PreferenceRepository preferenceRepository;

    public PreferenceService(PreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Updates only the user's alert threshold, leaving any existing daily-summary settings
     * untouched. Fulfills FR9.1 AC1/AC2, independent of FR10.1.
     *
     * @CacheEvict instantly removes the entry associated with this userId from Redis.
     * This guarantees the Notification Service will fetch the updated threshold on its next read[cite: 4].
     */
    @Transactional
    @CacheEvict(value = "user-preferences", key = "#userId")
    public void updateAlertThreshold(Long userId, BigDecimal alertThresholdAmount) {
        UserPreferenceEntity entity = findOrCreateDefault(userId);
        entity.setAlertThresholdAmount(alertThresholdAmount);
        preferenceRepository.save(entity);
    }

    /**
     * Updates only the user's daily-summary opt-in and timezone, leaving any existing alert
     * threshold untouched. Fulfills FR10.1 AC1/AC2/AC3, independent of FR9.1.
     *
     * @CacheEvict instantly removes the entry associated with this userId from Redis.
     */
    @Transactional
    @CacheEvict(value = "user-preferences", key = "#userId")
    public void updateDailySummarySettings(Long userId, Boolean dailySummaryEnabled, String timezone) {
        // Strict Domain Validation: Ensure the timezone is a valid IANA identifier[cite: 5].
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timezone identifier. Use IANA formats like 'America/New_York'.");
        }

        UserPreferenceEntity entity = findOrCreateDefault(userId);
        entity.setDailySummaryEnabled(dailySummaryEnabled);
        entity.setTimezone(timezone);
        preferenceRepository.save(entity);
    }

    private UserPreferenceEntity findOrCreateDefault(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreferenceEntity newEntity = new UserPreferenceEntity();
                    newEntity.setUserId(userId);
                    newEntity.setAlertThresholdAmount(DEFAULT_ALERT_THRESHOLD);
                    newEntity.setDailySummaryEnabled(DEFAULT_DAILY_SUMMARY_ENABLED);
                    newEntity.setTimezone(DEFAULT_TIMEZONE);
                    return newEntity;
                });
    }
}

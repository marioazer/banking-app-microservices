package com.example.profileservice.service;

import com.example.profileservice.dto.AlertPreferencesDto;
import com.example.profileservice.model.UserPreferenceEntity;
import com.example.profileservice.repository.PreferenceRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;

@Service
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;

    public PreferenceService(PreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Updates the user's notification preferences and evicts stale Redis cache.
     * Fulfills FR9.1 and FR10.1[cite: 4, 5].
     * 
     * @CacheEvict instantly removes the entry associated with this userId from Redis.
     * This guarantees the Notification Service will fetch the updated threshold on its next read[cite: 4].
     */
    @Transactional
    @CacheEvict(value = "user-preferences", key = "#userId")
    public void updateAlertPreferences(Long userId, AlertPreferencesDto dto) {
        
        // 1. Strict Domain Validation: Ensure the timezone is a valid IANA identifier[cite: 5].
        try {
            ZoneId.of(dto.timezone());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timezone identifier. Use IANA formats like 'America/New_York'.");
        }

        // 2. Fetch existing preferences or create a new default entity
        UserPreferenceEntity entity = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreferenceEntity newEntity = new UserPreferenceEntity();
                    newEntity.setUserId(userId);
                    return newEntity;
                });

        // 3. Apply updates based on the DTO contracts[cite: 4, 5]
        entity.setAlertThresholdAmount(dto.alertThresholdAmount());
        entity.setDailySummaryEnabled(dto.dailySummaryEnabled());
        entity.setTimezone(dto.timezone());

        // 4. Persist to PostgreSQL (Cache eviction happens automatically after this succeeds)
        preferenceRepository.save(entity);
    }
}
package com.example.profileservice.repository;

import com.example.profileservice.model.UserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreferenceRepository extends JpaRepository<UserPreferenceEntity, Long> {
    Optional<UserPreferenceEntity> findByUserId(Long userId);

    /**
     * Fulfills FR10.2 AC2: users opted into the daily balance summary for a specific timezone -
     * backs GET /api/v1/profile/alerts/daily-summary-users, which notification-service's
     * scheduled job calls once per timezone as it sweeps through the day.
     */
    List<UserPreferenceEntity> findByDailySummaryEnabledTrueAndTimezone(String timezone);
}
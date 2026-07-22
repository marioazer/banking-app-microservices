package com.example.notificationservice.client;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * Declarative REST client for communicating with the Profile Service.
 * Fulfills FR9.2 AC2 (Cache/Database Lookup) and FR10.2 AC2 (Identify users for batch job)[cite: 4, 5].
 */
@FeignClient(name = "profile-service", url = "${profile-service.url:http://profile-service:8080}")
public interface ProfileServiceClient {

    /**
     * Local representation of the Profile Service's preference data.
     */
    record UserPreferenceResponse(
            Long userId,
            BigDecimal alertThresholdAmount,
            Boolean dailySummaryEnabled,
            String timezone
    ) {}

    /**
     * Fetches a specific user's preferences for real-time Kafka event evaluation.
     * 
     * @Cacheable intercepts the call. If the data is in Redis, the HTTP call is SKIPPED,
     * ensuring sub-millisecond lookups to meet the 5-second dispatch SLA[cite: 4].
     */
    @GetMapping("/api/v1/profile/alerts/{userId}")
    @Cacheable(value = "user-preferences", key = "#userId", unless = "#result == null")
    UserPreferenceResponse getUserPreferences(@PathVariable("userId") Long userId);

    /**
     * Fetches a batch list of users who opted in to daily summaries for a specific timezone.
     * Fulfills FR10.2 AC2: Queries for users with daily_summary_enabled = true[cite: 5].
     * 
     * Note: This is NOT cached, as it's a daily batch job where the result set changes
     * constantly based on the requested timezone.
     */
    @GetMapping("/api/v1/profile/alerts/daily-summary-users")
    List<UserPreferenceResponse> getUsersForDailySummary(@RequestParam("timezone") String timezone);
}
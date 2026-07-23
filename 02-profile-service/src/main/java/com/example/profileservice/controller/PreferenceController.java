package com.example.profileservice.controller;

import com.example.profileservice.dto.UpdateAlertThresholdRequestDto;
import com.example.profileservice.dto.UpdateDailySummaryRequestDto;
import com.example.profileservice.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile/alerts")
@PreAuthorize("hasAuthority('SCOPE_FULL_AUTH')")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /**
     * Updates the user's custom dollar threshold for real-time transaction alerts.
     * Fulfills FR9.1 AC1: Exposes PUT /api/v1/profile/alerts/threshold[cite: 4].
     *
     * Only the alert threshold is affected - any existing daily-summary opt-in/timezone
     * preference for this user is left exactly as it was.
     */
    @PutMapping("/threshold")
    public ResponseEntity<String> updateAlertThreshold(
            @RequestBody @Valid UpdateAlertThresholdRequestDto request) {

        Long userId = extractUserIdFromAuth();
        preferenceService.updateAlertThreshold(userId, request.alertThresholdAmount());

        return ResponseEntity.ok("Alert threshold preferences successfully updated.");
    }

    /**
     * Updates the user's opt-in status and timezone for daily balance summaries.
     * Fulfills FR10.1 AC1: Exposes PUT /api/v1/profile/alerts/daily-summary[cite: 5].
     *
     * Only the daily-summary opt-in/timezone are affected - any existing alert threshold
     * for this user is left exactly as it was.
     */
    @PutMapping("/daily-summary")
    public ResponseEntity<String> updateDailySummarySettings(
            @RequestBody @Valid UpdateDailySummaryRequestDto request) {

        Long userId = extractUserIdFromAuth();
        preferenceService.updateDailySummarySettings(userId, request.dailySummaryEnabled(), request.timezone());

        return ResponseEntity.ok("Daily summary preferences successfully updated.");
    }

    /**
     * Cryptographically secures the endpoints against IDOR attacks by pulling 
     * the identity directly from the verified JWT token.
     */
    private Long extractUserIdFromAuth() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User is not authenticated");
        }
        return Long.valueOf(authentication.getName());
    }
}
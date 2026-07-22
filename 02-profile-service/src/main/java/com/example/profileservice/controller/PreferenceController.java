package com.example.profileservice.controller;

import com.example.profileservice.dto.AlertPreferencesDto;
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
     * Note: As a PUT request, this replaces the entire preference resource state.
     */
    @PutMapping("/threshold")
    public ResponseEntity<String> updateAlertThreshold(
            @RequestBody @Valid AlertPreferencesDto request) {
        
        Long userId = extractUserIdFromAuth();
        preferenceService.updateAlertPreferences(userId, request);
        
        return ResponseEntity.ok("Alert threshold preferences successfully updated.");
    }

    /**
     * Updates the user's opt-in status and timezone for daily balance summaries.
     * Fulfills FR10.1 AC1: Exposes PUT /api/v1/profile/alerts/daily-summary[cite: 5].
     * 
     * Note: As a PUT request, this replaces the entire preference resource state.
     */
    @PutMapping("/daily-summary")
    public ResponseEntity<String> updateDailySummarySettings(
            @RequestBody @Valid AlertPreferencesDto request) {
        
        Long userId = extractUserIdFromAuth();
        preferenceService.updateAlertPreferences(userId, request);
        
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
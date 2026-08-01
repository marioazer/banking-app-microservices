package com.example.profileservice.controller;

import com.example.profileservice.dto.UpdateAlertThresholdRequestDto;
import com.example.profileservice.dto.UpdateDailySummaryRequestDto;
import com.example.profileservice.model.UserPreferenceEntity;
import com.example.profileservice.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

// putting @PreAuthorize at the class level instead of on each method applies it to every single
// endpoint in this controller at once, learned this saves repeating the same check three times
@RestController
@RequestMapping("/api/v1/profile/alerts")
@PreAuthorize("hasAuthority('SCOPE_FULL_AUTH')")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    // @Valid tells spring to run bean validation on the incoming dto before this method body
    // even runs, if any @notnull/@min/etc constraint on the dto fails this returns a 400 automatically
    @PutMapping("/threshold")
    public ResponseEntity<String> updateAlertThreshold(
            @RequestBody @Valid UpdateAlertThresholdRequestDto request) {

        Long userId = extractUserIdFromAuth();
        preferenceService.updateAlertThreshold(userId, request.alertThresholdAmount());

        return ResponseEntity.ok("Alert threshold preferences successfully updated.");
    }

    @PutMapping("/daily-summary")
    public ResponseEntity<String> updateDailySummarySettings(
            @RequestBody @Valid UpdateDailySummaryRequestDto request) {

        Long userId = extractUserIdFromAuth();
        preferenceService.updateDailySummarySettings(userId, request.dailySummaryEnabled(), request.timezone());

        return ResponseEntity.ok("Daily summary preferences successfully updated.");
    }

    public record UserPreferenceResponse(
            Long userId,
            BigDecimal alertThresholdAmount,
            Boolean dailySummaryEnabled,
            String timezone
    ) {}

    @GetMapping("/{userId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<UserPreferenceResponse> getPreferences(@PathVariable Long userId) {
        UserPreferenceEntity entity = preferenceService.getPreferences(userId);
        return ResponseEntity.ok(new UserPreferenceResponse(
                entity.getUserId(), entity.getAlertThresholdAmount(), entity.getDailySummaryEnabled(), entity.getTimezone()));
    }

    @GetMapping("/daily-summary-users")
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<UserPreferenceResponse>> getUsersForDailySummary(@RequestParam String timezone) {
        List<UserPreferenceResponse> users = preferenceService.getUsersForDailySummary(timezone).stream()
                .map(entity -> new UserPreferenceResponse(
                        entity.getUserId(), entity.getAlertThresholdAmount(), entity.getDailySummaryEnabled(), entity.getTimezone()))
                .toList();
        return ResponseEntity.ok(users);
    }

    private Long extractUserIdFromAuth() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new SecurityException("User is not authenticated");
        }
        if (!authentication.isAuthenticated()) {
            throw new SecurityException("User is not authenticated");
        }
        // The JWT subject holds the username, not the id — auth-service puts the numeric
        // userId in its own claim instead, since this service has no User table to resolve it from.
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaim("userId");
    }
}
package com.example.profileservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/v1/profile/alerts/daily-summary.
 * Fulfills FR10.1 AC1/AC2: sets only the opt-in flag and timezone, independent of the alert threshold.
 */
public record UpdateDailySummaryRequestDto(

        @NotNull(message = "Daily summary preference must be specified")
        Boolean dailySummaryEnabled,

        @NotBlank(message = "Timezone cannot be blank")
        // We expect standard IANA timezone formats like "America/New_York" or "UTC"
        String timezone

) {}

package com.example.profileservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Data Transfer Object for updating user notification and alert preferences.
 * Fulfills FR9.1 and FR10.1 requirements[cite: 6, 7].
 */
public record AlertPreferencesDto(

        @NotNull(message = "Alert threshold cannot be null")
        @Positive(message = "Threshold must be a positive amount")
        @DecimalMin(value = "0.01", message = "Minimum threshold is 0.01")
        BigDecimal alertThresholdAmount,

        @NotNull(message = "Daily summary preference must be specified")
        Boolean dailySummaryEnabled,

        @NotBlank(message = "Timezone cannot be blank")
        // We expect standard IANA timezone formats like "America/New_York" or "UTC"[cite: 7]
        String timezone

) {}
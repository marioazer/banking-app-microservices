package com.example.profileservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for PUT /api/v1/profile/alerts/threshold.
 * Fulfills FR9.1 AC1: sets only the alert_threshold_amount, independent of daily-summary settings.
 */
// a record used purely as a request dto, learned validation annotations can go right on the
// record's own components like this instead of needing a whole class with private fields and getters
public record UpdateAlertThresholdRequestDto(

        @NotNull(message = "Alert threshold cannot be null")
        @Positive(message = "Threshold must be a positive amount")
        @DecimalMin(value = "0.01", message = "Minimum threshold is 0.01")
        BigDecimal alertThresholdAmount

) {}

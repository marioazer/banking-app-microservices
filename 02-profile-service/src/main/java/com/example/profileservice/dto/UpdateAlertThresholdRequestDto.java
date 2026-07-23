package com.example.profileservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for PUT /api/v1/profile/alerts/threshold.
 * Fulfills FR9.1 AC1: sets only the alert_threshold_amount, independent of daily-summary settings.
 */
public record UpdateAlertThresholdRequestDto(

        @NotNull(message = "Alert threshold cannot be null")
        @Positive(message = "Threshold must be a positive amount")
        @DecimalMin(value = "0.01", message = "Minimum threshold is 0.01")
        BigDecimal alertThresholdAmount

) {}

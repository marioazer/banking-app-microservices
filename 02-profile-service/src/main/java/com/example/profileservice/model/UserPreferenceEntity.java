package com.example.profileservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "user_preferences")
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private BigDecimal alertThresholdAmount;
    private Boolean dailySummaryEnabled;
    private String timezone;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getAlertThresholdAmount() { return alertThresholdAmount; }
    public void setAlertThresholdAmount(BigDecimal alertThresholdAmount) { this.alertThresholdAmount = alertThresholdAmount; }

    public Boolean getDailySummaryEnabled() { return dailySummaryEnabled; }
    public void setDailySummaryEnabled(Boolean dailySummaryEnabled) { this.dailySummaryEnabled = dailySummaryEnabled; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
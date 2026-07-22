-- ===========================================================================
-- V3__Update_Preferences_Schema.sql
-- Purpose: Extend user_preferences to support alert thresholds and daily summary scheduling.
-- ===========================================================================

-- 1. Add alert threshold with a default value of 100.00 as per FR9 requirement[cite: 6]
ALTER TABLE user_preferences 
ADD COLUMN alert_threshold_amount DECIMAL(19, 2) NOT NULL DEFAULT 100.00;

-- 2. Add daily summary toggle[cite: 7]
ALTER TABLE user_preferences 
ADD COLUMN daily_summary_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Add timezone support to ensure accuracy for 8:00 AM delivery[cite: 7]
ALTER TABLE user_preferences 
ADD COLUMN timezone VARCHAR(50) NOT NULL DEFAULT 'UTC';
-- ===========================================================================
-- 1. Create the KYC Status ENUM Type
-- ===========================================================================
-- Enforces strict data integrity at the database level.
CREATE TYPE kyc_status_enum AS ENUM (
    'PENDING_VERIFICATION', 
    'APPROVED', 
    'REJECTED'
);

-- ===========================================================================
-- 2. Update the existing User Profiles Table
-- ===========================================================================
ALTER TABLE user_profiles
    -- Automatically lock down all new (and existing) accounts per FR3.1
    ADD COLUMN kyc_status kyc_status_enum NOT NULL DEFAULT 'PENDING_VERIFICATION',
    
    -- Add contact fields for FR4.1 updates. 
    -- Nullable initially, to be populated by the user via the PUT endpoint.
    ADD COLUMN phone_number VARCHAR(20),
    ADD COLUMN address_line_1 VARCHAR(255),
    ADD COLUMN address_line_2 VARCHAR(255),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN state VARCHAR(50),
    ADD COLUMN zip_code VARCHAR(20);

-- ===========================================================================
-- 3. Create the Admin Override Audit Logs Table
-- ===========================================================================
-- Fulfills FR3.4: A dedicated table strictly for tracking compliance officers
-- who manually bypass the automated third-party KYC vendor.
CREATE TABLE kyc_override_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    old_status kyc_status_enum NOT NULL,
    new_status kyc_status_enum NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- If a user is deleted, their audit logs should technically remain for compliance,
    -- but for the scope of this project, we will cascade to keep the DB clean.
    CONSTRAINT fk_kyc_override_user FOREIGN KEY (user_id) REFERENCES user_profiles(id) ON DELETE CASCADE
);

-- Index the user_id and admin_id for fast lookup during compliance audits
CREATE INDEX idx_kyc_override_user ON kyc_override_audit_logs(user_id);
CREATE INDEX idx_kyc_override_admin ON kyc_override_audit_logs(admin_id);
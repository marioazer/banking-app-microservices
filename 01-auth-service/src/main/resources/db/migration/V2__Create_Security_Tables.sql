-- ===========================================================================
-- Table 1: Recognized Devices (For bypassing 2FA on trusted hardware)
-- ===========================================================================
CREATE TABLE recognized_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_hash VARCHAR(255) NOT NULL UNIQUE, -- Store hash, never the raw cookie
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recognized_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ===========================================================================
-- Table 2: Two-Factor Codes (For SMS Verification via Kafka)
-- ===========================================================================
CREATE TABLE two_factor_codes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(255) NOT NULL, -- Hashed 6-digit code
    attempts INT DEFAULT 0 NOT NULL, -- Lockout mechanism after 3 attempts
    expires_at TIMESTAMP NOT NULL,   -- Strict 5-minute expiration
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_2fa_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ===========================================================================
-- Table 3: Refresh Tokens (For Sliding Sessions)
-- ===========================================================================
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,  -- Flipped to true on manual logout
    expires_at TIMESTAMP NOT NULL,           -- e.g., 24 hours from creation
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ===========================================================================
-- Table 4: Revoked JWT Blacklist (For explicit logouts)
-- ===========================================================================
CREATE TABLE revoked_jwt_blacklist (
    jti VARCHAR(36) PRIMARY KEY, -- The unique UUID of the Access Token
    expires_at TIMESTAMP NOT NULL, -- Used to clean up the DB after the JWT naturally expires
    revoked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===========================================================================
-- Performance Indices
-- ===========================================================================
-- The blacklist is checked on EVERY request. We index 'jti' (automatically via PRIMARY KEY)
-- but we also index 'expires_at' so a background cron job can easily delete old rows.
CREATE INDEX idx_blacklist_expires_at ON revoked_jwt_blacklist(expires_at);

-- Used to quickly find valid refresh tokens for a user
CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id) WHERE revoked = FALSE;
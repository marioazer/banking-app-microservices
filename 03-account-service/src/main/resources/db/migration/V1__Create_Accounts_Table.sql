-- ===========================================================================
-- 1. Create Enums for Strict Type Safety
-- ===========================================================================
CREATE TYPE account_type_enum AS ENUM (
    'CHECKING', 
    'SAVINGS'
);

CREATE TYPE account_status_enum AS ENUM (
    'ACTIVE', 
    'FROZEN', 
    'CLOSED'
);

-- ===========================================================================
-- 2. Create the Accounts Table
-- ===========================================================================
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    
    account_type account_type_enum NOT NULL,
    
    -- DECIMAL(19,4) guarantees exact precision. 
    -- 19 total digits, 4 decimal places (e.g., 999999999999999.9999)
    available_balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    
    -- US Routing numbers are exactly 9 digits, but stored as VARCHAR to preserve leading zeros
    routing_number VARCHAR(9) NOT NULL,
    
    -- Account numbers vary by institution, generally between 8 and 12 digits
    account_number VARCHAR(20) NOT NULL UNIQUE,
    
    status account_status_enum NOT NULL DEFAULT 'ACTIVE',
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===========================================================================
-- 3. Performance Indexes
-- ===========================================================================
-- Fulfills FR5.1 AC3: B-Tree index explicitly on the user_id column.
-- When the dashboard queries "SELECT * FROM accounts WHERE user_id = 5", 
-- PostgreSQL uses this index to instantly locate the rows without scanning the entire table.
CREATE INDEX idx_accounts_user_id ON accounts (user_id);
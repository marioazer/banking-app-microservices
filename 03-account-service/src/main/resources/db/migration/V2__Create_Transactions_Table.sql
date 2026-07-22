-- ===========================================================================
-- 1. Create Enums for Strict Type Safety
-- ===========================================================================
CREATE TYPE transaction_type_enum AS ENUM (
    'CREDIT', 
    'DEBIT'
);

-- ===========================================================================
-- 2. Create the Transactions Table
-- ===========================================================================
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    
    -- Restricts data to exactly CREDIT or DEBIT
    transaction_type transaction_type_enum NOT NULL,
    
    -- FR6.1 AC3: Stored as absolute positive value. 
    -- The CHECK constraint ensures a bug in Java can't insert negative numbers.
    amount DECIMAL(19, 4) NOT NULL CHECK (amount >= 0),
    
    description VARCHAR(255) NOT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Establish relationship with the accounts table
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- ===========================================================================
-- 3. Performance Indexes (The Core of FR6.1)
-- ===========================================================================
-- Fulfills FR6.1 AC2: Composite B-Tree index for instant pagination.
-- The "DESC" keyword physically pre-sorts the index entries so the newest 
-- transactions are always at the top of the B-Tree for a given account.
CREATE INDEX idx_transactions_account_created ON transactions (account_id, created_at DESC);
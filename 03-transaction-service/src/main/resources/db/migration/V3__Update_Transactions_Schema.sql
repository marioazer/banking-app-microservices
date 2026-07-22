-- ===========================================================================
-- V3__Update_Transactions_Schema.sql
-- Purpose: Introduce status tracking for fraud reviews and async transfers.
-- ===========================================================================

-- 1. Create a strict Enum for the transaction lifecycle state machine
CREATE TYPE transaction_status_enum AS ENUM (
    'COMPLETED',
    'PENDING_APPROVAL',
    'REJECTED',
    'FAILED'
);

-- 2. Add the status column to the existing transactions table.
-- Using DEFAULT 'COMPLETED' is a critical safety measure: it ensures that 
-- all existing legacy transactions in the database are automatically mapped 
-- to a finalized state, preventing NullPointerExceptions in the Java layer.
ALTER TABLE transactions 
ADD COLUMN status transaction_status_enum NOT NULL DEFAULT 'COMPLETED';

-- 3. (Optional but recommended) Add an index to query pending transactions quickly
-- Fraud systems will frequently query the database looking for PENDING_APPROVAL rows.
CREATE INDEX idx_transactions_status ON transactions (status) WHERE status = 'PENDING_APPROVAL';
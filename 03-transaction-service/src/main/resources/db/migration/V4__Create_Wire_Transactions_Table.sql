-- ===========================================================================
-- V4__Create_Wire_Transactions_Table.sql
-- Purpose: Create the "wire_transactions" table TransactionEntity has always mapped to.
-- Previously missing entirely - ExternalWireService/InternalFraudController would fail on
-- first write against a real (non-H2) Postgres instance without this.
-- Reuses transaction_status_enum from V3, which already has the exact 4 values
-- (COMPLETED/PENDING_APPROVAL/REJECTED/FAILED) TransactionStatus.java declares.
-- ===========================================================================

CREATE TABLE wire_transactions (
    transaction_id UUID PRIMARY KEY,
    account_id BIGINT NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    status transaction_status_enum NOT NULL,
    description VARCHAR(255) NOT NULL
);

-- InternalFraudController looks up pending wires and FraudResolutionService re-locates rows by
-- account when reversing - index the account lookup path, same reasoning as V3's status index.
CREATE INDEX idx_wire_transactions_account_id ON wire_transactions (account_id);

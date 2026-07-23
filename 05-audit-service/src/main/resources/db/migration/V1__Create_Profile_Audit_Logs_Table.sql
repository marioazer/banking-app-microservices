-- ===========================================================================
-- V1__Create_Profile_Audit_Logs_Table.sql
-- Fulfills FR4.3 Technical Task: "Create the audit_logs table in the Audit
-- Service's distinct PostgreSQL database using Flyway."
-- ===========================================================================

CREATE TABLE profile_audit_logs (
    id BIGSERIAL PRIMARY KEY,

    -- FR4.3 AC3: timestamp, user ID, changed fields, and old/new values
    "timestamp" TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,

    -- Serialized JSON blob of the "changes" object (old/new values) from ProfileUpdatedEvent
    changed_fields_json TEXT NOT NULL

    -- FR4.3 AC4: append-only. No update/delete triggers, no soft-delete column, no
    -- updated_at column - deliberately absent, matching AuditLogEntity's getter-only,
    -- setter-free immutability in the Java layer.
);

-- Fulfills FR4.3 AC1/AC3: audit lookups are always by user, so index on user_id.
CREATE INDEX idx_profile_audit_logs_user_id ON profile_audit_logs (user_id);

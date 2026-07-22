package com.example.auditservice.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Immutable entity representing a change in a user's profile.
 * Fulfills FR4.3 AC4: No updates or deletes are permitted on this table.
 */
@Entity
@Table(name = "profile_audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    // Storing the changes (old and new values) as a serialized JSON string for flexibility.
    // In PostgreSQL, this column should ideally be of type JSONB for optimized querying.
    @Column(name = "changed_fields_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String changedFieldsJson;

    public AuditLogEntity() {}

    public AuditLogEntity(LocalDateTime timestamp, Long userId, String eventType, String changedFieldsJson) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.eventType = eventType;
        this.changedFieldsJson = changedFieldsJson;
    }

    // Notice: ONLY Getters are provided. No Setters exist to strictly enforce immutability in Java.
    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Long getUserId() { return userId; }
    public String getEventType() { return eventType; }
    public String getChangedFieldsJson() { return changedFieldsJson; }
}
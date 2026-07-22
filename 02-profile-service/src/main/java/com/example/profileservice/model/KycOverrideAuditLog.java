package com.example.profileservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_override_audit_logs")
public class KycOverrideAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long adminId;

    @Enumerated(EnumType.STRING)
    private KycStatus oldStatus;

    @Enumerated(EnumType.STRING)
    private KycStatus newStatus;

    private String reason;
    private LocalDateTime timestamp;

    public KycOverrideAuditLog() {}

    public KycOverrideAuditLog(Long userId, Long adminId, KycStatus oldStatus, KycStatus newStatus, String reason) {
        this.userId = userId;
        this.adminId = adminId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAdminId() { return adminId; }
    public KycStatus getOldStatus() { return oldStatus; }
    public KycStatus getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
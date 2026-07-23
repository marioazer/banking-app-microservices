package com.example.auditservice.repository;

import com.example.auditservice.model.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Fulfills FR4.3 AC4: the Audit Service must never update or delete these records - only
 * JpaRepository's inherited save()/insert path is used anywhere in this service; no update
 * or delete method is ever called against it.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
}

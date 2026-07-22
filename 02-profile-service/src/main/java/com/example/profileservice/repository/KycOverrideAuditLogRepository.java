package com.example.profileservice.repository;

import com.example.profileservice.model.KycOverrideAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KycOverrideAuditLogRepository extends JpaRepository<KycOverrideAuditLog, Long> {
}
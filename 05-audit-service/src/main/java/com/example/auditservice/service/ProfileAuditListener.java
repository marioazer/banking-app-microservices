package com.example.auditservice.service;

import com.example.auditservice.model.AuditLogEntity;
import com.example.auditservice.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ProfileAuditListener {

    private static final Logger logger = LoggerFactory.getLogger(ProfileAuditListener.class);
    
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public ProfileAuditListener(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes messages from the profile-events topic.
     * Uses a specific groupId to ensure it gets a copy of the message independently of other services.
     */
    @KafkaListener(topics = "profile-events", groupId = "audit-service-group")
    public void consumeProfileUpdate(Map<String, Object> eventPayload) {
        try {
            logger.info("Received profile update event for Audit Logging: {}", eventPayload);

            AuditLogEntity auditRecord = toAuditRecord(eventPayload);

            auditLogRepository.save(auditRecord);
            logger.info("Successfully persisted audit log for User ID: {}", auditRecord.getUserId());

        } catch (Exception e) {
            logger.error("Failed to process profile audit event", e);
            // In a production system, we would route this to a Dead Letter Queue (DLQ)
            // so the raw message isn't lost if parsing fails.
        }
    }

    private AuditLogEntity toAuditRecord(Map<String, Object> eventPayload) throws JsonProcessingException {
        // 1. Parse the incoming Kafka JSON payload (FR4.3 AC2)
        Long userId = Long.valueOf(eventPayload.get("userId").toString());
        String eventType = (String) eventPayload.get("eventType");

        // The timestamp might come across as an array or string depending on Jackson config,
        // but for simplicity we'll create the timestamp at the time of processing,
        // or parse it directly from the event.

        // 2. Extract the changes map and serialize it back to a JSON string for DB storage
        Object changesObj = eventPayload.get("changes");
        String changesJson = objectMapper.writeValueAsString(changesObj);

        // 3. Map to the Entity (FR4.3 AC3)
        return new AuditLogEntity(
                LocalDateTime.now(),
                userId,
                eventType,
                changesJson
        );
    }
}
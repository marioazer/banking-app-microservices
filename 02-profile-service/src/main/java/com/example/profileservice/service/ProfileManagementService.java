package com.example.profileservice.service;

import com.example.profileservice.dto.UpdateContactInfoRequestDto;
import com.example.profileservice.model.KycOverrideAuditLog;
import com.example.profileservice.model.KycStatus;
import com.example.profileservice.model.UserProfile;
import com.example.profileservice.repository.KycOverrideAuditLogRepository;
import com.example.profileservice.repository.UserProfileRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProfileManagementService {

    private final UserProfileRepository userProfileRepository;
    private final KycOverrideAuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Kafka Topic Constants
    private static final String PROFILE_EVENTS_TOPIC = "profile-events";
    private static final String KYC_EVENTS_TOPIC = "kyc-events";

    public ProfileManagementService(UserProfileRepository userProfileRepository,
                                    KycOverrideAuditLogRepository auditLogRepository,
                                    KafkaTemplate<String, Object> kafkaTemplate) {
        this.userProfileRepository = userProfileRepository;
        this.auditLogRepository = auditLogRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // ==========================================
    // FR4.1 & 4.2: User Profile Updates
    // ==========================================
    
    @Transactional
    public void updateContactInfo(Long userId, UpdateContactInfoRequestDto dto) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Capture the before state for the audit trail
        Map<String, String> oldState = captureOldContactState(user);

        // 2. Apply new state
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setAddressLine1(dto.getAddressLine1());
        user.setAddressLine2(dto.getAddressLine2());
        user.setCity(dto.getCity());
        user.setState(dto.getState());
        user.setZipCode(dto.getZipCode());

        // 3. Save to database
        userProfileRepository.save(user);

        // 4. Publish to Kafka with userId as the routing key
        publishContactInfoChangedEvent(userId, oldState, dto);
    }

    private Map<String, String> captureOldContactState(UserProfile user) {
        Map<String, String> oldState = new HashMap<>();
        oldState.put("phoneNumber", user.getPhoneNumber());
        oldState.put("addressLine1", user.getAddressLine1());
        oldState.put("city", user.getCity());
        // ... (capture other fields as needed)
        return oldState;
    }

    private void publishContactInfoChangedEvent(Long userId, Map<String, String> oldState, UpdateContactInfoRequestDto dto) {
        Map<String, Object> changes = Map.of("old", oldState, "new", dto);
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                userId, LocalDateTime.now(), "CONTACT_INFO_CHANGE", changes
        );

        kafkaTemplate.send(PROFILE_EVENTS_TOPIC, String.valueOf(userId), event);
    }

    // ==========================================
    // FR3.2 & 3.3: Automated Webhook Processing
    // ==========================================

    @Transactional
    public void processKycWebhook(Long userId, KycStatus newStatus) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getKycStatus() == newStatus) {
            return; // Idempotent check: Prevent spamming Kafka if status hasn't actually changed
        }

        KycStatus oldStatus = user.getKycStatus();
        user.setKycStatus(newStatus);
        userProfileRepository.save(user);

        // Broadcast to Notification Service
        KycStatusUpdatedEvent event = new KycStatusUpdatedEvent(userId, oldStatus, newStatus, LocalDateTime.now());
        kafkaTemplate.send(KYC_EVENTS_TOPIC, String.valueOf(userId), event);
    }

    // ==========================================
    // FR3.4: Manual Admin Overrides
    // ==========================================

    @Transactional
    public void adminOverrideKyc(Long userId, Long adminId, KycStatus newStatus, String reason) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        KycStatus oldStatus = user.getKycStatus();

        // 1. Log the override in the immutable audit table
        recordOverrideAuditLog(userId, adminId, oldStatus, newStatus, reason);

        // 2. Update the user's status
        user.setKycStatus(newStatus);
        userProfileRepository.save(user);

        // 3. Publish the exact same Kafka event as the webhook
        KycStatusUpdatedEvent event = new KycStatusUpdatedEvent(userId, oldStatus, newStatus, LocalDateTime.now());
        kafkaTemplate.send(KYC_EVENTS_TOPIC, String.valueOf(userId), event);
    }

    private void recordOverrideAuditLog(Long userId, Long adminId, KycStatus oldStatus, KycStatus newStatus, String reason) {
        KycOverrideAuditLog auditLog = new KycOverrideAuditLog(
                userId, adminId, oldStatus, newStatus, reason
        );
        auditLogRepository.save(auditLog);
    }

    // =========================================================================================
    // DTO Records for Kafka Events (Typically placed in a shared library, defined here for clarity)
    // =========================================================================================
    public record ProfileUpdatedEvent(Long userId, LocalDateTime timestamp, String eventType, Map<String, Object> changes) {}
    public record KycStatusUpdatedEvent(Long userId, KycStatus oldStatus, KycStatus newStatus, LocalDateTime timestamp) {}
}
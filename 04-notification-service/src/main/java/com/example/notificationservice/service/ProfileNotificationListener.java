package com.example.notificationservice.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes ProfileUpdatedEvents to notify users of security changes.
 * This service operates in an independent Consumer Group, ensuring
 * it processes events regardless of Audit Service performance.
 */
@Service
public class ProfileNotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(ProfileNotificationListener.class);

    // In a real implementation, you would inject an EmailClient (e.g., SendGrid/AWS SES)
    public ProfileNotificationListener() {
    }

    @KafkaListener(topics = "profile-events", groupId = "notification-service-group")
    public void consumeProfileUpdate(Map<String, Object> eventPayload) {
        try {
            logger.info("Notification Service: Processing profile update for security alert.");

            Long userId = Long.valueOf(eventPayload.get("userId").toString());
            String eventType = (String) eventPayload.get("eventType");

            // Logic: Send the security alert email/SMS (FR4.4)
            sendSecurityAlertEmail(userId, eventType);

            logger.info("Notification Service: Security alert sent for User ID: {}", userId);

        } catch (Exception e) {
            logger.error("Failed to send security notification", e);
        }
    }

    private void sendSecurityAlertEmail(Long userId, String eventType) {
        // Implementation for third-party email provider (e.g., SendGrid/AWS SES)
        String subject = "Security Alert: Your profile was recently updated";
        String body = String.format("Dear customer, an update of type '%s' was made to your profile.", eventType);
        
        logger.info("Email dispatched to user {}: [Subject: {}] [Body: {}]", userId, subject, body);
    }
}d
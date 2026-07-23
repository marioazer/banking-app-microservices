package com.example.notificationservice.client;

/**
 * Boundary to the external email provider (e.g. SendGrid, AWS SES). Extracted as its own
 * seam so NotificationProviderService's @Retryable/@Recover logic has something that can
 * actually fail - a real vendor SDK call at runtime, or a mock in tests - instead of a
 * hardcoded boolean that could never be triggered from outside the class.
 */
public interface EmailProviderClient {

    void send(String userEmail, String subject, String htmlContent);
}

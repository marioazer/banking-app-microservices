package com.example.notificationservice.client;

/**
 * Boundary to the external email provider (e.g. SendGrid, AWS SES). Extracted as its own
 * seam so NotificationProviderService's @Retryable/@Recover logic has something that can
 * actually fail - a real vendor SDK call at runtime, or a mock in tests - instead of a
 * hardcoded boolean that could never be triggered from outside the class.
 */
// this is a plain interface, not a @FeignClient like the other two clients in this package,
// on purpose, this one gets a real implementation class below instead of an auto generated http call
public interface EmailProviderClient {

    void send(String userEmail, String subject, String htmlContent);
}

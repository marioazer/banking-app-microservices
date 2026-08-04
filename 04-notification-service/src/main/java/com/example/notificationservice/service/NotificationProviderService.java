package com.example.notificationservice.service;

import com.example.notificationservice.client.EmailProviderClient;
import com.example.notificationservice.client.SmsProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class NotificationProviderService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProviderService.class);

    private final EmailProviderClient emailProviderClient;
    private final SmsProviderClient smsProviderClient;

    public NotificationProviderService(EmailProviderClient emailProviderClient, SmsProviderClient smsProviderClient) {
        this.emailProviderClient = emailProviderClient;
        this.smsProviderClient = smsProviderClient;
    }

    // learned @retryable needs @enablescheduling's cousin @enableretry turned on somewhere in the
    // app, otherwise this annotation just sits here doing nothing and a failure throws immediately
    @Retryable(
            retryFor = { RuntimeException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void dispatchEmail(String userEmail, String subject, String htmlContent) {
        log.info("Attempting to dispatch email via external provider to [{}]", userEmail);
        emailProviderClient.send(userEmail, subject, htmlContent);
    }

    // learned @recover has a strict signature rule, the first parameter has to be the same
    // exception type @retryable is watching for, and the rest of the parameters have to match
    // the original method's parameters in order, spring uses that shape to match them up
    @Recover
    public void recoverDispatchFailure(RuntimeException e, String userEmail, String subject, String htmlContent) {
        // In a production system, this would write the failed payload to a Dead Letter Queue (DLQ)
        // or a failed_notifications database table for a cron job to retry tomorrow.
        log.error("CRITICAL FAILURE: Exhausted all retries for email to [{}]. Reason: {}", userEmail, e.getMessage());
        log.error("Payload saved to Dead Letter Queue for manual review.");
    }

    @Retryable(
            retryFor = { RuntimeException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void dispatchSms(String phoneNumber, String message) {
        log.info("Attempting to dispatch SMS via external provider to [{}]", phoneNumber);
        smsProviderClient.send(phoneNumber, message);
    }

    @Recover
    public void recoverSmsDispatchFailure(RuntimeException e, String phoneNumber, String message) {
        // Same DLQ story as recoverDispatchFailure - a 2FA code that never arrives is time-sensitive,
        // so this at least keeps the failure from crashing the consumer/blocking other Kafka messages.
        log.error("CRITICAL FAILURE: Exhausted all retries for SMS to [{}]. Reason: {}", phoneNumber, e.getMessage());
        log.error("Payload saved to Dead Letter Queue for manual review.");
    }
}
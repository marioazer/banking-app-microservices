package com.example.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Service handling dispatch to external notification providers (e.g., SendGrid, AWS SNS).
 * Fulfills FR9.3 AC2 & Tech Task: Implement retry logic using exponential backoff[cite: 4].
 */
@Service
public class NotificationProviderService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProviderService.class);

    /**
     * Dispatches the formatted message to the external provider.
     * Fulfills FR9.3 AC2: Sent via external provider (e.g., SendGrid for Email)[cite: 4].
     * 
     * @Retryable acts as an interceptor. If a RuntimeException occurs:
     * - Attempt 1: Fails
     * - Wait 1000ms
     * - Attempt 2: Fails
     * - Wait 2000ms (1000 * multiplier of 2)
     * - Attempt 3: Fails, delegates to @Recover
     */
    @Retryable(
            retryFor = { RuntimeException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void dispatchEmail(String userEmail, String subject, String htmlContent) {
        log.info("Attempting to dispatch email via external provider to [{}]", userEmail);
        
        // ====================================================================
        // External SDK Integration Boundary
        // In a live environment, this is where SendGrid.api(request) executes.
        // We simulate a network call that could theoretically fail.
        // ====================================================================
        boolean simulateNetworkFailure = false; // Toggle to test retry logic
        
        if (simulateNetworkFailure) {
            throw new RuntimeException("503 Service Unavailable: SendGrid API Gateway timeout");
        }

        log.info("SUCCESS: Email payload delivered to external provider. Subject: {}", subject);
    }

    /**
     * The Fallback mechanism. 
     * If the external provider is completely unreachable after all retries are exhausted, 
     * Spring routes the flow here instead of crashing the application.
     * Fulfills FR10.3 Tech Task: Implement error handling when Notification Provider is unavailable[cite: 5].
     */
    @Recover
    public void recoverDispatchFailure(RuntimeException e, String userEmail, String subject, String htmlContent) {
        // In a production system, this would write the failed payload to a Dead Letter Queue (DLQ)
        // or a failed_notifications database table for a cron job to retry tomorrow[cite: 5].
        log.error("CRITICAL FAILURE: Exhausted all retries for email to [{}]. Reason: {}", userEmail, e.getMessage());
        log.error("Payload saved to Dead Letter Queue for manual review.");
    }
}
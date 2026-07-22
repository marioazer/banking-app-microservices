package com.example.notificationservice.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.example.notificationservice.client.ProfileServiceClient;
import com.example.notificationservice.event.FundsTransferredEvent;

@Service
public class TransactionAlertListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionAlertListener.class);

    private final ProfileServiceClient profileServiceClient;
    private final NotificationProviderService notificationProviderService;

    public TransactionAlertListener(ProfileServiceClient profileServiceClient, 
                                    NotificationProviderService notificationProviderService) {
        this.profileServiceClient = profileServiceClient;
        this.notificationProviderService = notificationProviderService;
    }

    /**
     * Listens to the Kafka broker for successful transfer events.
     * Fulfills FR9.2 AC1 & Tech Task: Implement @KafkaListener[cite: 4].
     */
    @KafkaListener(topics = "successful-transfers", groupId = "notification-service-group")
    public void consumeTransferEvent(FundsTransferredEvent event) {
        log.debug("Received transfer event for Transaction ID: {}", event.transactionId());

        try {
            // 1. Perform sub-millisecond lookup via Cached Feign Client[cite: 4]
            ProfileServiceClient.UserPreferenceResponse preferences = 
                    profileServiceClient.getUserPreferences(event.fromAccountId());

            if (preferences == null) {
                log.warn("No preferences found for User ID: {}. Skipping alert.", event.fromAccountId());
                return;
            }

            // 2. Evaluate if the transaction meets the alert criteria[cite: 4]
            if (event.amount().compareTo(preferences.alertThresholdAmount()) >= 0) {
                
                log.info("Transaction {} (Amount: ${}) exceeded threshold (${}). Dispatching alert.", 
                        event.transactionId(), event.amount(), preferences.alertThresholdAmount());

                // 3. Format the alert message[cite: 4]
                String subject = "Bank Alert: Large Debit Transaction";
                String htmlMessage = buildHtmlMessage(event);

                // Note: In a full system, we would fetch the user's email address here. 
                // We use a generated placeholder for the dispatch signature contract.
                String userEmail = "user_" + event.fromAccountId() + "@bank.com";

                // 4. Delegate to the provider service (which handles its own external retries)[cite: 4]
                notificationProviderService.dispatchEmail(userEmail, subject, htmlMessage);
                
            } else {
                log.debug("Transaction {} (Amount: ${}) is below threshold (${}). No alert needed.", 
                        event.transactionId(), event.amount(), preferences.alertThresholdAmount());
            }

        } catch (Exception e) {
            // We catch generic exceptions here so the Kafka Consumer doesn't crash 
            // and get stuck in an infinite loop for a single bad message.
            log.error("Failed to process Kafka event for Transaction ID: {}", event.transactionId(), e);
        }
    }

    /**
     * Formats a clear message including the transactionId, amount, and date.
     * Fulfills FR9.3 AC1[cite: 4].
     */
    private String buildHtmlMessage(FundsTransferredEvent event) {
        return """
               <html>
                   <body>
                       <h2>Transaction Alert</h2>
                       <p>A significant transaction has occurred on your account.</p>
                       <ul>
                           <li><strong>Amount:</strong> $%s</li>
                           <li><strong>Transaction ID:</strong> %s</li>
                           <li><strong>Date:</strong> %s</li>
                       </ul>
                       <p>If you did not authorize this, please contact support immediately.</p>
                   </body>
               </html>
               """.formatted(event.amount(), event.transactionId(), Instant.now().toString());
    }
}
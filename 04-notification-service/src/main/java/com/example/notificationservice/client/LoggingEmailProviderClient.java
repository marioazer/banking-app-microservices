package com.example.notificationservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder EmailProviderClient until a real vendor (SendGrid, AWS SES, etc.) is wired in.
 * Logs instead of actually sending, and never fails on its own.
 */
@Component
public class LoggingEmailProviderClient implements EmailProviderClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailProviderClient.class);

    @Override
    public void send(String userEmail, String subject, String htmlContent) {
        log.info("SUCCESS: Email payload delivered to external provider. To: [{}], Subject: {}", userEmail, subject);
    }
}

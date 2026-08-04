package com.example.notificationservice.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

// consumes the SMS_2FA_REQUESTED event auth-service publishes on every login that needs a 2FA
// code (see AuthSecurityService.publishSmsEvent) - this is the listener that was missing entirely,
// which is why 2FA codes never actually reached a phone before this class existed
@Service
public class TwoFactorSmsListener {

    private static final Logger logger = LoggerFactory.getLogger(TwoFactorSmsListener.class);

    private final NotificationProviderService notificationProviderService;

    public TwoFactorSmsListener(NotificationProviderService notificationProviderService) {
        this.notificationProviderService = notificationProviderService;
    }

    @KafkaListener(topics = "notification-events", groupId = "notification-service-group")
    public void consumeSmsRequest(Map<String, Object> event) {
        try {
            if (!"SMS_2FA_REQUESTED".equals(event.get("action"))) {
                return;
            }

            String phoneNumber = (String) event.get("phoneNumber");
            String code = (String) event.get("code");
            String message = String.format("Your verification code is %s. It expires in 5 minutes.", code);

            notificationProviderService.dispatchSms(phoneNumber, message);

            logger.info("Notification Service: 2FA SMS dispatched to phone number ending in {}",
                    phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)));
        } catch (Exception e) {
            logger.error("Failed to dispatch 2FA SMS", e);
        }
    }
}

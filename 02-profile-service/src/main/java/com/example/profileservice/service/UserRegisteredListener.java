package com.example.profileservice.service;

import com.example.profileservice.model.UserProfile;
import com.example.profileservice.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// auth-service only owns credentials - this is how profile-service learns a new user exists at
// all, so it can provision its own PENDING_VERIFICATION profile row for them (id = userId, same
// convention UserProfile already uses everywhere else).
@Service
public class UserRegisteredListener {

    private static final Logger logger = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final UserProfileRepository userProfileRepository;

    public UserRegisteredListener(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @KafkaListener(topics = "user-events", groupId = "profile-service-group")
    @Transactional
    public void consumeUserRegistered(Map<String, Object> event) {
        try {
            Long userId = Long.valueOf(event.get("userId").toString());

            // idempotent: if a retry/redelivery lands here for a user we already provisioned,
            // do nothing rather than clobber whatever profile data has accumulated since then
            if (userProfileRepository.existsById(userId)) {
                logger.info("Profile already exists for user id {}, skipping provisioning", userId);
                return;
            }

            UserProfile profile = new UserProfile();
            profile.setId(userId);
            profile.setPhoneNumber((String) event.get("phoneNumber"));
            userProfileRepository.save(profile);

            logger.info("Provisioned profile for newly registered user id {}", userId);
        } catch (Exception e) {
            logger.error("Failed to process UserRegistered event", e);
            // In a production system, we would route this to a Dead Letter Queue (DLQ)
            // so the raw message isn't lost if parsing fails.
        }
    }
}

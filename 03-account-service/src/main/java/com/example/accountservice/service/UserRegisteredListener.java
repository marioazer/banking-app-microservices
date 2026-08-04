package com.example.accountservice.service;

import com.example.accountservice.model.AccountEntity;
import com.example.accountservice.model.AccountStatus;
import com.example.accountservice.model.AccountType;
import com.example.accountservice.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;

// auth-service only owns credentials - this is how account-service learns a new user exists at
// all, so it can provision a starter account for them. Every account here shares this bank's
// routing number; account_number just needs to be a unique 20-char value, nothing more specific
// is implied by the schema.
@Service
public class UserRegisteredListener {

    private static final Logger logger = LoggerFactory.getLogger(UserRegisteredListener.class);
    private static final String DEFAULT_ROUTING_NUMBER = "021000021";

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public UserRegisteredListener(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @KafkaListener(topics = "user-events", groupId = "account-service-group")
    @Transactional
    public void consumeUserRegistered(Map<String, Object> event) {
        try {
            Long userId = Long.valueOf(event.get("userId").toString());

            // idempotent: if a retry/redelivery lands here for a user we already provisioned,
            // do nothing rather than hand them a second starter account
            if (accountRepository.existsByUserId(userId)) {
                logger.info("Account already exists for user id {}, skipping provisioning", userId);
                return;
            }

            AccountEntity account = new AccountEntity();
            account.setUserId(userId);
            account.setAccountType(AccountType.CHECKING);
            account.setAvailableBalance(BigDecimal.ZERO);
            account.setRoutingNumber(DEFAULT_ROUTING_NUMBER);
            account.setAccountNumber(generateAccountNumber());
            account.setStatus(AccountStatus.ACTIVE);
            accountRepository.save(account);

            logger.info("Provisioned starter checking account for newly registered user id {}", userId);
        } catch (Exception e) {
            logger.error("Failed to process UserRegistered event", e);
            // In a production system, we would route this to a Dead Letter Queue (DLQ)
            // so the raw message isn't lost if parsing fails.
        }
    }

    private String generateAccountNumber() {
        long number = 100_000_000_000L + (long) (random.nextDouble() * 900_000_000_000L);
        return String.valueOf(number);
    }
}

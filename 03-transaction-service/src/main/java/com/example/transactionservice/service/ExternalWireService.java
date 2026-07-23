package com.example.transactionservice.service;

import com.example.transactionservice.annotation.RequiresKyc;
import com.example.transactionservice.dto.ExternalWireRequestDto;
import com.example.transactionservice.dto.TransferResponseDto;
import com.example.transactionservice.event.LargeTransferRequestedEvent;
import com.example.transactionservice.model.AccountEntity;
import com.example.transactionservice.model.TransactionEntity;
import com.example.transactionservice.model.TransactionStatus;
import com.example.transactionservice.repository.AccountRepository;
import com.example.transactionservice.repository.TransactionRepository;
import com.example.transactionservice.util.IbanSwiftValidator;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ExternalWireService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IbanSwiftValidator validator;
    private final KafkaTemplate<String, LargeTransferRequestedEvent> kafkaTemplate;

    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("5000.00");
    private static final String FRAUD_TOPIC = "large-transfers-review";

    public ExternalWireService(AccountRepository accountRepository,
                               TransactionRepository transactionRepository,
                               IbanSwiftValidator validator,
                               KafkaTemplate<String, LargeTransferRequestedEvent> kafkaTemplate) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.validator = validator;
        this.kafkaTemplate = kafkaTemplate; // Injected to publish high-value transfer events
    }

    /**
     * Processes an external wire transfer, applying format validation and fraud thresholds.
     */
    @Transactional
    @RequiresKyc
    public TransferResponseDto initiateWire(Long userId, Long fromAccountId, ExternalWireRequestDto request) {
        
        // 1. Strict Formatting Validation[cite: 2]
        if (!validator.isValidIban(request.iban()) || !validator.isValidSwift(request.swiftCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IBAN or SWIFT code format.");
        }

        // 2. Lock the account row to prevent race conditions
        AccountEntity fromAccount = accountRepository.findByIdForUpdate(fromAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));

        if (!fromAccount.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized account access.");
        }

        // 3. Verify Sufficient Funds[cite: 2]
        if (fromAccount.getAvailableBalance().compareTo(request.amount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");
        }

        // 4. Pre-reserve the funds by deducting them immediately
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(request.amount()));
        accountRepository.save(fromAccount);

        // 5. Threshold Check Logic[cite: 2]
        TransactionStatus finalStatus = TransactionStatus.COMPLETED;
        if (request.amount().compareTo(FRAUD_THRESHOLD) > 0) {
            finalStatus = TransactionStatus.PENDING_APPROVAL; // Requires manual or automated review[cite: 2]
        }

        // 6. Record the transaction state
        UUID transactionId = UUID.randomUUID();
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionId(transactionId);
        transaction.setAccountId(fromAccountId);
        transaction.setAmount(request.amount());
        transaction.setStatus(finalStatus);
        transaction.setDescription("External Wire to " + request.beneficiaryName());
        transactionRepository.save(transaction);

        // 7. Publish to Kafka if flagged for Fraud Review[cite: 2]
        if (finalStatus == TransactionStatus.PENDING_APPROVAL) {
            LargeTransferRequestedEvent event = new LargeTransferRequestedEvent(
                    transactionId,
                    fromAccountId,
                    request.amount(),
                    request.iban(),
                    request.swiftCode(),
                    request.beneficiaryName()
            );
            // Fire the event to the Fraud Detection Service[cite: 2]
            kafkaTemplate.send(FRAUD_TOPIC, transactionId.toString(), event);
        }

        // 8. Return the UUID and the resulting status (either COMPLETED or PENDING_APPROVAL)
        return new TransferResponseDto(transactionId, finalStatus.name());
    }
}
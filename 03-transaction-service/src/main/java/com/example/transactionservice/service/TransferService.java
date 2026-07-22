package com.example.transactionservice.service;

import com.example.transactionservice.dto.TransferResponseDto;
import com.example.transactionservice.event.FundsTransferredEvent;
import com.example.transactionservice.model.AccountEntity;
import com.example.transactionservice.repository.AccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransferService(AccountRepository accountRepository, 
                           ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes an internal transfer between two accounts owned by the same user.
     * Guaranteed atomic execution via @Transactional.
     */
    @Transactional
    public TransferResponseDto executeTransfer(Long userId, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        
        // 1. Acquire hardware-level Pessimistic Locks on the rows to prevent race conditions
        AccountEntity fromAccount = accountRepository.findByIdForUpdate(fromAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source account not found"));
        
        AccountEntity toAccount = accountRepository.findByIdForUpdate(toAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination account not found"));

        // 2. Strict Ownership Verification[cite: 1]
        if (!fromAccount.getUserId().equals(userId) || !toAccount.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Both accounts must belong to the authenticated user");
        }

        // 3. Verify Sufficient Funds[cite: 1]
        if (fromAccount.getAvailableBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");
        }

        // 4. Atomically Update Balances in Memory
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(amount));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(amount));
        
        // 5. Persist the new balances to the database
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // 6. Generate a globally unique confirmation ID[cite: 1]
        UUID transactionId = UUID.randomUUID();

        // 7. Publish the domain event
        // A @TransactionalEventListener will catch this and send it to Kafka strictly AFTER the commit[cite: 1]
        FundsTransferredEvent event = new FundsTransferredEvent(fromAccountId, toAccountId, amount, transactionId);
        eventPublisher.publishEvent(event);

        // 8. Return confirmation payload[cite: 1]
        return new TransferResponseDto(transactionId, "COMPLETED");
    }
}
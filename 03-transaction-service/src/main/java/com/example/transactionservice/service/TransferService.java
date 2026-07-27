package com.example.transactionservice.service;

import com.example.transactionservice.annotation.RequiresKyc;
import com.example.transactionservice.client.AccountServiceClient;
import com.example.transactionservice.dto.TransferResponseDto;
import com.example.transactionservice.event.FundsTransferredEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountServiceClient accountServiceClient;
    private final ApplicationEventPublisher eventPublisher;

    public TransferService(AccountServiceClient accountServiceClient,
                           ApplicationEventPublisher eventPublisher) {
        this.accountServiceClient = accountServiceClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes an internal transfer between two accounts owned by the same user. The actual
     * balance mutation (ownership check, pessimistic locking, funds check, debit/credit, and
     * recording the transaction-history rows) happens atomically inside account-service, which
     * is the sole owner of the "accounts"/"transactions" tables - this method just orchestrates
     * KYC gating, confirmation-ID generation, and the post-commit domain event.
     */
    // @RequiresKyc is a custom annotation, not a built in spring one, KycEnforcementAspect
    // intercepts any call to a method carrying this and blocks it before the body even starts
    // if the caller's kyc status is not approved, learned this is aop, aspect oriented programming
    @Transactional
    @RequiresKyc
    public TransferResponseDto executeTransfer(Long userId, Long fromAccountId, Long toAccountId, BigDecimal amount) {

        // account-service throws (and FeignErrorConfig's ErrorDecoder re-throws locally as a
        // ResponseStatusException) on insufficient funds, missing accounts, or ownership mismatch -
        // any of those propagate straight out of this call, aborting the transfer.
        accountServiceClient.transfer(new AccountServiceClient.TransferRequest(userId, fromAccountId, toAccountId, amount));

        // Generate a globally unique confirmation ID[cite: 1]
        UUID transactionId = UUID.randomUUID();

        // Publish the domain event
        publishTransferEvent(userId, fromAccountId, toAccountId, amount, transactionId);

        // Return confirmation payload[cite: 1]
        return new TransferResponseDto(transactionId, "COMPLETED");
    }

    private void publishTransferEvent(Long userId, Long fromAccountId, Long toAccountId, BigDecimal amount, UUID transactionId) {
        // A @TransactionalEventListener will catch this and send it to Kafka strictly AFTER the commit[cite: 1]
        // learned applicationeventpublisher.publishevent is spring's own in process event bus,
        // completely separate from kafka, this just hands the event off inside the jvm, some other
        // listener method elsewhere is the one that actually forwards it out to kafka afterward
        FundsTransferredEvent event = new FundsTransferredEvent(userId, fromAccountId, toAccountId, amount, transactionId);
        eventPublisher.publishEvent(event);
    }
}
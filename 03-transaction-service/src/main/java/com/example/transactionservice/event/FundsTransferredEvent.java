package com.example.transactionservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable event record published to Kafka after a successful internal transfer.
 * Fulfills FR7.4 AC2: The event must contain fromAccountId, toAccountId, amount, and transactionId[cite: 3].
 */
public record FundsTransferredEvent(
        
        // The ID of the account where funds were deducted
        Long fromAccountId,
        
        // The ID of the account receiving the funds
        Long toAccountId,
        
        // The exact monetary value transferred
        BigDecimal amount,
        
        // The unique identifier for the transaction to be included in the email receipt
        UUID transactionId
        
) {}
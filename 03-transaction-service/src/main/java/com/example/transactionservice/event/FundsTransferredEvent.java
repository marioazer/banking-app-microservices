package com.example.transactionservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable event record published to Kafka after a successful internal transfer.
 * Fulfills FR7.4 AC2: The event must contain fromAccountId, toAccountId, amount, and transactionId[cite: 3].
 *
 * Carries userId (the authenticated owner of both accounts, per TransferService's ownership
 * check) alongside the account IDs so downstream consumers - like Notification Service's
 * alert-threshold lookup - don't have to mistake an account ID for a user ID.
 */
public record FundsTransferredEvent(

        // The user who owns both accounts and initiated the transfer
        Long userId,

        // The ID of the account where funds were deducted
        Long fromAccountId,

        // The ID of the account receiving the funds
        Long toAccountId,

        // The exact monetary value transferred
        BigDecimal amount,

        // The unique identifier for the transaction to be included in the email receipt
        UUID transactionId

) {}
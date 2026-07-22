package com.example.notificationservice.event;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable event record for deserializing Kafka messages from the Transaction Service.
 * Fulfills FR9.2 AC1: Consumes FundsTransferredEvent messages[cite: 4].
 * 
 * The Tolerant Reader pattern (@JsonIgnoreProperties) prevents deserialization 
 * exceptions if the Transaction Service adds new fields in the future.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundsTransferredEvent(
        
        // The account that initiated the transfer (and should receive the alert)
        Long fromAccountId,
        
        // The destination account
        Long toAccountId,
        
        // The monetary value used to evaluate against the alert_threshold_amount[cite: 4]
        BigDecimal amount,
        
        // The unique ID to format into the clear message receipt[cite: 4]
        UUID transactionId
        
) {}
package com.example.notificationservice.event;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// learned this is its own separate record from transaction-service's FundsTransferredEvent,
// two different classes in two different services that both just happen to deserialize the
// exact same kafka json payload, @JsonIgnoreProperties is what keeps that loosely coupled
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundsTransferredEvent(

        // The user who owns both accounts - use this for preference/threshold lookups,
        // NOT fromAccountId, which is an account ID from a different ID sequence entirely.
        Long userId,

        // The account that initiated the transfer
        Long fromAccountId,

        // The destination account
        Long toAccountId,

        // The monetary value used to evaluate against the alert_threshold_amount
        BigDecimal amount,

        // The unique ID to format into the clear message receipt
        UUID transactionId

) {}
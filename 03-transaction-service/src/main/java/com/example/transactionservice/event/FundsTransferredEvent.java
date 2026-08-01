package com.example.transactionservice.event;

import java.math.BigDecimal;
import java.util.UUID;

// records make sense for events specifically, an event describes something that already happened
// and should never be mutated after the fact, records being immutable fits that naturally
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
package com.example.transactionservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public record LargeTransferRequestedEvent(
        
        // The unique identifier of the transaction sitting in PENDING_APPROVAL state
        UUID transactionId,
        
        // The source account ID initiating the transfer
        Long fromAccountId,
        
        // The exact monetary value that triggered the fraud threshold
        BigDecimal amount,
        
        // The international bank account number of the recipient
        String iban,
        
        // The SWIFT/BIC code of the destination bank
        String swiftCode,
        
        // The name of the person or business receiving the funds
        String beneficiaryName
        
) {}
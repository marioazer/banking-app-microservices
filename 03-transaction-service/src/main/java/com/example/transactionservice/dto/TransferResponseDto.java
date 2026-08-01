package com.example.transactionservice.dto;

import java.util.UUID;

public record TransferResponseDto(
        
        // A globally unique identifier generated specifically for this transfer event
        UUID transactionId,
        
        // The final state of the transaction (e.g., "COMPLETED")
        String status
        
) {}
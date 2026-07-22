package com.example.transactionservice.dto;

import java.util.UUID;

/**
 * Data Transfer Object representing the final confirmation payload sent back to the client.
 * Fulfills FR7.3 AC1: The service must return a JSON response containing 
 * a transactionId (UUID) and a status: "COMPLETED".
 */
public record TransferResponseDto(
        
        // A globally unique identifier generated specifically for this transfer event
        UUID transactionId,
        
        // The final state of the transaction (e.g., "COMPLETED")
        String status
        
) {}
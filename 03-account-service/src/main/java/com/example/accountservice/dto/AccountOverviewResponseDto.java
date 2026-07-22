package com.example.accountservice.dto;

import java.math.BigDecimal;

/**
 * DTO ensuring raw account numbers are never exposed to the frontend.[cite: 3]
 */
public record AccountOverviewResponseDto(
        Long accountId,
        String accountType,
        BigDecimal availableBalance,
        String routingNumber, // Routing numbers are public banking info and sent in plain text[cite: 3]
        String maskedAccountNumber,
        String status
) {
}
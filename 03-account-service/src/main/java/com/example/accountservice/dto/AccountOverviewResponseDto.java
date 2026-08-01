package com.example.accountservice.dto;

import java.math.BigDecimal;

public record AccountOverviewResponseDto(
        Long accountId,
        String accountType,
        BigDecimal availableBalance,
        String routingNumber, // Routing numbers are public banking info and sent in plain text
        String maskedAccountNumber,
        String status
) {
}
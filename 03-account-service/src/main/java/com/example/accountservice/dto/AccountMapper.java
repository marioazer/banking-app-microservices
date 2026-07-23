package com.example.accountservice.mapper;

import org.springframework.stereotype.Component;

import com.example.accountservice.dto.AccountOverviewResponseDto;
import com.example.accountservice.model.AccountEntity;

@Component
public class AccountMapper {

    /**
     * Converts the database entity into a secure DTO for the frontend dashboard.
     */
    public AccountOverviewResponseDto toOverviewDto(AccountEntity entity) {
        return new AccountOverviewResponseDto(
                entity.getId(),
                entity.getAccountType().name(),
                entity.getAvailableBalance(),
                entity.getRoutingNumber(),
                maskAccountNumber(entity.getAccountNumber()),
                entity.getStatus().name()
        );
    }

    /**
     * Masks the account number, leaving only the last 4 digits visible.
     * Fulfills FR5.2 AC2.[cite: 3]
     */
    private String maskAccountNumber(String rawAccountNumber) {
        if (rawAccountNumber == null) {
            return rawAccountNumber; // Failsafe for unusually short or malformed numbers
        }
        if (rawAccountNumber.length() <= 4) {
            return rawAccountNumber; // Failsafe for unusually short or malformed numbers
        }
        
        int length = rawAccountNumber.length();
        String lastFourDigits = rawAccountNumber.substring(length - 4);
        
        // Creates a string of dots for the hidden portion
        String mask = ".".repeat(length - 4);
        
        return mask + lastFourDigits;
    }
}
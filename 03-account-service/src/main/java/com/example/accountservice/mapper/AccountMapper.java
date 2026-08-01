package com.example.accountservice.mapper;

import org.springframework.stereotype.Component;

import com.example.accountservice.dto.AccountOverviewResponseDto;
import com.example.accountservice.model.AccountEntity;

// separating this mapping logic into its own @component instead of stuffing it into the service
// or the entity keeps the entity to db mapping and the entity to api response mapping independent
@Component
public class AccountMapper {

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
        // learned string.repeat is a pretty recent java addition, used to have to build this
        // kind of padding with a loop or stringbuilder before it existed
        String mask = ".".repeat(length - 4);

        return mask + lastFourDigits;
    }
}

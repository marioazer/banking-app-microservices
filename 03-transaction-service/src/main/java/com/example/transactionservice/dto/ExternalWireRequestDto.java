package com.example.transactionservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ExternalWireRequestDto(

        @NotBlank(message = "IBAN is mandatory")
        // Basic structural regex for IBAN: 2 letters, 2 digits, followed by 11 to 30 alphanumeric characters
        // learned this regex only checks the shape, not whether the iban is a real valid one,
        // the actual mod 97 checksum math lives separately in IbanSwiftValidator
        @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$", message = "Invalid IBAN structure provided")
        String iban,

        @NotBlank(message = "SWIFT/BIC code is mandatory")
        // Standard SWIFT/BIC format: 6 letters, 2 alphanumeric, and an optional 3 alphanumeric branch code
        @Pattern(regexp = "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$", message = "Invalid SWIFT/BIC format provided")
        String swiftCode,

        @NotBlank(message = "Beneficiary name is mandatory")
        @Size(max = 100, message = "Beneficiary name must not exceed 100 characters")
        String beneficiaryName,

        @NotNull(message = "Transfer amount is mandatory")
        @Positive(message = "Transfer amount must be strictly greater than zero")
        @DecimalMin(value = "0.01", message = "Minimum transfer amount is 0.01")
        BigDecimal amount

) {}
package com.example.transactionservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for initiating external wire transfers.
 * Fulfills FR8.1 AC1 & AC2: Accepts recipient details and validates formats.
 */
public record ExternalWireRequestDto(

        @NotBlank(message = "IBAN is mandatory")
        // Basic structural regex for IBAN: 2 letters, 2 digits, followed by 11 to 30 alphanumeric characters
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
package com.example.transactionservice.util;

import java.math.BigInteger;

import org.springframework.stereotype.Component;

/**
 * Utility service for validating international banking formats.
 * Fulfills FR8.1 AC2: The system must validate the recipient bank details format 
 * (IBAN and SWIFT/BIC standard validation).
 */
@Component
public class IbanSwiftValidator {

    private static final String SWIFT_REGEX = "^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$";

    /**
     * Performs ISO 13616 Modulo-97 mathematical validation on an IBAN.
     * 
     * @param iban The raw IBAN string
     * @return true if mathematically valid, false otherwise
     */
    public boolean isValidIban(String iban) {
        if (iban == null) {
            return false;
        }

        // Standardize the format
        String normalizedIban = iban.replaceAll("\\s+", "").toUpperCase();

        if (normalizedIban.length() < 15) {
            return false;
        }
        if (normalizedIban.length() > 34) {
            return false;
        }

        // Step 1: Move the first four characters to the end of the string
        String rearrangedIban = normalizedIban.substring(4) + normalizedIban.substring(0, 4);

        // Step 2: Convert letters to numeric values (A=10, B=11, ... Z=35)
        StringBuilder numericIban = new StringBuilder();
        for (int i = 0; i < rearrangedIban.length(); i++) {
            char ch = rearrangedIban.charAt(i);
            if (Character.isLetter(ch)) {
                numericIban.append(Character.getNumericValue(ch));
            } else if (Character.isDigit(ch)) {
                numericIban.append(ch);
            } else {
                return false; // Contains invalid non-alphanumeric characters
            }
        }

        // Step 3: Perform Modulo 97 check
        try {
            BigInteger ibanNumber = new BigInteger(numericIban.toString());
            return ibanNumber.remainder(new BigInteger("97")).intValue() == 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates a SWIFT/BIC code based on standard structural length and character constraints.
     * 
     * @param swiftCode The raw SWIFT/BIC string
     * @return true if structurally valid, false otherwise
     */
    public boolean isValidSwift(String swiftCode) {
        if (swiftCode == null) {
            return false;
        }

        String normalizedSwift = swiftCode.trim().toUpperCase();

        // Must be exactly 8 (primary office) or 11 (specific branch) characters long
        boolean isValidLength = normalizedSwift.length() == 8 || normalizedSwift.length() == 11;
        if (!isValidLength) {
            return false;
        }

        return normalizedSwift.matches(SWIFT_REGEX);
    }
}
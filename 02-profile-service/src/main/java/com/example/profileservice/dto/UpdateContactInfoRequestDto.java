package com.example.profileservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO representing the incoming JSON payload for a profile contact info update.
 */
public class UpdateContactInfoRequestDto {

    // Regex strictly validates E.164 international phone number format (e.g., +14155552671)
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be a valid international format")
    private String phoneNumber;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 cannot exceed 255 characters")
    private String addressLine1;

    // Optional field, so no @NotBlank constraint
    @Size(max = 255, message = "Address line 2 cannot exceed 255 characters")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @NotBlank(message = "State/Province is required")
    @Size(max = 50, message = "State/Province cannot exceed 50 characters")
    private String state;

    @NotBlank(message = "Zip/Postal code is required")
    @Size(max = 20, message = "Zip/Postal code cannot exceed 20 characters")
    private String zipCode;

    // --- Getters and Setters ---
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
}
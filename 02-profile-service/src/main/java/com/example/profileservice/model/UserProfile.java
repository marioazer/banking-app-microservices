package com.example.profileservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    private Long id;

    private String phoneNumber;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;

    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus = KycStatus.PENDING_VERIFICATION;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public KycStatus getKycStatus() { return kycStatus; }
    public void setKycStatus(KycStatus kycStatus) { this.kycStatus = kycStatus; }
}
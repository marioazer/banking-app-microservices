package com.example.profileservice.model;

/**
 * Represents the Know Your Customer (KYC) verification state of a user.
 * This directly maps to the kyc_status_enum in the PostgreSQL database.
 */
public enum KycStatus {
    
    /**
     * Default state upon creation. The user cannot perform any financial transactions.
     */
    PENDING_VERIFICATION,

    /**
     * The third-party KYC vendor or an internal compliance officer has verified the user's identity.
     * The user is fully authorized to use the banking platform.
     */
    APPROVED,

    /**
     * The identity verification failed (e.g., fraudulent documents).
     * The account remains locked.
     */
    REJECTED
}
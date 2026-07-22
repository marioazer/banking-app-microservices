package com.example.authservice.security;

/**
 * Defines the strict boundaries of JWT authorization levels within the system.
 */
public enum TokenType {
    
    /**
     * Issued when a user provides correct credentials on an unrecognized device.
     * This token CANNOT access banking APIs. It is only valid for calling /verify-2fa.
     */
    PRE_AUTH,

    /**
     * Issued only after successful device recognition or 2FA completion.
     * This token grants full access to the banking ecosystem for 15 minutes.
     */
    FULL_AUTH
}
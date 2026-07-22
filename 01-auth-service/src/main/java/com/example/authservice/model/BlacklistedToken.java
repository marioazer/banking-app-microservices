package com.example.authservice.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "revoked_jwt_blacklist")
public class BlacklistedToken {

    // Notice we do NOT use @GeneratedValue here. 
    // The JWT 'jti' claim is already a globally unique UUID.
    @Id
    @Column(name = "jti", length = 36, nullable = false)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false, updatable = false)
    private LocalDateTime revokedAt;

    // --- Constructors ---

    public BlacklistedToken() {
        this.revokedAt = LocalDateTime.now();
    }

    public BlacklistedToken(String jti, LocalDateTime expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.revokedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getRevokedAt() { return revokedAt; }
}
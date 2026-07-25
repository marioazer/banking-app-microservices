package com.example.authservice.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// no lombok here, this class writes its own getters/setters by hand further down, learned both
// approaches exist side by side in this codebase depending on which model file you look at
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // We store a hashed version of the token so a database dump doesn't leak active sessions
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    // The Soft Delete flag. True means the token was manually killed before natural expiration.
    @Column(nullable = false)
    private Boolean revoked;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // --- Constructors ---

    public RefreshToken() {
        this.createdAt = LocalDateTime.now();
    }

    public RefreshToken(Long userId, String tokenHash) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.revoked = false;
        this.createdAt = LocalDateTime.now();
        // Sets expiration to 24 hours from creation
        this.expiresAt = LocalDateTime.now().plusHours(24);
    }

    // --- Rich Domain Helper Methods ---

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isValid() {
        return !this.revoked && !isExpired();
    }

    public void revoke() {
        this.revoked = true;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public Boolean getRevoked() { return revoked; }
    // We use the revoke() helper method instead of a raw setter for safety

    public LocalDateTime getExpiresAt() { return expiresAt; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
}
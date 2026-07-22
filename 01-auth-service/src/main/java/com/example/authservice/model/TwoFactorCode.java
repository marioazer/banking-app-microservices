package com.example.authservice.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "two_factor_codes")
public class TwoFactorCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // The hashed version of the 6-digit code
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // --- Constructors ---

    public TwoFactorCode() {
        this.createdAt = LocalDateTime.now();
    }

    public TwoFactorCode(Long userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.attempts = 0;
        this.createdAt = LocalDateTime.now();
        // Strict 5-minute expiration per FR1.3 AC2
        this.expiresAt = LocalDateTime.now().plusMinutes(5);
    }

    // --- Rich Domain Helper Methods ---

    /**
     * Checks if the 5-minute window has passed.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    /**
     * Safely increments the failed attempt counter.
     */
    public void incrementAttempts() {
        this.attempts++;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
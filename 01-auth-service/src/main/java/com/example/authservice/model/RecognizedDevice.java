package com.example.authservice.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recognized_devices")
public class RecognizedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // We do not map the entire User object here to keep the Auth Service lightweight.
    // We only need the userId to associate the device.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Storing the hash, never the raw HttpOnly cookie value
    @Column(name = "device_hash", nullable = false, unique = true)
    private String deviceHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login", nullable = false)
    private LocalDateTime lastLogin;

    // --- Constructors ---
    
    public RecognizedDevice() {
        this.createdAt = LocalDateTime.now();
        this.lastLogin = LocalDateTime.now();
    }

    public RecognizedDevice(Long userId, String deviceHash) {
        this.userId = userId;
        this.deviceHash = deviceHash;
        this.createdAt = LocalDateTime.now();
        this.lastLogin = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    // No setId() required; database handles generation.

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDeviceHash() { return deviceHash; }
    public void setDeviceHash(String deviceHash) { this.deviceHash = deviceHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    // No setCreatedAt() required; it should be immutable after creation.

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
}
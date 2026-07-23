package com.example.authservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.authservice.model.RefreshToken;
import com.example.authservice.model.User;
import com.example.authservice.repository.RefreshTokenRepository;
import com.example.authservice.security.TokenType;
import com.example.authservice.service.AuthSecurityService;
import com.example.authservice.service.JwtService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthSecurityService authSecurityService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          AuthSecurityService authSecurityService,
                          RefreshTokenRepository refreshTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authSecurityService = authSecurityService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ==========================================
    // 1. Initial Login Phase
    // ==========================================

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request,
                                   @CookieValue(name = "Device-ID", required = false) String deviceCookie) {
        
        String username = request.get("username");
        String password = request.get("password");

        // 1. Verify password (Throws BadCredentialsException if wrong)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
        User user = (User) authentication.getPrincipal(); // Assuming custom UserDetails implementation

        // 2. Check Device Fingerprint
        boolean isRecognized = authSecurityService.isDeviceRecognized(user.getId(), deviceCookie);

        if (isRecognized) {
            return handleRecognizedDeviceLogin(user);
        } else {
            return handleUnrecognizedDeviceLogin(user);
        }
    }

    private ResponseEntity<?> handleRecognizedDeviceLogin(User user) {
        // Bypass 2FA - Issue Full Access
        String fullJwt = jwtService.generateToken(user, TokenType.FULL_AUTH);
        ResponseCookie refreshCookie = createRefreshTokenCookie(user.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(Map.of("status", "SUCCESS", "access_token", fullJwt));
    }

    private ResponseEntity<?> handleUnrecognizedDeviceLogin(User user) {
        // Unrecognized Device - Require 2FA
        String preAuthJwt = jwtService.generateToken(user, TokenType.PRE_AUTH);
        authSecurityService.triggerSms2fa(user.getId(), user.getPhoneNumber());

        return ResponseEntity.accepted()
                .body(Map.of("status", "2FA_REQUIRED", "pre_auth_token", preAuthJwt));
    }

    // ==========================================
    // 2. 2FA Verification Phase
    // ==========================================

    @PostMapping("/verify-2fa/sms")
    public ResponseEntity<?> verifySms(@RequestBody Map<String, String> request) {
        
        // Thanks to our JwtAuthenticationFilter, we ALREADY know who this user is securely!
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();
        String code = request.get("code");

        // 1. Verify the code
        boolean isValid = authSecurityService.verifySms2fa(user.getId(), code);

        if (!isValid) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid 2FA code"));
        }

        // 2. Success! Issue Full Access, generate new Device ID, and new Refresh Token
        return buildSuccessfulAuthResponse(user);
    }

    private ResponseEntity<?> buildSuccessfulAuthResponse(User user) {
        String fullJwt = jwtService.generateToken(user, TokenType.FULL_AUTH);
        String rawDeviceId = authSecurityService.registerNewDevice(user.getId());

        ResponseCookie deviceCookie = ResponseCookie.from("Device-ID", rawDeviceId)
                .httpOnly(true).secure(true).path("/").maxAge(31536000) // 1 Year
                .build();

        ResponseCookie refreshCookie = createRefreshTokenCookie(user.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deviceCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(Map.of("status", "SUCCESS", "access_token", fullJwt));
    }

    // ==========================================
    // 3. Sliding Session Refresh Phase
    // ==========================================

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshSession(@CookieValue(name = "Refresh-Token", required = false) String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token missing"));
        }

        RefreshToken storedToken = validateAndFetchRefreshToken(refreshToken);
        if (storedToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token expired or revoked"));
        }

        // Token is valid! Issue a fresh 15-minute Access Token
        User user = getUserById(storedToken.getUserId()); // Utility to fetch user
        String newJwt = jwtService.generateToken(user, TokenType.FULL_AUTH);

        return ResponseEntity.ok(Map.of("access_token", newJwt));
    }

    private RefreshToken validateAndFetchRefreshToken(String refreshToken) {
        String hashedToken = hashString(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (!storedToken.isValid()) {
            return null;
        }
        return storedToken;
    }

    // ==========================================
    // 4. Explicit Logout Phase
    // ==========================================

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            if (authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                User user = (User) auth.getPrincipal();

                // Blacklist the token and revoke session
                authSecurityService.logoutUserSession(user.getId(), jwtService.extractJti(jwt), jwtService.extractExpirationDate(jwt));
            }
        }

        // Destroy the cookies on the frontend
        ResponseCookie clearRefresh = ResponseCookie.from("Refresh-Token", "").maxAge(0).path("/").build();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                .body(Map.of("message", "Logged out successfully"));
    }

    // ==========================================
    // Internal Utilities
    // ==========================================

    private ResponseCookie createRefreshTokenCookie(Long userId) {
        String rawToken = UUID.randomUUID().toString();
        refreshTokenRepository.save(new RefreshToken(userId, hashString(rawToken)));
        
        return ResponseCookie.from("Refresh-Token", rawToken)
                .httpOnly(true).secure(true).path("/") // Path "/" means it's sent on all endpoints
                .maxAge(86400) // 24 Hours
                .build();
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedHash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash", e);
        }
    }
    private User getUserById(Long userId) {
        User dummyUser = new User();
        dummyUser.setId(userId);
        dummyUser.setUsername("johndoe");
        return dummyUser;
    }
    
    // Note: getUserById() mock omitted for brevity; this would call a UserRepository.
}
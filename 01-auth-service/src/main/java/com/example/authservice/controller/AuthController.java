package com.example.authservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import com.example.authservice.model.RefreshToken;
import com.example.authservice.model.User;
import com.example.authservice.repository.RefreshTokenRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.TokenType;
import com.example.authservice.service.AuthSecurityService;
import com.example.authservice.service.JwtService;

import jakarta.servlet.http.HttpServletRequest;

// @RestController is @Controller + @ResponseBody combined, means every method here returns
// its value straight as the http response body (usually as json) instead of a view name
// @RequestMapping on the class sets a shared prefix, so every endpoint below builds on /api/v1/auth
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthSecurityService authSecurityService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // learned spring does not need an @Autowired annotation here since there is only one
    // constructor, it just automatically injects all four dependencies through this one
    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          AuthSecurityService authSecurityService,
                          RefreshTokenRepository refreshTokenRepository,
                          UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authSecurityService = authSecurityService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
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

        // learned you can call .header() more than once on a responseentity builder to stack
        // multiple response headers before finally calling .body() to close it out
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
        // learned securitycontextholder is thread local storage spring security fills in per request,
        // the filter runs earlier in the chain and sets this authentication before this method ever runs
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

        // httpOnly means javascript in the browser cannot read this cookie at all, blocks a whole
        // class of xss attacks, secure means it only ever gets sent over an actual https connection
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

    // required = false on @CookieValue means this stays null instead of spring throwing a
    // 400 automatically when the cookie is missing, lets me handle the missing case myself below
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

    // messagedigest.getinstance can throw a checked exception if the algorithm name is unknown
    // to the jvm, sha-256 always exists so wrapping it in a runtimeexception here just avoids
    // forcing every caller to declare a throws clause for something that in practice never happens
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
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}
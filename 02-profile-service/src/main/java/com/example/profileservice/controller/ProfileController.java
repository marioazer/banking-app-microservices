package com.example.profileservice.controller;

import com.example.profileservice.dto.UpdateContactInfoRequestDto;
import com.example.profileservice.model.KycStatus;
import com.example.profileservice.model.UserProfile;
import com.example.profileservice.repository.UserProfileRepository;
import com.example.profileservice.service.ProfileManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    private final ProfileManagementService profileManagementService;
    private final UserProfileRepository userProfileRepository;

    // Portfolio-demo affordance: lets the logged-in user flip their own KYC to APPROVED without an
    // admin role (none can be granted today, see README known limitations) or manual DB access.
    // Explicitly off in prod (application-prod.yml) — see AuthController for the same pattern.
    @Value("${app.demo.enabled:false}")
    private boolean demoModeEnabled;

    public ProfileController(ProfileManagementService profileManagementService,
                             UserProfileRepository userProfileRepository) {
        this.profileManagementService = profileManagementService;
        this.userProfileRepository = userProfileRepository;
    }

    @PutMapping("/profiles/me/contact-info")
    public ResponseEntity<?> updateMyContactInfo(@Valid @RequestBody UpdateContactInfoRequestDto dto) {
        // Securely extract the userId from the JWT session, preventing IDOR attacks
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = extractUserIdFromAuth(authentication);

        profileManagementService.updateContactInfo(currentUserId, dto);
        
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    // @PathVariable pulls the {userId} segment straight out of the url and hands it to me
    // already converted to a long, spring matches it up by parameter name automatically
    @GetMapping("/profiles/{userId}/kyc-status")
    public ResponseEntity<?> getKycStatus(@PathVariable Long userId) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found"));
                
        return ResponseEntity.ok(Map.of("status", user.getKycStatus().name()));
    }

    @PostMapping("/webhooks/kyc-update")
    public ResponseEntity<?> handleKycWebhook(@RequestBody Map<String, String> payload) {
        // We can safely process this because the KycWebhookFilter verified the HMAC signature
        Long userId = Long.valueOf(payload.get("userId"));
        KycStatus newStatus = KycStatus.valueOf(payload.get("status"));

        profileManagementService.processKycWebhook(userId, newStatus);
        
        // Always return 200 OK immediately so the external vendor knows we received it
        return ResponseEntity.ok().build(); 
    }

    // Simulates the same vendor-webhook callback handleKycWebhook() above receives, but triggered
    // by the user themselves for demo purposes and scoped to their own userId from the JWT only —
    // unlike the admin override below, there is no reason/audit trail since this isn't a real override.
    @PostMapping("/profiles/kyc/simulate-approval")
    @PreAuthorize("hasAuthority('SCOPE_FULL_AUTH')")
    public ResponseEntity<?> simulateKycApproval() {
        if (!demoModeEnabled) {
            return ResponseEntity.notFound().build();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = extractUserIdFromAuth(authentication);

        profileManagementService.processKycWebhook(userId, KycStatus.APPROVED);

        return ResponseEntity.ok(Map.of("status", KycStatus.APPROVED.name()));
    }

    // @PreAuthorize runs before the method body even starts, checking the spring expression
    // language string against the logged in user's roles, request never even reaches this
    // code if neither role matches
    @PatchMapping("/admin/profiles/{userId}/kyc")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER')")
    public ResponseEntity<?> adminOverrideKyc(@PathVariable Long userId,
                                              @RequestBody Map<String, String> payload) {
                                              
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long adminId = extractUserIdFromAuth(authentication);
        
        KycStatus newStatus = KycStatus.valueOf(payload.get("status"));
        String reason = payload.get("reason");

        if (reason == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Override reason is mandatory"));
        }
        if (reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Override reason is mandatory"));
        }

        profileManagementService.adminOverrideKyc(userId, adminId, newStatus, reason);
        
        return ResponseEntity.ok(Map.of("message", "KYC status manually overridden by compliance officer"));
    }

    // =========================================================================
    // Internal Utilities
    // =========================================================================
    
    private Long extractUserIdFromAuth(Authentication authentication) {
        // The JWT subject holds the username, not the id — auth-service puts the numeric
        // userId in its own claim instead, since this service has no User table to resolve it from.
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaim("userId");
    }
}
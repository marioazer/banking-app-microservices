package com.example.profileservice.controller;

import com.example.profileservice.dto.UpdateContactInfoRequestDto;
import com.example.profileservice.model.KycStatus;
import com.example.profileservice.model.UserProfile;
import com.example.profileservice.repository.UserProfileRepository;
import com.example.profileservice.service.ProfileManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    private final ProfileManagementService profileManagementService;
    private final UserProfileRepository userProfileRepository;

    public ProfileController(ProfileManagementService profileManagementService,
                             UserProfileRepository userProfileRepository) {
        this.profileManagementService = profileManagementService;
        this.userProfileRepository = userProfileRepository;
    }

    // =========================================================================
    // FR4.1: Secure Profile Update API (Customer Facing)
    // =========================================================================
    
    @PutMapping("/profiles/me/contact-info")
    public ResponseEntity<?> updateMyContactInfo(@Valid @RequestBody UpdateContactInfoRequestDto dto) {
        // Securely extract the userId from the JWT session, preventing IDOR attacks
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = extractUserIdFromAuth(authentication);

        profileManagementService.updateContactInfo(currentUserId, dto);
        
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    // =========================================================================
    // FR3.1: KYC Status Query (Internal Microservice Facing)
    // =========================================================================
    
    @GetMapping("/profiles/{userId}/kyc-status")
    public ResponseEntity<?> getKycStatus(@PathVariable Long userId) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found"));
                
        return ResponseEntity.ok(Map.of("status", user.getKycStatus().name()));
    }

    // =========================================================================
    // FR3.2: Async Webhook for KYC Approval (External Vendor Facing)
    // =========================================================================
    
    @PostMapping("/webhooks/kyc-update")
    public ResponseEntity<?> handleKycWebhook(@RequestBody Map<String, String> payload) {
        // We can safely process this because the KycWebhookFilter verified the HMAC signature
        Long userId = Long.valueOf(payload.get("userId"));
        KycStatus newStatus = KycStatus.valueOf(payload.get("status"));

        profileManagementService.processKycWebhook(userId, newStatus);
        
        // Always return 200 OK immediately so the external vendor knows we received it
        return ResponseEntity.ok().build(); 
    }

    // =========================================================================
    // FR3.4: Manual Admin Override (Internal Employee Facing)
    // =========================================================================
    
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
        // Assuming the JWT subject holds the User ID as a String
        return Long.valueOf(authentication.getName()); 
    }
}
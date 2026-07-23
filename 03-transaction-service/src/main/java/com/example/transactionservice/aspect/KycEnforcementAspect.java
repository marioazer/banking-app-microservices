package com.example.transactionservice.aspect;

import com.example.transactionservice.client.ProfileServiceClient;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
public class KycEnforcementAspect {

    private final ProfileServiceClient profileServiceClient;

    public KycEnforcementAspect(ProfileServiceClient profileServiceClient) {
        this.profileServiceClient = profileServiceClient;
    }

    /**
     * This advice runs before any method annotated with @RequiresKyc.
     * It completely halts execution if the compliance check fails.
     */
    @Before("@annotation(com.example.transactionservice.annotation.RequiresKyc)")
    public void enforceKycStatus() {
        // 1. Securely extract the user ID from the active JWT session
        Long userId = resolveAuthenticatedUserId();

        // 2. Make the synchronous network call to the Profile Service
        String kycStatus = fetchKycStatus(userId);

        // 3. Enforce the strict compliance boundary (FR3.1 AC3)
        if (!"APPROVED".equals(kycStatus)) {
            // Throwing this custom exception instantly aborts the intercepted transaction method.
            // A @RestControllerAdvice class will catch this and translate it into a 403 Forbidden HTTP response.
            throw new KycRequiredException("Action forbidden: KYC verification is " + kycStatus + ". Approved status required to move funds.");
        }
    }

    private Long resolveAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new SecurityException("User is not authenticated.");
        }
        if (!authentication.isAuthenticated()) {
            throw new SecurityException("User is not authenticated.");
        }
        return Long.valueOf(authentication.getName());
    }

    private String fetchKycStatus(Long userId) {
        Map<String, String> response = profileServiceClient.getKycStatus(userId);

        if (response == null) {
            throw new RuntimeException("Failed to retrieve KYC status from Profile Service");
        }
        if (!response.containsKey("status")) {
            throw new RuntimeException("Failed to retrieve KYC status from Profile Service");
        }

        return response.get("status");
    }
    
    // =========================================================================
    // Inner Exception Class for clarity (usually kept in a separate file)
    // =========================================================================
    public static class KycRequiredException extends RuntimeException {
        public KycRequiredException(String message) {
            super(message);
        }
    }
}
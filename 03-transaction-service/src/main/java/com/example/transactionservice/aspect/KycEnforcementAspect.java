package com.example.transactionservice.aspect;

import com.example.transactionservice.client.ProfileServiceClient;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Map;

// @Aspect plus @Component is what turns this class into actual aop advice instead of just a
// plain bean, spring wraps any method carrying @RequiresKyc in a proxy that calls this class first
@Aspect
@Component
public class KycEnforcementAspect {

    private final ProfileServiceClient profileServiceClient;

    public KycEnforcementAspect(ProfileServiceClient profileServiceClient) {
        this.profileServiceClient = profileServiceClient;
    }

    // @Before with this pointcut expression means run before any method anywhere in the app
    // carrying @RequiresKyc, learned this is why executeTransfer and initiateWire both got this
    // check for free just by adding the annotation, no code duplicated between the two services
    @Before("@annotation(com.example.transactionservice.annotation.RequiresKyc)")
    public void enforceKycStatus() {
        // 1. Securely extract the user ID from the active JWT session
        Long userId = resolveAuthenticatedUserId();

        // 2. Make the synchronous network call to the Profile Service
        String kycStatus = fetchKycStatus(userId);

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
        // The JWT subject holds the username, not the id — auth-service puts the numeric
        // userId in its own claim instead, since this service has no User table to resolve it from.
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaim("userId");
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
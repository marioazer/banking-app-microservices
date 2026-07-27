package com.example.transactionservice.exception;

import com.example.transactionservice.aspect.KycEnforcementAspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * KycEnforcementAspect's Javadoc has always claimed "a @RestControllerAdvice class will catch
 * this and translate it into a 403 Forbidden" - until now, no such class existed anywhere in the
 * repo, so a KYC-blocked transfer surfaced as an unhandled 500 instead of the intended 403.
 * ResponseStatusException cases elsewhere (insufficient funds, ownership, not-found) already map
 * correctly via Spring's default resolver and don't need a handler here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KycEnforcementAspect.KycRequiredException.class)
    public ResponseEntity<Map<String, String>> handleKycRequired(KycEnforcementAspect.KycRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}

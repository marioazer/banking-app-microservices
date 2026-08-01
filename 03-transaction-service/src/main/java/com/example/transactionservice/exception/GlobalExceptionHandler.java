package com.example.transactionservice.exception;

import com.example.transactionservice.aspect.KycEnforcementAspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KycEnforcementAspect.KycRequiredException.class)
    public ResponseEntity<Map<String, String>> handleKycRequired(KycEnforcementAspect.KycRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}

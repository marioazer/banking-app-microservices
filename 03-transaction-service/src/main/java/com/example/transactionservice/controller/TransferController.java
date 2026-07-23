package com.example.transactionservice.controller;

import com.example.transactionservice.dto.ExternalWireRequestDto;
import com.example.transactionservice.dto.TransferResponseDto;
import com.example.transactionservice.service.ExternalWireService;
import com.example.transactionservice.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/transfers")
@PreAuthorize("hasAuthority('SCOPE_FULL_AUTH')")
public class TransferController {

    private final TransferService transferService;
    private final ExternalWireService externalWireService;

    public TransferController(TransferService transferService, ExternalWireService externalWireService) {
        this.transferService = transferService;
        this.externalWireService = externalWireService;
    }

    /**
     * DTO specifically for internal account-to-account transfers.
     */
    public record InternalTransferRequestDto(
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @NotNull @Positive BigDecimal amount
    ) {}

    /**
     * FR7: Internal Funds Transfer API.
     * Executes an atomic transfer and returns a unique confirmation ID in under 500ms[cite: 1].
     */
    @PostMapping("/internal")
    public ResponseEntity<TransferResponseDto> executeInternalTransfer(
            @RequestBody @Valid InternalTransferRequestDto request) {
        
        Long userId = extractUserIdFromAuth();
        
        TransferResponseDto response = transferService.executeTransfer(
                userId, 
                request.fromAccountId(), 
                request.toAccountId(), 
                request.amount()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * FR8.1 AC1: Endpoint POST /api/v1/transfers/external must accept recipient details.
     * The @Valid annotation enforces strict regex validation on IBAN and SWIFT codes.
     */
    @PostMapping("/external")
    public ResponseEntity<TransferResponseDto> executeExternalWire(
            @RequestParam Long fromAccountId,
            @RequestBody @Valid ExternalWireRequestDto request) {
        
        Long userId = extractUserIdFromAuth();
        
        TransferResponseDto response = externalWireService.initiateWire(
                userId, 
                fromAccountId, 
                request
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Securely extracts the user ID directly from the authenticated JWT session.
     * Prevents IDOR (Insecure Direct Object Reference) attacks.
     */
    private Long extractUserIdFromAuth() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new SecurityException("User is not authenticated");
        }
        if (!authentication.isAuthenticated()) {
            throw new SecurityException("User is not authenticated");
        }
        return Long.valueOf(authentication.getName());
    }
}
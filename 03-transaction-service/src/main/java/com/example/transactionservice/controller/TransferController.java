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
import org.springframework.security.oauth2.jwt.Jwt;
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

    // learned a record can be declared right inside a controller class like this, keeps a tiny
    // dto that only this one controller cares about from needing its own separate file
    public record InternalTransferRequestDto(
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @NotNull @Positive BigDecimal amount
    ) {}

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

    // mixing @RequestParam and @RequestBody on the same endpoint, learned spring is fine reading
    // fromAccountId off the query string while the rest of the payload comes from the json body
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

    private Long extractUserIdFromAuth() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new SecurityException("User is not authenticated");
        }
        if (!authentication.isAuthenticated()) {
            throw new SecurityException("User is not authenticated");
        }
        // The JWT subject holds the username, not the id — auth-service puts the numeric
        // userId in its own claim instead, since this service has no User table to resolve it from.
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaim("userId");
    }
}
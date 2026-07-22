package com.example.transactionservice.controller;

import com.example.transactionservice.model.TransactionEntity;
import com.example.transactionservice.model.TransactionStatus;
import com.example.transactionservice.model.AccountEntity;
import com.example.transactionservice.repository.TransactionRepository;
import com.example.transactionservice.repository.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Internal API Controller for machine-to-machine communication.
 * Fulfills FR8.3: Manual Fraud Review Webhook[cite: 2].
 */
@RestController
@RequestMapping("/api/v1/internal/transfers")
public class InternalFraudController {

    private final FraudResolutionService fraudResolutionService;

    public InternalFraudController(FraudResolutionService fraudResolutionService) {
        this.fraudResolutionService = fraudResolutionService;
    }

    /**
     * Data Transfer Object for the Fraud Service webhook payload.
     * Fulfills FR8.3 AC2: Accepts status (APPROVED/REJECTED) and reviewer_notes[cite: 2].
     */
    public record FraudReviewUpdateDto(
            @NotBlank(message = "Status is required")
            @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "Status must be APPROVED or REJECTED")
            String status,
            
            String reviewerNotes
    ) {}

    /**
     * Endpoint utilized by the Fraud Detection Service to finalize pending wires.
     * Fulfills FR8.3 AC1: Exposes PATCH /api/v1/internal/transfers/{transactionId}/fraud-status[cite: 2].
     */
    @PatchMapping("/{transactionId}/fraud-status")
    public ResponseEntity<String> updateFraudStatus(
            @PathVariable UUID transactionId,
            @RequestBody @Valid FraudReviewUpdateDto payload) {
        
        fraudResolutionService.resolvePendingTransfer(transactionId, payload);
        
        return ResponseEntity.ok("Transaction " + transactionId + " successfully updated to " + payload.status());
    }
}

/**
 * Service handling the finalization or atomic reversal of pending wires.
 */
@Service
class FraudResolutionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public FraudResolutionService(TransactionRepository transactionRepository, 
                                  AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Fulfills FR8.3 AC3: Finalizes the wire if APPROVED, or cancels and releases funds if REJECTED[cite: 2].
     */
    @Transactional
    public void resolvePendingTransfer(UUID transactionId, InternalFraudController.FraudReviewUpdateDto payload) {
        
        TransactionEntity transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() != TransactionStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not in a PENDING_APPROVAL state");
        }

        if ("APPROVED".equals(payload.status())) {
            // Funds were already deducted during initiation, so we simply finalize the status[cite: 2].
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setDescription(transaction.getDescription() + " [Fraud Review: APPROVED. Notes: " + payload.reviewerNotes() + "]");
            transactionRepository.save(transaction);
            
        } else if ("REJECTED".equals(payload.status())) {
            // Atomic Reversal Logic: Return the reserved funds to the user[cite: 2].
            transaction.setStatus(TransactionStatus.REJECTED);
            transaction.setDescription(transaction.getDescription() + " [Fraud Review: REJECTED. Notes: " + payload.reviewerNotes() + "]");
            
            AccountEntity account = accountRepository.findByIdForUpdate(transaction.getAccountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Source account missing during reversal"));
            
            // Add the exact amount back to the user's available balance[cite: 2].
            account.setAvailableBalance(account.getAvailableBalance().add(transaction.getAmount()));
            
            accountRepository.save(account);
            transactionRepository.save(transaction);
        }
    }
}
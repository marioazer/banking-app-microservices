package com.example.transactionservice.controller;

import com.example.transactionservice.client.AccountServiceClient;
import com.example.transactionservice.model.TransactionEntity;
import com.example.transactionservice.model.TransactionStatus;
import com.example.transactionservice.repository.TransactionRepository;
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

// no @PreAuthorize on this one unlike the customer facing controllers, learned this is meant
// to only ever be reachable from inside the cluster network, not directly from an end user
@RestController
@RequestMapping("/api/v1/internal/transfers")
public class InternalFraudController {

    private final FraudResolutionService fraudResolutionService;

    public InternalFraudController(FraudResolutionService fraudResolutionService) {
        this.fraudResolutionService = fraudResolutionService;
    }

    public record FraudReviewUpdateDto(
            @NotBlank(message = "Status is required")
            @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "Status must be APPROVED or REJECTED")
            String status,
            
            String reviewerNotes
    ) {}

    @PatchMapping("/{transactionId}/fraud-status")
    public ResponseEntity<String> updateFraudStatus(
            @PathVariable UUID transactionId,
            @RequestBody @Valid FraudReviewUpdateDto payload) {
        
        fraudResolutionService.resolvePendingTransfer(transactionId, payload);
        
        return ResponseEntity.ok("Transaction " + transactionId + " successfully updated to " + payload.status());
    }
}

// learned a top level class does not have to be public, and a file can hold more than one
// top level class as long as only one of them (the one matching the filename) is public,
// this service is only ever used by the controller right above it so package private is enough
@Service
class FraudResolutionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    public FraudResolutionService(TransactionRepository transactionRepository,
                                  AccountServiceClient accountServiceClient) {
        this.transactionRepository = transactionRepository;
        this.accountServiceClient = accountServiceClient;
    }

    @Transactional
    public void resolvePendingTransfer(UUID transactionId, InternalFraudController.FraudReviewUpdateDto payload) {

        TransactionEntity transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() != TransactionStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not in a PENDING_APPROVAL state");
        }

        if ("APPROVED".equals(payload.status())) {
            finalizeTransaction(transaction, payload.reviewerNotes());
        } else if ("REJECTED".equals(payload.status())) {
            reverseTransaction(transaction, payload.reviewerNotes());
        }
    }

    private void finalizeTransaction(TransactionEntity transaction, String reviewerNotes) {
        // Funds were already deducted during initiation, so we simply finalize the status.
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(transaction.getDescription() + " [Fraud Review: APPROVED. Notes: " + reviewerNotes + "]");
        transactionRepository.save(transaction);
    }

    private void reverseTransaction(TransactionEntity transaction, String reviewerNotes) {
        // Atomic Reversal Logic: Return the reserved funds to the user.
        transaction.setStatus(TransactionStatus.REJECTED);
        transaction.setDescription(transaction.getDescription() + " [Fraud Review: REJECTED. Notes: " + reviewerNotes + "]");

        // account-service locks the row and adds the amount back atomically, same as the
        // original debit did - it's the sole owner of the accounts table now.
        accountServiceClient.credit(transaction.getAccountId(), new AccountServiceClient.CreditRequest(
                transaction.getAmount(), "Wire reversed - fraud review rejected"));

        transactionRepository.save(transaction);
    }
}
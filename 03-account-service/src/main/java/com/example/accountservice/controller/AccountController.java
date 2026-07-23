package com.example.accountservice.controller;

import com.example.accountservice.dto.AccountOverviewResponseDto;
import com.example.accountservice.model.TransactionEntity;
import com.example.accountservice.model.TransactionType;
import com.example.accountservice.service.AccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
// Enforce read-only authorization so PRE_AUTH tokens cannot access financial data (FR5.4 AC1 & AC2)
@PreAuthorize("hasAuthority('SCOPE_FULL_AUTH')")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * FR5.3: Consolidated Dashboard API.
     * Returns a list of all open accounts and their current balances as soon as the user logs in.
     */
    @GetMapping
    public ResponseEntity<List<AccountOverviewResponseDto>> getAccountsOverview() {
        Long userId = extractUserIdFromAuth();
        
        List<AccountOverviewResponseDto> accounts = accountService.getDashboardAccounts(userId);
        
        return ResponseEntity.ok(accounts);
    }

    /**
     * FR6.4: Secure Transaction History API.
     * Exposes a protected endpoint to fetch paginated transaction data[cite: 4].
     * Accepts optional query parameters: ?page=0&size=50&type=DEBIT[cite: 4].
     */
    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<Page<TransactionEntity>> getTransactionHistory(
            @PathVariable Long accountId,
            @RequestParam(required = false) TransactionType type,
            // Default pagination settings if the frontend does not provide them (50 per page, newest first)[cite: 4]
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Long userId = extractUserIdFromAuth();
        
        Page<TransactionEntity> transactions = accountService.getAccountTransactions(userId, accountId, type, pageable);
        
        // Returns the standard Spring Page JSON containing content and metadata (totalPages, totalElements)[cite: 4]
        return ResponseEntity.ok(transactions);
    }

    /**
     * Securely extracts the user ID directly from the authenticated JWT session.
     * Ensures the user can only fetch their own accounts (FR5.3 AC2).
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
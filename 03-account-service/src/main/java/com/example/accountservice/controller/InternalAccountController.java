package com.example.accountservice.controller;

import com.example.accountservice.model.AccountEntity;
import com.example.accountservice.model.TransactionEntity;
import com.example.accountservice.model.TransactionType;
import com.example.accountservice.repository.AccountRepository;
import com.example.accountservice.repository.TransactionRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class InternalAccountController {

    private final InternalAccountService internalAccountService;

    public InternalAccountController(InternalAccountService internalAccountService) {
        this.internalAccountService = internalAccountService;
    }

    public record TransferRequest(
            @NotNull Long userId,
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @NotNull @Positive BigDecimal amount
    ) {}

    public record DebitRequest(
            @NotNull Long userId,
            @NotNull @Positive BigDecimal amount,
            @NotNull String description
    ) {}

    public record CreditRequest(
            @NotNull @Positive BigDecimal amount,
            @NotNull String description
    ) {}

    public record UserAggregateBalanceResponse(Long userId, BigDecimal totalBalance) {}

    @PostMapping("/api/v1/internal/accounts/transfer")
    public ResponseEntity<Void> transfer(@RequestBody TransferRequest request) {
        internalAccountService.transfer(request.userId(), request.fromAccountId(), request.toAccountId(), request.amount());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/internal/accounts/{accountId}/debit")
    public ResponseEntity<Void> debit(@PathVariable Long accountId, @RequestBody DebitRequest request) {
        internalAccountService.debit(request.userId(), accountId, request.amount(), request.description());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/internal/accounts/{accountId}/credit")
    public ResponseEntity<Void> credit(@PathVariable Long accountId, @RequestBody CreditRequest request) {
        internalAccountService.credit(accountId, request.amount(), request.description());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/accounts/balances/batch")
    public ResponseEntity<List<UserAggregateBalanceResponse>> balancesBatch(@RequestBody List<Long> userIds) {
        return ResponseEntity.ok(internalAccountService.aggregateBalances(userIds));
    }
}

@Service
class InternalAccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    InternalAccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void transfer(Long userId, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        AccountEntity fromAccount = lockAndVerifyOwnership(fromAccountId, userId);
        AccountEntity toAccount = lockAndVerifyOwnership(toAccountId, userId);

        if (fromAccount.getAvailableBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");
        }

        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(amount));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(amount));
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        recordTransaction(fromAccountId, TransactionType.DEBIT, amount, "Internal transfer to account " + toAccountId);
        recordTransaction(toAccountId, TransactionType.CREDIT, amount, "Internal transfer from account " + fromAccountId);
    }

    @Transactional
    public void debit(Long userId, Long accountId, BigDecimal amount, String description) {
        AccountEntity account = lockAndVerifyOwnership(accountId, userId);

        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");
        }

        account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
        accountRepository.save(account);
        recordTransaction(accountId, TransactionType.DEBIT, amount, description);
    }

    @Transactional
    public void credit(Long accountId, BigDecimal amount, String description) {
        AccountEntity account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        account.setAvailableBalance(account.getAvailableBalance().add(amount));
        accountRepository.save(account);
        recordTransaction(accountId, TransactionType.CREDIT, amount, description);
    }

    @Transactional(readOnly = true)
    public List<InternalAccountController.UserAggregateBalanceResponse> aggregateBalances(List<Long> userIds) {
        return accountRepository.sumAvailableBalanceByUserIds(userIds).stream()
                .map(row -> new InternalAccountController.UserAggregateBalanceResponse((Long) row[0], (BigDecimal) row[1]))
                .toList();
    }

    private AccountEntity lockAndVerifyOwnership(Long accountId, Long userId) {
        AccountEntity account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Both accounts must belong to the authenticated user");
        }
        return account;
    }

    private void recordTransaction(Long accountId, TransactionType type, BigDecimal amount, String description) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setAccountId(accountId);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transactionRepository.save(transaction);
    }
}

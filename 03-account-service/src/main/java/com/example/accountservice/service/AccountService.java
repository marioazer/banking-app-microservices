package com.example.accountservice.service;

import com.example.accountservice.dto.AccountOverviewResponseDto;
import com.example.accountservice.mapper.AccountMapper;
import com.example.accountservice.model.AccountEntity;
import com.example.accountservice.model.AccountStatus;
import com.example.accountservice.model.TransactionEntity;
import com.example.accountservice.model.TransactionType;
import com.example.accountservice.repository.AccountRepository;
import com.example.accountservice.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

// @Transactional(readOnly = true) at the class level applies to every method here by default,
// learned this lets hibernate skip some dirty checking work since it knows nothing gets written
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountMapper accountMapper;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountMapper = accountMapper;
    }

    public List<AccountOverviewResponseDto> getDashboardAccounts(Long userId) {
        // Query the database for accounts, strictly excluding CLOSED ones
        List<AccountEntity> accounts = accountRepository.findByUserIdAndStatusNot(userId, AccountStatus.CLOSED);
        
        // Map the raw entities to secure DTOs, masking the sensitive account numbers
        return accounts.stream()
                .map(accountMapper::toOverviewDto)
                .collect(Collectors.toList());
    }

    public Page<TransactionEntity> getAccountTransactions(Long userId, Long accountId, TransactionType filterType, Pageable pageable) {

        verifyAccountOwnership(userId, accountId);

        if (filterType != null) {
            // If the user specified CREDIT or DEBIT, use the highly targeted repository method
            return transactionRepository.findByAccountIdAndTransactionType(accountId, filterType, pageable);
        } else {
            // If no filter is specified, return all transactions for the account
            return transactionRepository.findByAccountId(accountId, pageable);
        }
    }

    private void verifyAccountOwnership(Long userId, Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found or invalid ID provided."));

        if (!account.getUserId().equals(userId)) {
            // Throwing this exception ensures Spring Security intercepts it and returns a 403 Forbidden
            // learned this specific exception type matters, spring security has an exception handler
            // already registered for accessdeniedexception, a plain runtimeexception would have
            // just bubbled up as an unhandled 500 instead of a proper 403
            throw new AccessDeniedException("Action forbidden: You do not have permission to view this account's history.");
        }
    }
}
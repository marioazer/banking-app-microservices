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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * Retrieves a consolidated list of active and frozen accounts for the user dashboard.
     * Fulfills FR5.3 AC4: Automatically filter out any accounts where the status is CLOSED.
     */
    public List<AccountOverviewResponseDto> getDashboardAccounts(Long userId) {
        // Query the database for accounts, strictly excluding CLOSED ones
        List<AccountEntity> accounts = accountRepository.findByUserIdAndStatusNot(userId, AccountStatus.CLOSED);
        
        // Map the raw entities to secure DTOs, masking the sensitive account numbers
        return accounts.stream()
                .map(accountMapper::toOverviewDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves paginated transaction history, enforcing strict ownership checks and dynamic filtering.
     * Fulfills FR6.3 (Dynamic Filtering) and FR6.4 (Ownership Authorization).[cite: 4]
     */
    public Page<TransactionEntity> getAccountTransactions(Long userId, Long accountId, TransactionType filterType, Pageable pageable) {
        
        // 1. Strict Ownership Authorization Check (FR6.4 AC3)[cite: 4]
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found or invalid ID provided."));

        if (!account.getUserId().equals(userId)) {
            // Throwing this exception ensures Spring Security intercepts it and returns a 403 Forbidden[cite: 4]
            throw new AccessDeniedException("Action forbidden: You do not have permission to view this account's history.");
        }

        // 2. Dynamic Repository Routing (FR6.3 AC2)[cite: 4]
        if (filterType != null) {
            // If the user specified CREDIT or DEBIT, use the highly targeted repository method[cite: 4]
            return transactionRepository.findByAccountIdAndTransactionType(accountId, filterType, pageable);
        } else {
            // If no filter is specified, return all transactions for the account[cite: 4]
            return transactionRepository.findByAccountId(accountId, pageable);
        }
    }
}
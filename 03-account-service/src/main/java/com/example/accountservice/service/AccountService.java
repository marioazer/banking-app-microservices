package com.example.accountservice.service;

import com.example.accountservice.dto.AccountOverviewResponseDto;
import com.example.accountservice.mapper.AccountMapper;
import com.example.accountservice.model.AccountEntity;
import com.example.accountservice.model.AccountStatus;
import com.example.accountservice.model.AccountType;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final SecureRandom random = new SecureRandom();

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

    // Self-service "Add Funds" for the portfolio demo — a real product would fund this through a
    // linked debit card/ACH pull; here it's a capped, ownership-checked credit the user triggers
    // themselves, distinct from the unauthenticated internal /credit endpoint account-to-account
    // transfers and Kafka provisioning use.
    private static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("10000");

    @Transactional
    public AccountOverviewResponseDto depositFunds(Long userId, Long accountId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit amount must be positive");
        }
        if (amount.compareTo(MAX_DEPOSIT_AMOUNT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit amount cannot exceed " + MAX_DEPOSIT_AMOUNT);
        }

        AccountEntity account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUserId().equals(userId)) {
            throw new AccessDeniedException("Action forbidden: You do not have permission to deposit into this account.");
        }

        account.setAvailableBalance(account.getAvailableBalance().add(amount));
        accountRepository.save(account);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setAccountId(accountId);
        transaction.setTransactionType(TransactionType.CREDIT);
        transaction.setAmount(amount);
        transaction.setDescription("Deposit (self-service demo)");
        transactionRepository.save(transaction);

        return accountMapper.toOverviewDto(account);
    }

    // Self-service "Open Account" — an ordinary banking feature (not a demo-only shortcut), so it
    // isn't gated behind app.demo.enabled. The cap just keeps one user from spamming accounts.
    private static final String DEFAULT_ROUTING_NUMBER = "021000021";
    private static final int MAX_ACCOUNTS_PER_USER = 5;

    @Transactional
    public AccountOverviewResponseDto openAccount(Long userId, AccountType accountType) {
        long existingCount = accountRepository.findByUserIdAndStatusNot(userId, AccountStatus.CLOSED).size();
        if (existingCount >= MAX_ACCOUNTS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account limit reached");
        }

        AccountEntity account = new AccountEntity();
        account.setUserId(userId);
        account.setAccountType(accountType);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setRoutingNumber(DEFAULT_ROUTING_NUMBER);
        account.setAccountNumber(generateAccountNumber());
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);

        return accountMapper.toOverviewDto(account);
    }

    private String generateAccountNumber() {
        long number = 100_000_000_000L + (long) (random.nextDouble() * 900_000_000_000L);
        return String.valueOf(number);
    }

    // Demo-only: fabricates a realistic-looking transaction history (paycheck deposits, everyday
    // purchases, spread over the past ~45 days) so a freshly-deposited-into account doesn't just
    // show one flat "Deposit" line. Gated behind app.demo.enabled at the controller.
    private static final String[] DEMO_CREDIT_DESCRIPTIONS = {
            "Payroll Deposit", "Freelance Payment", "Refund - Online Order", "Interest Payment"
    };
    private static final String[] DEMO_DEBIT_DESCRIPTIONS = {
            "Grocery Store", "Coffee Shop", "Electric Bill", "Online Purchase - Amazon",
            "Gas Station", "Restaurant", "Streaming Subscription", "Pharmacy"
    };

    @Transactional
    public AccountOverviewResponseDto seedDemoTransactions(Long userId, Long accountId) {
        AccountEntity account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getUserId().equals(userId)) {
            throw new AccessDeniedException("Action forbidden: You do not have permission to modify this account.");
        }

        List<TransactionEntity> generated = new ArrayList<>();

        BigDecimal creditsSum = BigDecimal.ZERO;
        int creditCount = 2 + random.nextInt(2); // 2-3
        for (int i = 0; i < creditCount; i++) {
            BigDecimal amount = randomAmount(800, 2500);
            creditsSum = creditsSum.add(amount);
            generated.add(buildDemoTransaction(accountId, TransactionType.CREDIT, amount, randomChoice(DEMO_CREDIT_DESCRIPTIONS)));
        }

        // Never let seeded debits push the account below half of what it would have after the
        // seeded credits land, so this can never drain or negative-balance the account.
        BigDecimal maxDebitBudget = account.getAvailableBalance().add(creditsSum)
                .multiply(new BigDecimal("0.5"));
        BigDecimal debitsSum = BigDecimal.ZERO;
        int debitCount = 5 + random.nextInt(5); // 5-9
        for (int i = 0; i < debitCount; i++) {
            BigDecimal amount = randomAmount(5, 150);
            if (debitsSum.add(amount).compareTo(maxDebitBudget) > 0) {
                break;
            }
            debitsSum = debitsSum.add(amount);
            generated.add(buildDemoTransaction(accountId, TransactionType.DEBIT, amount, randomChoice(DEMO_DEBIT_DESCRIPTIONS)));
        }

        transactionRepository.saveAll(generated);

        account.setAvailableBalance(account.getAvailableBalance().add(creditsSum).subtract(debitsSum));
        accountRepository.save(account);

        return accountMapper.toOverviewDto(account);
    }

    private TransactionEntity buildDemoTransaction(Long accountId, TransactionType type, BigDecimal amount, String description) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setAccountId(accountId);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setCreatedAt(randomPastDateTime());
        return transaction;
    }

    private BigDecimal randomAmount(int min, int max) {
        double value = min + random.nextDouble() * (max - min);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String randomChoice(String[] options) {
        return options[random.nextInt(options.length)];
    }

    private LocalDateTime randomPastDateTime() {
        return LocalDateTime.now()
                .minusDays(1 + random.nextInt(45))
                .minusHours(random.nextInt(24))
                .minusMinutes(random.nextInt(60));
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
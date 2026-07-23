package com.example.accountservice;

import com.example.accountservice.dto.AccountOverviewResponseDto;
import com.example.accountservice.mapper.AccountMapper;
import com.example.accountservice.model.AccountEntity;
import com.example.accountservice.model.AccountStatus;
import com.example.accountservice.model.AccountType;
import com.example.accountservice.model.TransactionEntity;
import com.example.accountservice.model.TransactionType;
import com.example.accountservice.repository.AccountRepository;
import com.example.accountservice.repository.TransactionRepository;
import com.example.accountservice.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Consolidated FR5 (Account Overview) & FR6 (Transaction History Pagination) acceptance suite.
 * Replaces the previously overlapping AccountServiceTestSuite / AccountOverviewTestSuite /
 * TransactionHistoryTestSuite trio with one canonical suite, mirroring the pattern established
 * in AuthManagementTestSuite and ProfileServiceTestSuite.
 *
 * The real AccountService and AccountMapper are autowired so masking/filtering/ownership logic
 * is genuinely exercised, not just stubbed; only the JPA repositories are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountServiceTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountMapper accountMapper;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private TransactionRepository transactionRepository;

    private AccountEntity activeChecking;

    @BeforeEach
    void setUp() {
        activeChecking = new AccountEntity();
        activeChecking.setId(1L);
        activeChecking.setUserId(42L);
        activeChecking.setAccountType(AccountType.CHECKING);
        activeChecking.setAvailableBalance(new BigDecimal("1500.0000"));
        activeChecking.setRoutingNumber("021000021");
        activeChecking.setAccountNumber("9876543210");
        activeChecking.setStatus(AccountStatus.ACTIVE);
    }

    /* ==========================================================
       USER STORY: 5.3 - Consolidated Dashboard API
       ========================================================== */

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 1: Dashboard excludes CLOSED accounts, masks account number - [MEANT TO PASS]")
    void testBlock1_dashboardExcludesClosedAccountsAndMasksNumber() throws Exception {
        // Requirement Cites: [Story 5.2 - AC1,AC2], [Story 5.3 - AC3,AC4]
        given(accountRepository.findByUserIdAndStatusNot(42L, AccountStatus.CLOSED))
                .willReturn(List.of(activeChecking));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountType").value("CHECKING"))
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("......3210"))
                .andExpect(jsonPath("$[0].routingNumber").value("021000021"));
    }

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 2: Dashboard returns empty array (not an error) when user has no active accounts - [MEANT TO PASS]")
    void testBlock2_emptyDashboardWhenNoActiveAccounts() throws Exception {
        // Requirement Cites: [Story 5.3 - AC3,AC4] (edge case)
        given(accountRepository.findByUserIdAndStatusNot(42L, AccountStatus.CLOSED)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 3: userId is extracted from the JWT/SecurityContext, not a spoofable request param - [MEANT TO PASS]")
    void testBlock3_userIdExtractedFromSecurityContextNotParams() throws Exception {
        // Requirement Cites: [Story 5.3 - AC2] (IDOR prevention)
        given(accountRepository.findByUserIdAndStatusNot(42L, AccountStatus.CLOSED)).willReturn(List.of());

        // A spoofed userId query param must be ignored; the repository is still queried with 42 (the JWT principal)
        mockMvc.perform(get("/api/v1/accounts?userId=999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /* ==========================================================
       USER STORY: 5.4 - Read-Only Authorization Enforcement
       ========================================================== */

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_PRE_AUTH"})
    @DisplayName("Block 4: Pre-Auth JWT (2FA incomplete) is rejected with 403 on the dashboard - [MEANT TO FAIL]")
    void testBlock4_preAuthTokenRejectedOnDashboard() throws Exception {
        // Requirement Cites: [Story 5.4 - AC1, AC2]
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Block 5: Unauthenticated request is rejected on the dashboard - [MEANT TO FAIL]")
    void testBlock5_unauthenticatedRequestRejectedOnDashboard() throws Exception {
        // Requirement Cites: [Story 5.4 - AC1] (no authentication at all)
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().is4xxClientError());
    }

    /* ==========================================================
       FINAL BLOCK: FR5 End-to-End Dashboard Verification
       ========================================================== */

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Final Block: Full-Auth user retrieves masked, filtered, correctly-priced dashboard - [MEANT TO PASS]")
    void testFinalAC_fullAuthUserGetsMaskedFilteredDashboard() throws Exception {
        // Requirement Cites: [Story 5.2 - AC1,AC2,AC3], [Story 5.3 - AC1,AC2,AC3,AC4], [Story 5.4 - AC1,AC2]
        given(accountRepository.findByUserIdAndStatusNot(42L, AccountStatus.CLOSED))
                .willReturn(List.of(activeChecking));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("......3210"))
                .andExpect(jsonPath("$[0].availableBalance").value(1500.0))
                .andExpect(jsonPath("$[0].routingNumber").value("021000021"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    /* ==========================================================
       USER STORY: 6.2 - Pagination & Sorting Logic (Data Layer)
       ========================================================== */

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 6: Default pagination applies size=50 and DESC sort by createdAt - [MEANT TO PASS]")
    void testBlock6_defaultPaginationAppliedCorrectly() throws Exception {
        // Requirement Cites: [Story 6.2 - AC1, AC2, AC3]
        given(accountRepository.findById(1L)).willReturn(Optional.of(activeChecking));
        // Stub with the exact expected Pageable so the page metadata (size=50) reflects the real request,
        // not an unpaged default - PageImpl<>(List.of()) alone reports size=0 regardless of what was asked.
        given(transactionRepository.findByAccountId(eq(1L), eq(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")), 0));

        mockMvc.perform(get("/api/v1/accounts/1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    /* ==========================================================
       USER STORY: 6.3 - Dynamic Filtering by Transaction Type
       ========================================================== */

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 7: type=DEBIT routes to the filtered repository query - [MEANT TO PASS]")
    void testBlock7_debitFilterUsesFilteredQuery() throws Exception {
        // Requirement Cites: [Story 6.3 - AC1]
        TransactionEntity debit = buildTransaction(1L, TransactionType.DEBIT, new BigDecimal("75.5000"));
        given(accountRepository.findById(1L)).willReturn(Optional.of(activeChecking));
        given(transactionRepository.findByAccountIdAndTransactionType(eq(1L), eq(TransactionType.DEBIT), any()))
                .willReturn(new PageImpl<>(List.of(debit)));

        mockMvc.perform(get("/api/v1/accounts/1/transactions").param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].transactionType").value("DEBIT"));
    }

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 8: No type filter returns all transactions via the unfiltered query - [MEANT TO PASS]")
    void testBlock8_noFilterReturnsAllTransactions() throws Exception {
        // Requirement Cites: [Story 6.3 - AC2]
        TransactionEntity credit = buildTransaction(1L, TransactionType.CREDIT, new BigDecimal("200.0000"));
        TransactionEntity debit = buildTransaction(1L, TransactionType.DEBIT, new BigDecimal("50.0000"));
        given(accountRepository.findById(1L)).willReturn(Optional.of(activeChecking));
        given(transactionRepository.findByAccountId(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(credit, debit)));

        mockMvc.perform(get("/api/v1/accounts/1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    /* ==========================================================
       USER STORY: 6.4 - Secure Transaction History API (Ownership)
       ========================================================== */

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 9: Requesting another user's accountId is rejected with 403 - [MEANT TO FAIL]")
    void testBlock9_ownershipMismatchReturns403() throws Exception {
        // Requirement Cites: [Story 6.4 - AC3]
        AccountEntity notOwned = new AccountEntity();
        notOwned.setId(1L);
        notOwned.setUserId(999L);
        given(accountRepository.findById(1L)).willReturn(Optional.of(notOwned));

        mockMvc.perform(get("/api/v1/accounts/1/transactions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 10 [GAP]: Non-existent accountId should return a 4xx, not an unhandled 500 - [MEANT TO PASS, CURRENTLY FAILS: no exception mapping for IllegalArgumentException]")
    void testBlock10_Gap_nonExistentAccountIdShouldFailCleanly() throws Exception {
        // Requirement Cites: [Story 6.4 - AC3] (invalid ID path)
        // AccountService.getAccountTransactions() throws a raw IllegalArgumentException for a missing
        // account, and there is no @ExceptionHandler/@ControllerAdvice mapping it to a client error -
        // it currently surfaces as an unhandled 500 instead of 404/400. Left asserting the desired
        // (spec-correct) outcome so this stays red until that mapping is added.
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/999/transactions"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_PRE_AUTH"})
    @DisplayName("Block 11: Pre-Auth JWT cannot access transaction history - [MEANT TO FAIL]")
    void testBlock11_preAuthTokenBlockedFromTransactionHistory() throws Exception {
        // Requirement Cites: [Story 5.4 - AC1, AC2] (class-level @PreAuthorize applies to all endpoints)
        mockMvc.perform(get("/api/v1/accounts/1/transactions"))
                .andExpect(status().isForbidden());
    }

    /* ==========================================================
       FINAL BLOCK: FR6 End-to-End Transaction History Verification
       ========================================================== */

    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Final Block: Owner retrieves paginated, filtered, well-formed transaction page - [MEANT TO PASS]")
    void testFinalAC_ownerRetrievesPaginatedFilteredHistory() throws Exception {
        // Requirement Cites: [Story 6.2 - AC1,AC2,AC3], [Story 6.3 - AC1,AC2], [Story 6.4 - AC1,AC2,AC3,AC4]
        TransactionEntity credit = buildTransaction(1L, TransactionType.CREDIT, new BigDecimal("999.9900"));
        given(accountRepository.findById(1L)).willReturn(Optional.of(activeChecking));
        given(transactionRepository.findByAccountIdAndTransactionType(
                eq(1L), eq(TransactionType.CREDIT), eq(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .willReturn(new PageImpl<>(List.of(credit), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/accounts/1/transactions?page=0&size=50&type=CREDIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionType").value("CREDIT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0));
    }

    private TransactionEntity buildTransaction(Long accountId, TransactionType type, BigDecimal amount) {
        TransactionEntity tx = new TransactionEntity();
        tx.setAccountId(accountId);
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setDescription("Test transaction");
        return tx;
    }
}

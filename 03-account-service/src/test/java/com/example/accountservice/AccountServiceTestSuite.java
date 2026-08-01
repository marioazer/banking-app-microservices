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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    // shared fixture built before every test, one active checking account belonging to user 42
    // giving it a real routing number and account number so the masking logic has something real to mask
    // status starts out active since most tests below care about the normal, non closed case
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

    // mocks the same Jwt-shaped principal the real oauth2 resource server filter builds in
    // production - the "scope" claim drives the SCOPE_FULL_AUTH authority via the default
    // JwtGrantedAuthoritiesConverter, and "userId" is what the controllers actually read
    private static RequestPostProcessor fullAuthUser(long userId) {
        return jwt().jwt(j -> j.claim("scope", "FULL_AUTH").claim("userId", userId));
    }

    // same idea but with a Pre-Auth scope, simulating a session where 2FA was never completed
    private static RequestPostProcessor preAuthUser(long userId) {
        return jwt().jwt(j -> j.claim("scope", "PRE_AUTH").claim("userId", userId));
    }

    // checking the dashboard endpoint filters out closed accounts and masks the raw account number
    // simulates user 42 logged in with a full auth session
    // stub the repository so this user's non closed accounts come back as just the one fixture account
    // hit get /api/v1/accounts
    // expect just one account in the response, checking type, masked number showing only last four digits,
    // and the routing number coming through in full since that one is not sensitive the same way
    @Test
    @DisplayName("Block 1: Dashboard excludes CLOSED accounts, masks account number - [MEANT TO PASS]")
    void testBlock1_dashboardExcludesClosedAccountsAndMasksNumber() throws Exception {
        given(accountRepository.findByUserIdAndStatusNot(42L, AccountStatus.CLOSED))
                .willReturn(List.of(activeChecking));

        mockMvc.perform(get("/api/v1/accounts").with(fullAuthUser(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountType").value("CHECKING"))
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("......3210"))
                .andExpect(jsonPath("$[0].routingNumber").value("021000021"));
    }

    // making sure a user with zero active accounts gets back an empty list, not some kind of error
    // stub the repository so this user's query returns an empty list
    // hit the dashboard endpoint
    // expect a normal 200 ok with a zero length array, an empty account list is a valid state, not a bug
    @Test
    @DisplayName("Block 2: Dashboard returns empty array (not an error) when user has no active accounts - [MEANT TO PASS]")
    void testBlock2_emptyDashboardWhenNoActiveAccounts() throws Exception {
        given(accountRepository.findByUserIdAndStatusNot(42L, AccountStatus.CLOSED)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts").with(fullAuthUser(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // security focused test, making sure the endpoint trusts the jwt and not a query param for user id
    // stub the repository so the real logged in user, 42, has no accounts
    // hit the dashboard but pass a spoofed userId=999 query param, trying to impersonate another user
    // that param should be completely ignored, the repo call still only ever used the real jwt user id
    // so the response should reflect user 42's data, which is empty here, not user 999's
    @Test
    @DisplayName("Block 3: userId is extracted from the JWT/SecurityContext, not a spoofable request param - [MEANT TO PASS]")
    void testBlock3_userIdExtractedFromSecurityContextNotParams() throws Exception {
        given(accountRepository.findByUserIdAndStatusNot(42L, AccountStatus.CLOSED)).willReturn(List.of());

        // A spoofed userId query param must be ignored; the repository is still queried with 42 (the JWT principal)
        mockMvc.perform(get("/api/v1/accounts?userId=999").with(fullAuthUser(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // making sure a pre auth token, meaning 2fa was never finished, cannot reach the dashboard
    // only grants scope_pre_auth instead of the full auth scope the other tests use
    // hit the dashboard endpoint with that partial authority
    // expect a 403 forbidden, since the class level security check requires full auth specifically
    @Test
    @DisplayName("Block 4: Pre-Auth JWT (2FA incomplete) is rejected with 403 on the dashboard - [MEANT TO FAIL]")
    void testBlock4_preAuthTokenRejectedOnDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").with(preAuthUser(42)))
                .andExpect(status().isForbidden());
    }

    // similar idea to the last test but this time there is no logged in user at all
    // no auth request post processor on this one on purpose
    // hit the dashboard endpoint completely unauthenticated
    // expect some flavor of 4xx client error, confirming anonymous requests never reach real account data
    @Test
    @DisplayName("Block 5: Unauthenticated request is rejected on the dashboard - [MEANT TO FAIL]")
    void testBlock5_unauthenticatedRequestRejectedOnDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().is4xxClientError());
    }

    // end to end style check that pulls together masking, filtering and correct balance formatting
    // gives a proper full auth session for user 42
    // stub the repository to return the one active checking account fixture
    // hit the dashboard endpoint
    // expect the masked number to only show the last four digits, the balance to come through as
    // a real number not a string, the routing number in full, and status reported as active
    @Test
    @DisplayName("Final Block: Full-Auth user retrieves masked, filtered, correctly-priced dashboard - [MEANT TO PASS]")
    void testFinalAC_fullAuthUserGetsMaskedFilteredDashboard() throws Exception {
        given(accountRepository.findByUserIdAndStatusNot(42L, AccountStatus.CLOSED))
                .willReturn(List.of(activeChecking));

        mockMvc.perform(get("/api/v1/accounts").with(fullAuthUser(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("......3210"))
                .andExpect(jsonPath("$[0].availableBalance").value(1500.0))
                .andExpect(jsonPath("$[0].routingNumber").value("021000021"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    // checking that hitting the transactions endpoint with no page params still applies sane defaults
    // stub the account lookup so account 1 resolves to our fixture, owned by user 42
    // stub the transaction repository for the exact pageable spring should build internally,
    // page zero, size fifty, sorted newest first by createdAt, since a generic pageimpl with an
    // empty list would otherwise report size zero regardless of what was actually asked for
    // hit get /api/v1/accounts/1/transactions with no query params at all
    // expect status ok and the size field in the response to reflect the real default of fifty
    @Test
    @DisplayName("Block 6: Default pagination applies size=50 and DESC sort by createdAt - [MEANT TO PASS]")
    void testBlock6_defaultPaginationAppliedCorrectly() throws Exception {
        given(accountRepository.findById(1L)).willReturn(Optional.of(activeChecking));
        // Stub with the exact expected Pageable so the page metadata (size=50) reflects the real request,
        // not an unpaged default - PageImpl<>(List.of()) alone reports size=0 regardless of what was asked.
        given(transactionRepository.findByAccountId(eq(1L), eq(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")), 0));

        mockMvc.perform(get("/api/v1/accounts/1/transactions").with(fullAuthUser(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    // making sure passing type=debit actually routes to the filtered repository query, not the general one
    // build one debit transaction using the little buildTransaction helper at the bottom of the file
    // stub the account lookup, then stub findbyaccountidandtransactiontype specifically for debit
    // hit the endpoint with the type=DEBIT query param
    // expect one transaction back in the content array, and that its type is debit
    @Test
    @DisplayName("Block 7: type=DEBIT routes to the filtered repository query - [MEANT TO PASS]")
    void testBlock7_debitFilterUsesFilteredQuery() throws Exception {
        TransactionEntity debit = buildTransaction(1L, TransactionType.DEBIT, new BigDecimal("75.5000"));
        given(accountRepository.findById(1L)).willReturn(Optional.of(activeChecking));
        given(transactionRepository.findByAccountIdAndTransactionType(eq(1L), eq(TransactionType.DEBIT), any()))
                .willReturn(new PageImpl<>(List.of(debit)));

        mockMvc.perform(get("/api/v1/accounts/1/transactions").param("type", "DEBIT").with(fullAuthUser(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].transactionType").value("DEBIT"));
    }

    // the flip side of the last test, no type filter should return everything, credits and debits both
    // build one credit and one debit transaction with the helper
    // stub the account lookup, then stub the plain findbyaccountid query, the unfiltered version
    // hit the endpoint with no type param at all
    // expect both transactions back in the content array
    @Test
    @DisplayName("Block 8: No type filter returns all transactions via the unfiltered query - [MEANT TO PASS]")
    void testBlock8_noFilterReturnsAllTransactions() throws Exception {
        TransactionEntity credit = buildTransaction(1L, TransactionType.CREDIT, new BigDecimal("200.0000"));
        TransactionEntity debit = buildTransaction(1L, TransactionType.DEBIT, new BigDecimal("50.0000"));
        given(accountRepository.findById(1L)).willReturn(Optional.of(activeChecking));
        given(transactionRepository.findByAccountId(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(credit, debit)));

        mockMvc.perform(get("/api/v1/accounts/1/transactions").with(fullAuthUser(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    // security check for ownership, user 42 should not be able to read another user's account transactions
    // build an account entity by hand that belongs to a completely different user, 999
    // stub the account lookup so account id 1 resolves to that not owned account
    // hit /api/v1/accounts/1/transactions logged in as user 42
    // expect a 403 forbidden, confirming the ownership check runs before any transaction data leaks
    @Test
    @DisplayName("Block 9: Requesting another user's accountId is rejected with 403 - [MEANT TO FAIL]")
    void testBlock9_ownershipMismatchReturns403() throws Exception {
        AccountEntity notOwned = new AccountEntity();
        notOwned.setId(1L);
        notOwned.setUserId(999L);
        given(accountRepository.findById(1L)).willReturn(Optional.of(notOwned));

        mockMvc.perform(get("/api/v1/accounts/1/transactions").with(fullAuthUser(42)))
                .andExpect(status().isForbidden());
    }

    // making sure asking for an account id that does not exist gives a clean 404, not a crash
    // stub the account lookup for id 999 so it returns completely empty
    // hit /api/v1/accounts/999/transactions
    // accountservice now throws a responsestatusexception with not_found for this case,
    // so spring maps it to a real http status instead of letting it blow up as an unhandled 500
    // expect some flavor of 4xx client error back
    @Test
    @DisplayName("Block 10: Non-existent accountId returns 404, not an unhandled 500 - [MEANT TO FAIL]")
    void testBlock10_nonExistentAccountIdReturns404() throws Exception {
        // AccountService.getAccountTransactions() now throws ResponseStatusException(NOT_FOUND, ...)
        // for a missing account instead of a raw IllegalArgumentException, so Spring MVC maps it to
        // a proper 404 rather than letting it escape as an unhandled 500.
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/999/transactions").with(fullAuthUser(42)))
                .andExpect(status().is4xxClientError());
    }

    // same pre auth restriction as the dashboard test earlier, but this time on the transactions endpoint
    // only grants scope_pre_auth here, meaning 2fa was never completed
    // hit the transactions endpoint for account 1 with that partial authority
    // expect a 403 forbidden since the class level preauthorize check covers every endpoint in this controller
    @Test
    @DisplayName("Block 11: Pre-Auth JWT cannot access transaction history - [MEANT TO FAIL]")
    void testBlock11_preAuthTokenBlockedFromTransactionHistory() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1/transactions").with(preAuthUser(42)))
                .andExpect(status().isForbidden());
    }

    // full end to end test tying pagination, filtering and ownership together in one request
    // build one credit transaction with the helper method
    // stub the account lookup so account 1 resolves to the fixture, owned by the logged in user
    // stub the filtered repository query for exactly page zero, size fifty, sorted by createdAt desc, credit only
    // hit the endpoint with explicit page, size and type query params matching that stub
    // expect status ok, the one credit transaction in the content array, and the page metadata
    // (total elements, total pages, current page number) all reflecting a single result correctly
    @Test
    @DisplayName("Final Block: Owner retrieves paginated, filtered, well-formed transaction page - [MEANT TO PASS]")
    void testFinalAC_ownerRetrievesPaginatedFilteredHistory() throws Exception {
        TransactionEntity credit = buildTransaction(1L, TransactionType.CREDIT, new BigDecimal("999.9900"));
        given(accountRepository.findById(1L)).willReturn(Optional.of(activeChecking));
        given(transactionRepository.findByAccountIdAndTransactionType(
                eq(1L), eq(TransactionType.CREDIT), eq(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .willReturn(new PageImpl<>(List.of(credit), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/accounts/1/transactions?page=0&size=50&type=CREDIT").with(fullAuthUser(42)))
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

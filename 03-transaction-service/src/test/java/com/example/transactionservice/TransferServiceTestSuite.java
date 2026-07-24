package com.example.transactionservice;

import com.example.transactionservice.aspect.KycEnforcementAspect;
import com.example.transactionservice.client.ProfileServiceClient;
import com.example.transactionservice.controller.TransferController.InternalTransferRequestDto;
import com.example.transactionservice.event.FundsTransferredEvent;
import com.example.transactionservice.event.LargeTransferRequestedEvent;
import com.example.transactionservice.model.AccountEntity;
import com.example.transactionservice.repository.AccountRepository;
import com.example.transactionservice.repository.TransactionRepository;
import com.example.transactionservice.service.ExternalWireService;
import com.example.transactionservice.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR7 (Internal Funds Transfer) & FR8 (External Wire Transfers) acceptance tests,
 * mirroring the Block/Final-Block pattern established in AuthManagementTestSuite
 * and ProfileServiceTestSuite. Kafka is mocked (@MockBean), no live broker required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class TransferServiceTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferService transferService;

    @Autowired
    private ExternalWireService externalWireService;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private ProfileServiceClient profileServiceClient;

    @MockBean
    private KafkaTemplate<String, FundsTransferredEvent> fundsTransferredKafkaTemplate;

    @MockBean
    private KafkaTemplate<String, LargeTransferRequestedEvent> largeTransferKafkaTemplate;

    // A canonical, checksum-valid IBAN (ISO 7064 MOD 97-10) used across banking test suites.
    private static final String VALID_IBAN = "GB29NWBK60161331926819";
    private static final String VALID_SWIFT = "DEUTDEFF";

    private AccountEntity fromAccount;
    private AccountEntity toAccount;

    // shared fixture that runs before every test, two accounts both owned by user 42
    // fromAccount starts with a thousand dollars available, toAccount starts with five hundred
    // also stub the profile service client so this user defaults to kyc approved, since most tests
    // are not actually about kyc, only the couple of kyc specific tests below override this default
    // note kycenforcementaspect reads the caller off of securitycontextholder, not the userId param,
    // so any test calling the service directly still needs withmockuser for @requireskyc to fire at all
    @BeforeEach
    void setUp() {
        fromAccount = new AccountEntity();
        fromAccount.setId(1L);
        fromAccount.setUserId(42L);
        fromAccount.setAvailableBalance(new BigDecimal("1000.0000"));

        toAccount = new AccountEntity();
        toAccount.setId(2L);
        toAccount.setUserId(42L);
        toAccount.setAvailableBalance(new BigDecimal("500.0000"));

        // Default: caller is KYC-APPROVED. Individual KYC-rejection tests override this stub.
        // KycEnforcementAspect resolves the caller from SecurityContextHolder, not from the
        // userId method parameter, so tests calling the service directly (not through MockMvc)
        // need @WithMockUser(username = "42", ...) for @RequiresKyc to reach this stub at all.
        given(profileServiceClient.getKycStatus(42L)).willReturn(Map.of("status", "APPROVED"));
    }

    /* ==========================================================
       USER STORY 7.1: Atomic Transfer Logic (@Transactional)
       ========================================================== */

    // checking that trying to move more money than is actually available gets rejected
    // stub both account lookups to return the fixture accounts from setUp
    // call executetransfer directly asking to move five thousand dollars, way more than fromAccount has
    // expect it to throw a responsestatusexception whose message mentions insufficient_funds
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 1: Insufficient funds rejects transfer with INSUFFICIENT_FUNDS - [MEANT TO FAIL]")
    void testBlock1_ExecuteTransfer_InsufficientFunds_ThrowsBadRequest() {
        // Requirement Cites: [Story 7.1 - AC1]
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));
        given(accountRepository.findByIdForUpdate(2L)).willReturn(Optional.of(toAccount));

        assertThatThrownBy(() ->
                transferService.executeTransfer(42L, 1L, 2L, new BigDecimal("5000.00")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INSUFFICIENT_FUNDS");
    }

    // making sure a user cannot transfer money into or out of an account that is not actually theirs
    // build a second account by hand that belongs to a totally different user, 999
    // stub the lookups so account 1 is the user's own account but account 2 belongs to that other user
    // call executetransfer trying to move money between them
    // expect a responsestatusexception explaining both accounts have to belong to the authenticated user
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 2: Transfer between accounts not owned by the caller is forbidden - [MEANT TO FAIL]")
    void testBlock2_ExecuteTransfer_OwnershipMismatch_ThrowsForbidden() {
        // Requirement Cites: [Story 7.1] ownership boundary implied by "own accounts" in FR7 feature statement
        AccountEntity otherUsersAccount = new AccountEntity();
        otherUsersAccount.setId(2L);
        otherUsersAccount.setUserId(999L);
        otherUsersAccount.setAvailableBalance(new BigDecimal("500.0000"));

        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));
        given(accountRepository.findByIdForUpdate(2L)).willReturn(Optional.of(otherUsersAccount));

        assertThatThrownBy(() ->
                transferService.executeTransfer(42L, 1L, 2L, new BigDecimal("100.00")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Both accounts must belong to the authenticated user");
    }

    /* ==========================================================
       USER STORY 7.2: High-Performance Locking (Pessimistic Locking)
       ========================================================== */

    // confirming the transfer actually goes through the locking version of the account lookup
    // stub findbyidforupdate for both accounts, this is the pessimistic lock query, not a plain find
    // run a normal hundred dollar transfer between the two fixture accounts
    // then verify findbyidforupdate specifically got called for both account ids
    // using pessimistic locking here so two transfers touching the same account cannot race each other
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 3: Transfer acquires pessimistic locks via findByIdForUpdate on both accounts - [MEANT TO PASS]")
    void testBlock3_ExecuteTransfer_UsesPessimisticLockOnBothAccounts() {
        // Requirement Cites: [Story 7.2 - AC1]
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));
        given(accountRepository.findByIdForUpdate(2L)).willReturn(Optional.of(toAccount));

        transferService.executeTransfer(42L, 1L, 2L, new BigDecimal("100.00"));

        verify(accountRepository).findByIdForUpdate(1L);
        verify(accountRepository).findByIdForUpdate(2L);
    }

    /* ==========================================================
       FINAL BLOCK: FR7 End-to-End Internal Transfer
       ========================================================== */

    // full end to end test for a normal successful internal transfer, going through the real http endpoint
    // deliberately not wrapping this test itself in a transaction, since the real code relies on
    // @transactionaleventlistener(phase = after_commit), and that would never fire if this test
    // wrapped everything in a transaction that just gets rolled back at the end
    // stub both account lookups to return the fixture accounts
    // build a request moving a hundred dollars from account 1 to account 2 and post it to the transfer endpoint
    // expect status ok with a transaction id and a completed status in the response
    // then check the actual account balances updated correctly in memory, nine hundred and six hundred
    // and confirm a fundstransferredevent got published to kafka on the successful transfers topic
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Final Block: Successful internal transfer commits balances, returns confirmation ID, and publishes FundsTransferredEvent to Kafka AFTER commit - [MEANT TO PASS]")
    void testFinalAC_InternalTransfer_SuccessCommitsAndPublishesEvent() throws Exception {
        // Requirement Cites: [Story 7.1 - AC2,AC3], [Story 7.2 - AC1,AC2], [Story 7.4 - AC1]
        // NOTE: deliberately NOT @Transactional at the test level - the production code relies on
        // @TransactionalEventListener(phase = AFTER_COMMIT), which never fires if the test itself
        // wraps the call in a transaction that gets rolled back.
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));
        given(accountRepository.findByIdForUpdate(2L)).willReturn(Optional.of(toAccount));

        InternalTransferRequestDto request = new InternalTransferRequestDto(1L, 2L, new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/transfers/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(fromAccount.getAvailableBalance()).isEqualByComparingTo("900.0000");
        assertThat(toAccount.getAvailableBalance()).isEqualByComparingTo("600.0000");
        verify(fundsTransferredKafkaTemplate).send(eq("successful-transfers"), any(String.class), any(FundsTransferredEvent.class));
    }

    /* ==========================================================
       USER STORY 8.1: External Wire Transfer Initiation
       ========================================================== */

    // making sure an obviously malformed iban gets caught before it ever reaches the service layer
    // build a raw json payload with a completely bogus iban string, not even close to the real format
    // post that to the external wire endpoint
    // expect a plain 400 bad request, this is jakarta validation's @pattern annotation on the dto
    // catching the bad shape before any real business logic even runs
    @Test
    @DisplayName("Block 4: Structurally invalid IBAN/SWIFT rejected before reaching the service - [MEANT TO FAIL]")
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    void testBlock4_ExternalWire_MalformedIban_ReturnsBadRequest() throws Exception {
        // Requirement Cites: [Story 8.1 - AC2] (jakarta.validation @Pattern on the DTO)
        String payload = """
                {"iban":"NOT_AN_IBAN","swiftCode":"DEUTDEFF","beneficiaryName":"John Smith","amount":100.00}
                """;

        mockMvc.perform(post("/api/v1/transfers/external")
                .param("fromAccountId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    // one level deeper than the last test, this iban looks correctly formatted but the checksum is wrong
    // stub the account lookup so account 1 resolves to the fixture
    // build a request with an iban that is one digit off from the real valid checksum iban constant,
    // which fails the actual mod 97 checksum math even though a simple regex would let it through
    // call initiatewire directly instead of going through mockmvc this time
    // expect a responsestatusexception mentioning invalid iban or swift code format
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 5: Structurally valid but checksum-invalid IBAN rejected by IbanSwiftValidator - [MEANT TO FAIL]")
    void testBlock5_ExternalWire_ChecksumInvalidIban_ThrowsBadRequest() {
        // Requirement Cites: [Story 8.1 - AC2] (ISO 7064 MOD 97-10 validation, not just regex)
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));

        var request = new com.example.transactionservice.dto.ExternalWireRequestDto(
                "GB30NWBK60161331926819", // one digit off from the valid checksum IBAN -> fails MOD 97
                VALID_SWIFT, "John Smith", new BigDecimal("100.00"));

        assertThatThrownBy(() -> externalWireService.initiateWire(42L, 1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid IBAN or SWIFT code format");
    }

    // making sure an external wire for more money than is available gets rejected up front
    // stub the account lookup for the fixture fromAccount, which only has a thousand dollars
    // build a wire request using the known valid iban and swift constants but asking for way more, 5000.01
    // call initiatewire directly
    // expect a responsestatusexception mentioning insufficient_funds, same style error as internal transfers
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 6: Insufficient funds rejects external wire before reserving funds - [MEANT TO FAIL]")
    void testBlock6_ExternalWire_InsufficientFunds_ThrowsBadRequest() {
        // Requirement Cites: [Story 8.1 - AC3]
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));

        var request = new com.example.transactionservice.dto.ExternalWireRequestDto(
                VALID_IBAN, VALID_SWIFT, "John Smith", new BigDecimal("5000.01"));

        assertThatThrownBy(() -> externalWireService.initiateWire(42L, 1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INSUFFICIENT_FUNDS");
    }

    /* ==========================================================
       USER STORY 8.2: Fraud Threshold & Pending Approval
       ========================================================== */

    // checking the exact boundary of the fraud review threshold, right at five thousand dollars
    // bump the fixture account balance up to ten thousand so there is plenty of room for this wire
    // build a wire request for exactly five thousand dollars, the threshold value itself
    // call initiatewire directly
    // since the rule is strictly greater than five thousand, this amount should complete right away
    // expect the response status to say completed and confirm no fraud review kafka event went out
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 7: Wire at or below $5000 completes immediately without a fraud event - [MEANT TO PASS]")
    void testBlock7_ExternalWire_AtThreshold_CompletesWithoutFraudEvent() {
        // Requirement Cites: [Story 8.2 - AC1] (threshold is strictly ">" 5000)
        fromAccount.setAvailableBalance(new BigDecimal("10000.0000"));
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));

        var request = new com.example.transactionservice.dto.ExternalWireRequestDto(
                VALID_IBAN, VALID_SWIFT, "John Smith", new BigDecimal("5000.00"));

        var response = externalWireService.initiateWire(42L, 1L, request);

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(largeTransferKafkaTemplate, never()).send(any(), any(), any());
    }

    // the flip side of the last test, going over the five thousand dollar threshold this time
    // bump the fixture balance up to ten thousand again so there is enough to reserve
    // build a wire request for seventy five hundred dollars, comfortably over the threshold
    // call initiatewire directly
    // expect the response status to say pending_approval instead of completed
    // check that the funds actually got reserved right away, balance drops to twenty five hundred
    // then confirm the account and a new transaction record both got saved
    // and that a largetransferrequestedevent went out to the fraud review topic for a human to look at
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Final Block: Wire over $5000 is pre-reserved, marked PENDING_APPROVAL, and publishes LargeTransferRequestedEvent - [MEANT TO PASS]")
    void testFinalAC_ExternalWire_OverThreshold_PendingApprovalAndFraudEvent() {
        // Requirement Cites: [Story 8.1 - AC1,AC2,AC3], [Story 8.2 - AC1,AC2,AC3]
        fromAccount.setAvailableBalance(new BigDecimal("10000.0000"));
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));

        var request = new com.example.transactionservice.dto.ExternalWireRequestDto(
                VALID_IBAN, VALID_SWIFT, "John Smith", new BigDecimal("7500.00"));

        var response = externalWireService.initiateWire(42L, 1L, request);

        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(fromAccount.getAvailableBalance()).isEqualByComparingTo("2500.0000");
        verify(accountRepository).save(fromAccount);
        verify(transactionRepository).save(any());
        verify(largeTransferKafkaTemplate).send(eq("large-transfers-review"), eq(response.transactionId().toString()), any(LargeTransferRequestedEvent.class));
    }

    /* ==========================================================
       CROSS-CUTTING: Auth Boundary Enforcement (mirrors FR5.4 pattern)
       ========================================================== */

    // making sure a pre auth session, meaning 2fa was never finished, cannot move money at all
    // withmockuser only grants scope_pre_auth here instead of the full auth scope other tests use
    // build a small ten dollar transfer request and post it to the internal transfer endpoint
    // expect a 403 forbidden since moving real money requires a fully authenticated session
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_PRE_AUTH"})
    @DisplayName("Block 8: Pre-Auth (partial 2FA) token denied on internal transfer endpoint - [MEANT TO FAIL]")
    void testBlock8_InternalTransfer_PreAuthTokenDenied() throws Exception {
        InternalTransferRequestDto request = new InternalTransferRequestDto(1L, 2L, new BigDecimal("10.00"));

        mockMvc.perform(post("/api/v1/transfers/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // same idea but no logged in user at all this time, not even a partial session
    // no withmockuser annotation here on purpose
    // post the same small transfer request with no authentication attached
    // expect some flavor of 4xx client error, confirming anonymous requests never get near real money
    @Test
    @DisplayName("Block 9: Unauthenticated request denied on internal transfer endpoint - [MEANT TO FAIL]")
    void testBlock9_InternalTransfer_UnauthenticatedDenied() throws Exception {
        InternalTransferRequestDto request = new InternalTransferRequestDto(1L, 2L, new BigDecimal("10.00"));

        mockMvc.perform(post("/api/v1/transfers/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    /* ==========================================================
       USER STORY FR3.1 AC3 (cross-service): KYC Enforcement on Fund Movement
       (@RequiresKyc is now applied to both TransferService.executeTransfer and
       ExternalWireService.initiateWire; KycEnforcementAspect blocks either call
       unless the Profile Service reports the caller's KYC status as APPROVED.)
       ========================================================== */

    // checking that kyc enforcement actually blocks a transfer for a user who is not approved yet
    // stub both account lookups like normal, but override the kyc stub to say pending_verification
    // call executetransfer directly
    // expect it to throw kycrequiredexception, the specific exception the aop aspect throws,
    // and its message should mention the pending_verification status so its clear why it was blocked
    // last, confirm the account repository never got a save call, no money moved before the kyc check ran
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Block 10: Non-APPROVED KYC status blocks an internal transfer - [MEANT TO FAIL]")
    void testBlock10_NonApprovedKyc_BlocksInternalTransfer() {
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));
        given(accountRepository.findByIdForUpdate(2L)).willReturn(Optional.of(toAccount));
        given(profileServiceClient.getKycStatus(42L)).willReturn(Map.of("status", "PENDING_VERIFICATION"));

        assertThatThrownBy(() -> transferService.executeTransfer(42L, 1L, 2L, new BigDecimal("50.00")))
                .isInstanceOf(KycEnforcementAspect.KycRequiredException.class)
                .hasMessageContaining("PENDING_VERIFICATION");

        verify(accountRepository, never()).save(any());
    }

    // same kyc gating check but this time on the external wire path instead of internal transfers
    // stub the account lookup, then override the kyc stub to say rejected this time, a harsher status
    // build a normal, otherwise valid wire request
    // call initiatewire directly
    // expect kycrequiredexception with a message mentioning rejected
    // and confirm neither the account nor a transaction record got saved, the aspect blocks it early
    @Test
    @WithMockUser(username = "42", authorities = {"SCOPE_FULL_AUTH"})
    @DisplayName("Final Block: Non-APPROVED KYC status blocks an external wire before any funds move - [MEANT TO FAIL]")
    void testFinalAC_NonApprovedKyc_BlocksExternalWire() {
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));
        given(profileServiceClient.getKycStatus(42L)).willReturn(Map.of("status", "REJECTED"));

        var request = new com.example.transactionservice.dto.ExternalWireRequestDto(
                VALID_IBAN, VALID_SWIFT, "John Smith", new BigDecimal("100.00"));

        assertThatThrownBy(() -> externalWireService.initiateWire(42L, 1L, request))
                .isInstanceOf(KycEnforcementAspect.KycRequiredException.class)
                .hasMessageContaining("REJECTED");

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}

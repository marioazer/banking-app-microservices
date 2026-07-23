package com.example.transactionservice;

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
    }

    /* ==========================================================
       USER STORY 7.1: Atomic Transfer Logic (@Transactional)
       ========================================================== */

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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
       GAP FINDING: FR3.1 AC3 cross-service KYC enforcement
       (@RequiresKyc is defined and KycEnforcementAspect implements the
       check, but the annotation is not actually placed on any
       TransferService/ExternalWireService method yet - so this test
       currently FAILS, correctly surfacing the missing wiring.)
       ========================================================== */

    @Test
    @DisplayName("Block 10 [GAP]: Non-APPROVED KYC status should block fund transfers - [MEANT TO PASS, CURRENTLY FAILS: @RequiresKyc not wired to TransferService]")
    void testBlock10_Gap_NonApprovedKyc_ShouldBlockTransfer() {
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(fromAccount));
        given(accountRepository.findByIdForUpdate(2L)).willReturn(Optional.of(toAccount));
        given(profileServiceClient.getKycStatus(42L)).willReturn(Map.of("status", "PENDING_VERIFICATION"));

        assertThatThrownBy(() -> transferService.executeTransfer(42L, 1L, 2L, new BigDecimal("50.00")))
                .isInstanceOf(RuntimeException.class);
    }
}

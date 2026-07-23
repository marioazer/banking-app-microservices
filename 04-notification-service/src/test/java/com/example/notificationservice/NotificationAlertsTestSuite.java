package com.example.notificationservice;

import com.example.notificationservice.client.AccountServiceClient;
import com.example.notificationservice.client.AccountServiceClient.UserAggregateBalanceResponse;
import com.example.notificationservice.client.ProfileServiceClient;
import com.example.notificationservice.client.ProfileServiceClient.UserPreferenceResponse;
import com.example.notificationservice.event.FundsTransferredEvent;
import com.example.notificationservice.job.DailyBalanceSummaryJob;
import com.example.notificationservice.service.NotificationProviderService;
import com.example.notificationservice.service.TransactionAlertListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * FR9 (Real-time Transaction Alerts) & FR10 (Daily Balance Summaries) acceptance tests.
 * Mirrors the Block/Final-Block pattern from AuthManagementTestSuite/ProfileServiceTestSuite.
 *
 * Kafka is NOT excluded here (unlike other services' suites): TransactionAlertListener and
 * ProfileNotificationListener are real @KafkaListener beans, and this module's existing
 * NotificationServiceApplicationTests already proved the context starts cleanly with the
 * default (unreachable localhost broker) config - consumer containers just retry connecting
 * in the background without blocking startup or these tests, which invoke the listener
 * methods directly rather than round-tripping through a real broker.
 */
@SpringBootTest
class NotificationAlertsTestSuite {

    @Autowired
    private TransactionAlertListener transactionAlertListener;

    @Autowired
    private DailyBalanceSummaryJob dailyBalanceSummaryJob;

    @MockBean
    private ProfileServiceClient profileServiceClient;

    @MockBean
    private AccountServiceClient accountServiceClient;

    @MockBean
    private NotificationProviderService notificationProviderService;

    @MockBean
    private Clock clock;

    // 2024-01-15T13:00:00Z is 08:00 local time in America/New_York (EST, UTC-5, no DST in
    // January) - a fixed instant so DailyBalanceSummaryJob's "which timezones are at 8 AM"
    // scan is deterministic across test runs, instead of depending on the real wall clock.
    private static final Instant EIGHT_AM_NEW_YORK = Instant.parse("2024-01-15T13:00:00Z");
    private static final String NEW_YORK_ZONE = "America/New_York";

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(EIGHT_AM_NEW_YORK);
    }

    /* ==========================================================
       USER STORY 9.2: Kafka Consumer for Transaction Events
       ========================================================== */

    @Test
    @DisplayName("Block 1: Transaction at/above the user's threshold dispatches an alert - [MEANT TO PASS]")
    void testBlock1_transferAtOrAboveThreshold_dispatchesAlert() {
        // Requirement Cites: [Story 9.2 - AC2, AC3]
        // userId (42L) is deliberately distinct from fromAccountId/toAccountId (501L/502L) so this
        // test cannot pass by accident if the listener regresses to using an account ID again.
        FundsTransferredEvent event = new FundsTransferredEvent(42L, 501L, 502L, new BigDecimal("150.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(42L))
                .willReturn(new UserPreferenceResponse(42L, new BigDecimal("100.00"), true, "America/New_York"));

        transactionAlertListener.consumeTransferEvent(event);

        verify(notificationProviderService).dispatchEmail(eq("user_42@bank.com"), anyString(), anyString());
        verify(profileServiceClient, never()).getUserPreferences(501L);
    }

    @Test
    @DisplayName("Block 2: Transaction below the user's threshold does not dispatch an alert - [MEANT TO PASS]")
    void testBlock2_transferBelowThreshold_noAlert() {
        // Requirement Cites: [Story 9.2 - AC3] (evaluate against threshold before dispatching)
        FundsTransferredEvent event = new FundsTransferredEvent(42L, 501L, 502L, new BigDecimal("50.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(42L))
                .willReturn(new UserPreferenceResponse(42L, new BigDecimal("100.00"), true, "America/New_York"));

        transactionAlertListener.consumeTransferEvent(event);

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    @Test
    @DisplayName("Block 3: Missing preferences skips the alert without throwing - [MEANT TO PASS]")
    void testBlock3_missingPreferences_skipsAlertGracefully() {
        // Requirement Cites: [Story 9.2] (defensive handling, no preferences record found)
        FundsTransferredEvent event = new FundsTransferredEvent(42L, 501L, 502L, new BigDecimal("500.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(42L)).willReturn(null);

        assertThatCode(() -> transactionAlertListener.consumeTransferEvent(event)).doesNotThrowAnyException();

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    @Test
    @DisplayName("Block 4: Profile Service failure is swallowed so the Kafka consumer thread survives - [MEANT TO PASS]")
    void testBlock4_profileServiceFailure_doesNotCrashListener() {
        // Requirement Cites: [Story 9.2] (a single bad event/downstream outage must not kill the consumer loop)
        FundsTransferredEvent event = new FundsTransferredEvent(42L, 501L, 502L, new BigDecimal("500.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(42L)).willThrow(new RuntimeException("Profile Service unavailable"));

        assertThatCode(() -> transactionAlertListener.consumeTransferEvent(event)).doesNotThrowAnyException();

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    /* ==========================================================
       USER STORY 9.2 (fixed): userId vs. account ID resolution
       (FundsTransferredEvent now carries an explicit userId field, populated by
       TransferService from its own ownership check, so TransactionAlertListener no longer
       has to guess. This replaces the old test that documented the conflation as a gap.)
       ========================================================== */

    @Test
    @DisplayName("Block 5: Listener queries preferences by the event's userId, never by an account ID - [MEANT TO PASS]")
    void testBlock5_listenerUsesUserIdNotAccountId() {
        FundsTransferredEvent event = new FundsTransferredEvent(777L, 111L, 222L, new BigDecimal("200.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(777L))
                .willReturn(new UserPreferenceResponse(777L, new BigDecimal("100.00"), true, "UTC"));

        transactionAlertListener.consumeTransferEvent(event);

        verify(profileServiceClient).getUserPreferences(777L);
        verify(profileServiceClient, never()).getUserPreferences(111L);
        verify(profileServiceClient, never()).getUserPreferences(222L);
        verify(notificationProviderService).dispatchEmail(eq("user_777@bank.com"), anyString(), anyString());
    }

    // USER STORY 9.3 (NotificationProviderService's own dispatch/retry/recover behavior) is
    // covered by the dedicated NotificationProviderServiceTestSuite, not here - this suite
    // @MockBeans NotificationProviderService to verify its callers, which would conflict with
    // exercising the real AOP-proxied bean's retry logic in the same Spring context.

    /* ==========================================================
       USER STORY 10.2: Scheduled Job for Balance Aggregation
       ========================================================== */

    @Test
    @DisplayName("Block 7: Opted-in users with a matching balance receive a summary email - [MEANT TO PASS]")
    void testBlock7_optedInUsersWithBalance_receiveSummaryEmail() {
        // Requirement Cites: [Story 10.2 - AC2, AC3]
        // DailyBalanceSummaryJob now reads Instant.now(clock) instead of the real wall clock, so
        // with the fixed 8 AM America/New_York instant stubbed in @BeforeEach, exactly which
        // timezone string the job queries is deterministic - this stubs and verifies that one
        // zone specifically, rather than answering (and counting calls) for every zone at once.
        given(profileServiceClient.getUsersForDailySummary(eq(NEW_YORK_ZONE)))
                .willReturn(List.of(new UserPreferenceResponse(100L, new BigDecimal("100.00"), true, NEW_YORK_ZONE)));
        given(accountServiceClient.getAggregateBalancesBatch(eq(List.of(100L))))
                .willReturn(List.of(new UserAggregateBalanceResponse(100L, new BigDecimal("5432.10"))));

        dailyBalanceSummaryJob.processDailySummaries();

        verify(profileServiceClient, times(1)).getUsersForDailySummary(eq(NEW_YORK_ZONE));
        verify(notificationProviderService, times(1))
                .dispatchEmail(eq("user_100@bank.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("Block 8: No opted-in users means no downstream balance lookup or email - [MEANT TO PASS]")
    void testBlock8_noOptedInUsers_noDownstreamCalls() {
        // Requirement Cites: [Story 10.2 - AC2] (empty result set short-circuits)
        given(profileServiceClient.getUsersForDailySummary(anyString())).willReturn(List.of());

        dailyBalanceSummaryJob.processDailySummaries();

        verify(accountServiceClient, never()).getAggregateBalancesBatch(any());
        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    @Test
    @DisplayName("Block 9: A user with no matching balance entry is skipped, not errored - [MEANT TO PASS]")
    void testBlock9_userWithoutMatchingBalance_isSkipped() {
        // Requirement Cites: [Story 10.2] (in-memory join must not fail on a partial result set)
        given(profileServiceClient.getUsersForDailySummary(anyString()))
                .willReturn(List.of(new UserPreferenceResponse(200L, new BigDecimal("100.00"), true, "any")));
        given(accountServiceClient.getAggregateBalancesBatch(eq(List.of(200L))))
                .willReturn(List.of()); // Account Service returned nothing for this user

        assertThatCode(() -> dailyBalanceSummaryJob.processDailySummaries()).doesNotThrowAnyException();

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    /* ==========================================================
       USER STORY 10.3: Error Isolation Per Timezone
       ========================================================== */

    @Test
    @DisplayName("Final Block: A failure looking up one timezone's users does not abort the sweep - [MEANT TO PASS]")
    void testFinalAC_timezoneFailure_isolatedAndDoesNotPropagate() {
        // Requirement Cites: [Story 10.3] (per-timezone try/catch so one region's outage
        // doesn't block the rest of the world's daily summaries)
        given(profileServiceClient.getUsersForDailySummary(anyString()))
                .willThrow(new RuntimeException("Profile Service unavailable"));

        assertThatCode(() -> dailyBalanceSummaryJob.processDailySummaries()).doesNotThrowAnyException();

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }
}

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

    // runs before every test, pins the mocked clock to a fixed instant, 8am new york time in january
    // this makes dailybalancesummaryjob's timezone scan deterministic instead of depending on
    // whatever the real wall clock happens to be when the test suite runs
    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(EIGHT_AM_NEW_YORK);
    }

    // checking that a transfer at or above the user's alert threshold actually triggers an email
    // build a fundstransferredevent, using userId 42 but deliberately different account ids, 501 and 502,
    // so this test cannot pass by accident if the listener code regresses to reading an account id instead
    // stub the user's preferences with a hundred dollar threshold, well below the 150 dollar transfer
    // call consumetransferevent directly like the kafka listener would
    // verify an email got dispatched to this specific user's address
    // and verify the listener never mistakenly looked up preferences using the account id instead
    @Test
    @DisplayName("Block 1: Transaction at/above the user's threshold dispatches an alert - [MEANT TO PASS]")
    void testBlock1_transferAtOrAboveThreshold_dispatchesAlert() {
        // userId (42L) is deliberately distinct from fromAccountId/toAccountId (501L/502L) so this
        // test cannot pass by accident if the listener regresses to using an account ID again.
        FundsTransferredEvent event = new FundsTransferredEvent(42L, 501L, 502L, new BigDecimal("150.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(42L))
                .willReturn(new UserPreferenceResponse(42L, new BigDecimal("100.00"), true, "America/New_York"));

        transactionAlertListener.consumeTransferEvent(event);

        verify(notificationProviderService).dispatchEmail(eq("user_42@bank.com"), anyString(), anyString());
        verify(profileServiceClient, never()).getUserPreferences(501L);
    }

    // the flip side of the last test, a small transfer that should stay under the radar
    // build an event for a fifty dollar transfer, below the stubbed hundred dollar threshold
    // call consumetransferevent directly
    // verify dispatchemail never got called at all, since the amount never crossed the threshold
    @Test
    @DisplayName("Block 2: Transaction below the user's threshold does not dispatch an alert - [MEANT TO PASS]")
    void testBlock2_transferBelowThreshold_noAlert() {
        FundsTransferredEvent event = new FundsTransferredEvent(42L, 501L, 502L, new BigDecimal("50.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(42L))
                .willReturn(new UserPreferenceResponse(42L, new BigDecimal("100.00"), true, "America/New_York"));

        transactionAlertListener.consumeTransferEvent(event);

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    // defensive check for when the profile service has no preferences on file for this user at all
    // stub getuserpreferences to return null, like a user who never set up alert preferences
    // call consumetransferevent and expect it to not throw any exception
    // then verify no email attempt was made either, since there is nothing to base a threshold check on
    @Test
    @DisplayName("Block 3: Missing preferences skips the alert without throwing - [MEANT TO PASS]")
    void testBlock3_missingPreferences_skipsAlertGracefully() {
        FundsTransferredEvent event = new FundsTransferredEvent(42L, 501L, 502L, new BigDecimal("500.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(42L)).willReturn(null);

        assertThatCode(() -> transactionAlertListener.consumeTransferEvent(event)).doesNotThrowAnyException();

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    // making sure a downstream outage in the profile service does not take down the kafka consumer thread
    // stub getuserpreferences to throw a runtime exception, simulating profile service being unreachable
    // call consumetransferevent and expect it to not throw anything back out
    // then verify no email attempt was made, since we could not even check the threshold in the first place
    // a single bad event or a temporary outage should never kill the whole consumer loop
    @Test
    @DisplayName("Block 4: Profile Service failure is swallowed so the Kafka consumer thread survives - [MEANT TO PASS]")
    void testBlock4_profileServiceFailure_doesNotCrashListener() {
        FundsTransferredEvent event = new FundsTransferredEvent(42L, 501L, 502L, new BigDecimal("500.00"), UUID.randomUUID());
        given(profileServiceClient.getUserPreferences(42L)).willThrow(new RuntimeException("Profile Service unavailable"));

        assertThatCode(() -> transactionAlertListener.consumeTransferEvent(event)).doesNotThrowAnyException();

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    // regression test making sure the listener uses the real userId field, not one of the account ids
    // build an event with userId 777 and two very different looking account ids, 111 and 222
    // stub preferences only for user 777, leaving 111 and 222 completely unstubbed on purpose
    // call consumetransferevent
    // verify preferences got looked up for 777 specifically, and never for either account id
    // then confirm the email still went out correctly to that user's address
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

    // checking the scheduled daily summary job actually emails users who are opted in and have a balance
    // thanks to the clock stub in setUpClock this always looks like 8am in america/new_york,
    // so we know exactly which timezone string the job is going to query for, no guessing needed
    // stub the profile service to return one opted in user for that timezone
    // stub the account service to return an aggregate balance for that same user
    // call processdailysummaries directly, the same way the scheduler would trigger it
    // verify the timezone lookup happened exactly once, and that one summary email actually went out
    @Test
    @DisplayName("Block 7: Opted-in users with a matching balance receive a summary email - [MEANT TO PASS]")
    void testBlock7_optedInUsersWithBalance_receiveSummaryEmail() {
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

    // making sure an empty opted in list short circuits instead of doing pointless downstream work
    // stub the profile service to return an empty list no matter what timezone gets asked for
    // call processdailysummaries
    // verify the account service balance lookup never got called at all
    // and verify no email attempt was made either, since there was nobody to send one to
    @Test
    @DisplayName("Block 8: No opted-in users means no downstream balance lookup or email - [MEANT TO PASS]")
    void testBlock8_noOptedInUsers_noDownstreamCalls() {
        given(profileServiceClient.getUsersForDailySummary(anyString())).willReturn(List.of());

        dailyBalanceSummaryJob.processDailySummaries();

        verify(accountServiceClient, never()).getAggregateBalancesBatch(any());
        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    // edge case where the profile service knows about a user but the account service has no balance for them
    // stub the profile service to return one opted in user
    // stub the account service's batch balance lookup to come back completely empty for that user
    // call processdailysummaries and expect no exception, this is an in memory join that has to handle gaps
    // then verify no email attempt was made for a user with no balance data to actually report
    @Test
    @DisplayName("Block 9: A user with no matching balance entry is skipped, not errored - [MEANT TO PASS]")
    void testBlock9_userWithoutMatchingBalance_isSkipped() {
        given(profileServiceClient.getUsersForDailySummary(anyString()))
                .willReturn(List.of(new UserPreferenceResponse(200L, new BigDecimal("100.00"), true, "any")));
        given(accountServiceClient.getAggregateBalancesBatch(eq(List.of(200L))))
                .willReturn(List.of()); // Account Service returned nothing for this user

        assertThatCode(() -> dailyBalanceSummaryJob.processDailySummaries()).doesNotThrowAnyException();

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }

    // making sure one timezone's outage cannot take down the entire daily summary sweep for every region
    // stub the profile service so looking up users for any timezone string just throws
    // call processdailysummaries and expect it to not throw anything back out to the scheduler
    // then verify no email attempt was made, since nothing could be looked up in this failure scenario
    // the real job wraps each timezone in its own try/catch so one region's problem stays contained
    @Test
    @DisplayName("Final Block: A failure looking up one timezone's users does not abort the sweep - [MEANT TO PASS]")
    void testFinalAC_timezoneFailure_isolatedAndDoesNotPropagate() {
        given(profileServiceClient.getUsersForDailySummary(anyString()))
                .willThrow(new RuntimeException("Profile Service unavailable"));

        assertThatCode(() -> dailyBalanceSummaryJob.processDailySummaries()).doesNotThrowAnyException();

        verify(notificationProviderService, never()).dispatchEmail(any(), any(), any());
    }
}

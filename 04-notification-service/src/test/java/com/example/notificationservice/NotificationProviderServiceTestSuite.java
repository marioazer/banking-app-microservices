package com.example.notificationservice;

import com.example.notificationservice.client.EmailProviderClient;
import com.example.notificationservice.service.NotificationProviderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * FR9.3 / FR10.3: NotificationProviderService's @Retryable/@Recover behavior.
 *
 * A separate suite from NotificationAlertsTestSuite on purpose: that suite @MockBeans
 * NotificationProviderService itself (to verify TransactionAlertListener/DailyBalanceSummaryJob
 * call it correctly), which replaces the real AOP-proxied bean for the whole test class. This
 * suite needs the real bean - with @EnableRetry actually wired up - so it mocks one level
 * lower, at EmailProviderClient, which is now the seam @Retryable/@Recover can actually see
 * fail. Previously the "failure" was a hardcoded `boolean simulateNetworkFailure = false` with
 * no way to flip it from outside the class, so this behavior was unreachable through any real
 * code path.
 */
@SpringBootTest
class NotificationProviderServiceTestSuite {

    @Autowired
    private NotificationProviderService notificationProviderService;

    @MockBean
    private EmailProviderClient emailProviderClient;

    @Test
    @DisplayName("Block 1: Successful dispatch calls the provider exactly once, no retries - [MEANT TO PASS]")
    void testBlock1_successfulDispatch_singleAttempt() {
        notificationProviderService.dispatchEmail("user_1@bank.com", "Subject", "<p>Body</p>");

        verify(emailProviderClient, times(1)).send("user_1@bank.com", "Subject", "<p>Body</p>");
    }

    @Test
    @DisplayName("Final Block: Provider failing every attempt is retried 3 times then recovers without propagating - [MEANT TO PASS]")
    void testFinalAC_persistentProviderFailure_retriesThenRecovers() {
        // Requirement Cites: [Story 9.3 - AC2 Tech Task], [Story 10.3 Tech Task]
        doThrow(new RuntimeException("503 Service Unavailable: SendGrid API Gateway timeout"))
                .when(emailProviderClient).send(anyString(), anyString(), anyString());

        // @Recover means a persistently-failing provider must NOT surface as an exception to the caller.
        assertThatCode(() ->
                notificationProviderService.dispatchEmail("user_2@bank.com", "Subject", "<p>Body</p>"))
                .doesNotThrowAnyException();

        // maxAttempts = 3: the real send() call is attempted exactly 3 times before @Recover takes over.
        verify(emailProviderClient, times(3)).send(eq("user_2@bank.com"), eq("Subject"), eq("<p>Body</p>"));
    }
}

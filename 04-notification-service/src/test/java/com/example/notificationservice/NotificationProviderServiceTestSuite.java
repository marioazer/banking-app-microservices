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

@SpringBootTest
class NotificationProviderServiceTestSuite {

    @Autowired
    private NotificationProviderService notificationProviderService;

    @MockBean
    private EmailProviderClient emailProviderClient;

    // basic happy path, sending an email that succeeds on the very first try
    // call dispatchemail directly with a normal address, subject and html body
    // since emailproviderclient is mocked and not stubbed to throw anything, the call just succeeds
    // verify send() was called exactly once, confirming no retry logic kicked in for a clean send
    @Test
    @DisplayName("Block 1: Successful dispatch calls the provider exactly once, no retries - [MEANT TO PASS]")
    void testBlock1_successfulDispatch_singleAttempt() {
        notificationProviderService.dispatchEmail("user_1@bank.com", "Subject", "<p>Body</p>");

        verify(emailProviderClient, times(1)).send("user_1@bank.com", "Subject", "<p>Body</p>");
    }

    // this is the real test for the @retryable / @recover behavior mentioned in the class comment
    // stub emailproviderclient.send so it always throws, simulating a provider that is completely down
    // call dispatchemail and expect it to not throw anything back out to the caller at all,
    // because @recover is supposed to swallow the persistent failure once retries are exhausted
    // then verify send() actually got called three separate times, matching maxattempts = 3,
    // before @recover took over instead of giving up after just the first failure
    @Test
    @DisplayName("Final Block: Provider failing every attempt is retried 3 times then recovers without propagating - [MEANT TO PASS]")
    void testFinalAC_persistentProviderFailure_retriesThenRecovers() {
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

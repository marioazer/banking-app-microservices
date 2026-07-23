package com.example.notificationservice.job;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.notificationservice.client.AccountServiceClient;
import com.example.notificationservice.client.ProfileServiceClient;
import com.example.notificationservice.service.NotificationProviderService;

/**
 * Scheduled job for aggregating and dispatching daily balance summaries.
 * Fulfills FR10.2 and FR10.3[cite: 5].
 */
@Component
public class DailyBalanceSummaryJob {

    private static final Logger log = LoggerFactory.getLogger(DailyBalanceSummaryJob.class);

    private final ProfileServiceClient profileServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final NotificationProviderService notificationProviderService;
    private final Clock clock;

    public DailyBalanceSummaryJob(ProfileServiceClient profileServiceClient,
                                  AccountServiceClient accountServiceClient,
                                  NotificationProviderService notificationProviderService,
                                  Clock clock) {
        this.profileServiceClient = profileServiceClient;
        this.accountServiceClient = accountServiceClient;
        this.notificationProviderService = notificationProviderService;
        this.clock = clock;
    }

    /**
     * Executes at the top of every hour (e.g., 1:00, 2:00, 3:00).
     * Fulfills FR10.2 AC1 & Tech Task: Timezone-aware job execution[cite: 5].
     */
    @Scheduled(cron = "0 0 * * * *")
    public void processDailySummaries() {
        log.info("Starting hourly sweep for 8:00 AM Daily Balance Summaries.");

        Instant now = Instant.now(clock);

        // 1. Find all global timezones where the current local time is 8:00 AM
        List<String> targetTimezones = findTimezonesAtHour(now, 8);

        if (targetTimezones.isEmpty()) {
            log.info("No timezones are currently at 8:00 AM. Job ending.");
            return;
        }

        // 2. Process each target timezone
        for (String timezone : targetTimezones) {
            try {
                processUsersForTimezone(timezone);
            } catch (Exception e) {
                // Fulfills FR10.3 Tech Task: Implement error handling for unavailability[cite: 5].
                // We catch exceptions per-timezone so a failure in one region 
                // doesn't block processing for the rest of the world.
                log.error("Failed to process daily summaries for timezone: {}", timezone, e);
            }
        }
    }

    private List<String> findTimezonesAtHour(Instant now, int hour) {
        return ZoneId.getAvailableZoneIds().stream()
                .filter(zoneId -> {
                    ZonedDateTime localTime = ZonedDateTime.ofInstant(now, ZoneId.of(zoneId));
                    return localTime.getHour() == hour;
                })
                .toList();
    }

    private void processUsersForTimezone(String timezone) {
        // 3. Fetch users who opted in and belong to this timezone[cite: 5]
        List<ProfileServiceClient.UserPreferenceResponse> users =
                profileServiceClient.getUsersForDailySummary(timezone);

        if (users == null) {
            return;
        }
        if (users.isEmpty()) {
            return;
        }

        List<Long> userIds = users.stream()
                .map(ProfileServiceClient.UserPreferenceResponse::userId)
                .toList();

        log.info("Found {} opted-in users for timezone {}. Fetching bulk balances.", userIds.size(), timezone);

        // 4. Perform the single batch network call to avoid N+1 query problems[cite: 5]
        Map<Long, AccountServiceClient.UserAggregateBalanceResponse> balanceMap = fetchBalanceMap(userIds);

        // 5. In-Memory Join & Dispatch[cite: 5]
        dispatchSummaries(users, balanceMap);
    }

    private Map<Long, AccountServiceClient.UserAggregateBalanceResponse> fetchBalanceMap(List<Long> userIds) {
        List<AccountServiceClient.UserAggregateBalanceResponse> balances =
                accountServiceClient.getAggregateBalancesBatch(userIds);

        // Convert balance list into a Map for O(1) instantaneous lookup during the loop
        return balances.stream()
                .collect(Collectors.toMap(
                        AccountServiceClient.UserAggregateBalanceResponse::userId,
                        b -> b
                ));
    }

    private void dispatchSummaries(List<ProfileServiceClient.UserPreferenceResponse> users,
                                    Map<Long, AccountServiceClient.UserAggregateBalanceResponse> balanceMap) {
        for (ProfileServiceClient.UserPreferenceResponse user : users) {
            AccountServiceClient.UserAggregateBalanceResponse userBalance = balanceMap.get(user.userId());

            if (userBalance != null) {
                String emailSubject = "Your Daily Balance Summary";
                String emailHtml = buildHtmlSummary(userBalance);
                String userEmail = "user_" + user.userId() + "@bank.com"; // Placeholder mapping

                notificationProviderService.dispatchEmail(userEmail, emailSubject, emailHtml);
            }
        }
    }

    /**
     * Constructs a professionally formatted HTML email.
     * Fulfills FR10.3 AC2[cite: 5].
     */
    private String buildHtmlSummary(AccountServiceClient.UserAggregateBalanceResponse balanceData) {
        return """
               <html>
                   <body>
                       <h2>Good Morning!</h2>
                       <p>Here is your daily aggregate balance summary:</p>
                       <div style="font-size: 24px; font-weight: bold; color: #2E86C1;">
                           Total Aggregate Balance: $%s
                       </div>
                       <p>Thank you for banking with us.</p>
                   </body>
               </html>
               """.formatted(balanceData.totalBalance());
    }
}
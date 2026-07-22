package com.example.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;

/**
 * Declarative REST client for communicating with the Account Service.
 * Fulfills FR10.2 AC2 & AC3: Retrieves current aggregate balances for opted-in users[cite: 5].
 */
@FeignClient(name = "account-service", url = "${account-service.url:http://account-service:8080}")
public interface AccountServiceClient {

    /**
     * Local representation of the Account Service's batch balance response.
     */
    record UserAggregateBalanceResponse(
            Long userId,
            BigDecimal totalBalance
    ) {}

    /**
     * Fetches aggregate balances for a bulk list of users in a single network hop.
     * Fulfills FR10.2 Tech Task: Optimize balance retrieval to batch requests, 
     * preventing N+1 query problems[cite: 5].
     * 
     * @param userIds A list of User IDs gathered from the Profile Service.
     * @return A list containing the total aggregate balance for each requested user.
     */
    @PostMapping("/api/v1/accounts/balances/batch")
    List<UserAggregateBalanceResponse> getAggregateBalancesBatch(@RequestBody List<Long> userIds);
}
package com.example.transactionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

/**
 * Feign client for the account-service internal API. account-service is the sole owner of the
 * "accounts"/"transactions" tables - this service no longer touches them directly, it delegates
 * every balance mutation here instead (see InternalAccountController on the account-service side).
 */
@FeignClient(name = "account-service", url = "${application.client.account-service.url:http://localhost:8083}")
public interface AccountServiceClient {

    record TransferRequest(Long userId, Long fromAccountId, Long toAccountId, BigDecimal amount) {}
    record DebitRequest(Long userId, BigDecimal amount, String description) {}
    record CreditRequest(BigDecimal amount, String description) {}

    /**
     * Executes an atomic, pessimistic-locked internal account-to-account transfer inside
     * account-service. Throws (via the ErrorDecoder in FeignErrorConfig) a ResponseStatusException
     * carrying the same status/message account-service produced, e.g. 400 "INSUFFICIENT_FUNDS".
     */
    @PostMapping("/api/v1/internal/accounts/transfer")
    void transfer(@RequestBody TransferRequest request);

    /**
     * Pre-reserves (debits) funds on a single account, used by external wire initiation.
     */
    @PostMapping("/api/v1/internal/accounts/{accountId}/debit")
    void debit(@PathVariable("accountId") Long accountId, @RequestBody DebitRequest request);

    /**
     * Returns previously-reserved funds to an account, used when a pending wire is rejected.
     */
    @PostMapping("/api/v1/internal/accounts/{accountId}/credit")
    void credit(@PathVariable("accountId") Long accountId, @RequestBody CreditRequest request);
}

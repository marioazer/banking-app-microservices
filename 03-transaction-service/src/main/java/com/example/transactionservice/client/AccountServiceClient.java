package com.example.transactionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "account-service", url = "${application.client.account-service.url:http://localhost:8083}")
public interface AccountServiceClient {

    record TransferRequest(Long userId, Long fromAccountId, Long toAccountId, BigDecimal amount) {}
    record DebitRequest(Long userId, BigDecimal amount, String description) {}
    record CreditRequest(BigDecimal amount, String description) {}

    @PostMapping("/api/v1/internal/accounts/transfer")
    void transfer(@RequestBody TransferRequest request);

    @PostMapping("/api/v1/internal/accounts/{accountId}/debit")
    void debit(@PathVariable("accountId") Long accountId, @RequestBody DebitRequest request);

    @PostMapping("/api/v1/internal/accounts/{accountId}/credit")
    void credit(@PathVariable("accountId") Long accountId, @RequestBody CreditRequest request);
}

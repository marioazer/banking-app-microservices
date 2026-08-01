package com.example.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;

// second feign client in this service, same declarative pattern as ProfileServiceClient below,
// just pointed at a different downstream service and url property
@FeignClient(name = "account-service", url = "${account-service.url:http://localhost:8083}")
public interface AccountServiceClient {

    record UserAggregateBalanceResponse(
            Long userId,
            BigDecimal totalBalance
    ) {}

    @PostMapping("/api/v1/accounts/balances/batch")
    List<UserAggregateBalanceResponse> getAggregateBalancesBatch(@RequestBody List<Long> userIds);
}
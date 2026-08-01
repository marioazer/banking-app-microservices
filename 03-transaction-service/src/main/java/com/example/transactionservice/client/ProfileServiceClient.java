package com.example.transactionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

// In production, 'url' is omitted in favor of a Service Registry like Netflix Eureka.
// learned feign is a declarative http client, this whole interface has no actual implementation
// body anywhere, spring generates the real http call at startup just from these annotations
@FeignClient(name = "profile-service", url = "${application.client.profile-service.url:http://localhost:8082}")
public interface ProfileServiceClient {

    // the @GetMapping/@PathVariable annotations here look identical to a controller, but on a
    // feign client they describe an outgoing request instead of an incoming one
    @GetMapping("/api/v1/profiles/{userId}/kyc-status")
    Map<String, String> getKycStatus(@PathVariable("userId") Long userId);

}
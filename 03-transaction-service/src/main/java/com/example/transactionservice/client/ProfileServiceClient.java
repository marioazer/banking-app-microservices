package com.example.transactionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Feign Client for synchronous communication with the Profile microservice.
 * This abstracts away all the boilerplate of manual HTTP calls.
 */
// In production, 'url' is omitted in favor of a Service Registry like Netflix Eureka.
@FeignClient(name = "profile-service", url = "${application.client.profile-service.url:http://localhost:8082}")
public interface ProfileServiceClient {

    /**
     * Executes an HTTP GET request to the Profile Service to retrieve the user's current KYC state.
     * Maps to FR3.1 AC2: The Profile Service must expose an endpoint for other services to query.
     */
    @GetMapping("/api/v1/profiles/{userId}/kyc-status")
    Map<String, String> getKycStatus(@PathVariable("userId") Long userId);
    
}
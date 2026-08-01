package com.example.transactionservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@Configuration
public class FeignErrorConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            HttpStatus status = HttpStatus.resolve(response.status());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            return new ResponseStatusException(status, extractMessage(response));
        };
    }

    // account-service returns Spring Boot's default error body ({"message": "...", ...}) as long
    // as server.error.include-message=always is set there - fall back to the raw HTTP reason
    // phrase if the body is missing or isn't in that shape.
    private String extractMessage(Response response) {
        if (response.body() == null) {
            return response.reason();
        }
        try (var body = response.body().asInputStream()) {
            Map<?, ?> parsed = new ObjectMapper().readValue(body, Map.class);
            Object message = parsed.get("message");
            return message != null ? message.toString() : response.reason();
        } catch (IOException e) {
            return response.reason();
        }
    }
}

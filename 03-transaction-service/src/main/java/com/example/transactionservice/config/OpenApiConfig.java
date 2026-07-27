package com.example.transactionservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transaction Service API")
                .description("Payments & transfers: atomic internal transfers and external wires with fraud-threshold review, delegating account balance mutations to account-service (FR7, FR8).")
                .version("v1"));
    }
}

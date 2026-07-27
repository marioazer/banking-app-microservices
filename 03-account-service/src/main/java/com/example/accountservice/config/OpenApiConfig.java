package com.example.accountservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI accountServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Account Service API")
                .description("Sole owner of the accounts/transactions ledger: masked account dashboard, paginated transaction history (FR5, FR6). Also exposes an internal-only API for transfer/debit/credit calls from transaction-service and batch-balance lookups from notification-service.")
                .version("v1"));
    }
}

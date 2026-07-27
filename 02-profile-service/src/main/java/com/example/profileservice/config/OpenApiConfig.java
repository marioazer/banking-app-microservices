package com.example.profileservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI profileServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Profile Service API")
                .description("Customer information: KYC status/webhook/admin override, contact info, alert & daily-summary preferences (FR3, FR4, FR9.1, FR10.1).")
                .version("v1"));
    }
}

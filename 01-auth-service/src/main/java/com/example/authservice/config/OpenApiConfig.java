package com.example.authservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Auth Service API")
                .description("Identity & Access Management: login, device fingerprinting, TOTP/SMS 2FA, session refresh/logout (FR1, FR2).")
                .version("v1"));
    }
}

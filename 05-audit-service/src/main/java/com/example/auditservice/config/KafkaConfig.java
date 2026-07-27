package com.example.auditservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Without this, ProfileAuditListener's Map<String, Object> parameter has no way to turn the raw
 * JSON payload string into a Map - Spring Boot only auto-registers this converter if one is
 * present as a bean.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public RecordMessageConverter recordMessageConverter() {
        return new StringJsonMessageConverter();
    }
}

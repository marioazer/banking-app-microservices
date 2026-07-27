package com.example.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Without this, @KafkaListener methods that take a typed parameter (FundsTransferredEvent) or a
 * Map (ProfileNotificationListener) have no way to turn the raw JSON payload string into that
 * type - Spring Boot only auto-registers this converter if one is present as a bean.
 * StringJsonMessageConverter infers the target type per-listener from the method signature
 * itself, so this works even though the producer (a different service) and this consumer each
 * define their own, separately-compiled copy of the "same" event class.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public RecordMessageConverter recordMessageConverter() {
        return new StringJsonMessageConverter();
    }
}

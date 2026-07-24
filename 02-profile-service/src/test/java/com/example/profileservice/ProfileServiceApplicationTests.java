package com.example.profileservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:profiletestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "kyc.vendor.webhook.secret=SuperSecretVendorKey123!"
})
class ProfileServiceApplicationTests {

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    // default spring boot smoke test, just confirming the whole context wires up cleanly
    // uses an in memory h2 database so nothing here touches the real postgres instance
    // the kafka template is mocked out so there is no dependency on a real broker being up
    // if any bean fails to construct or wire correctly this test fails on its own
    @Test
    void contextLoads() {
    }
}
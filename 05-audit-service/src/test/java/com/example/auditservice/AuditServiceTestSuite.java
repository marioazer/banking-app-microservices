package com.example.auditservice;

import com.example.auditservice.model.AuditLogEntity;
import com.example.auditservice.repository.AuditLogRepository;
import com.example.auditservice.service.ProfileAuditListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * FR4.3 (Immutable Audit Logging) acceptance tests, mirroring the Block/Final-Block pattern
 * established across the other services' suites.
 *
 * AC1 ("An independent Audit Service must consume messages from the profile-events Kafka
 * topic") is verified at context-load time by AuditServiceApplicationTests, whose log output
 * shows "Subscribed to topic(s): profile-events" for the @KafkaListener bean - this suite
 * exercises the listener method directly rather than round-tripping through a real broker,
 * same approach used for TransactionAlertListener/ProfileNotificationListener elsewhere.
 */
@SpringBootTest
class AuditServiceTestSuite {

    @Autowired
    private ProfileAuditListener profileAuditListener;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuditLogRepository auditLogRepository;

    /* ==========================================================
       USER STORY 4.3: Immutable Audit Logging
       ========================================================== */

    @Test
    @DisplayName("Block 1: Valid ProfileUpdatedEvent payload is mapped and persisted correctly - [MEANT TO PASS]")
    void testBlock1_validPayload_mapsAndPersistsEntity() {
        // Requirement Cites: [Story 4.3 - AC2, AC3]
        Map<String, Object> payload = Map.of(
                "userId", "100",
                "eventType", "PHONE_CHANGE",
                "changes", Map.of("phoneNumber", Map.of("old", "+15551111111", "new", "+15552222222"))
        );

        profileAuditListener.consumeProfileUpdate(payload);

        verify(auditLogRepository).save(argThat(entity -> entity.getUserId().equals(100L)));
        verify(auditLogRepository).save(argThat(entity -> "PHONE_CHANGE".equals(entity.getEventType())));
        verify(auditLogRepository).save(argThat(entity -> entity.getTimestamp() != null));
        verify(auditLogRepository).save(argThat(entity -> entity.getChangedFieldsJson().contains("+15552222222")));
    }

    @Test
    @DisplayName("Block 2: The changes object is faithfully serialized into changed_fields_json - [MEANT TO PASS]")
    void testBlock2_changesObjectSerializedToJson() {
        // Requirement Cites: [Story 4.3 - AC3] (old/new values must be captured)
        Map<String, Object> payload = Map.of(
                "userId", "200",
                "eventType", "ADDRESS_CHANGE",
                "changes", Map.of("addressLine1", Map.of("old", "123 Main St", "new", "456 Oak Ave"))
        );

        profileAuditListener.consumeProfileUpdate(payload);

        verify(auditLogRepository).save(argThat(entity -> {
            try {
                Map<?, ?> parsedChanges = objectMapper.readValue(entity.getChangedFieldsJson(), Map.class);
                Map<?, ?> addressChange = (Map<?, ?>) parsedChanges.get("addressLine1");
                return "123 Main St".equals(addressChange.get("old"));
            } catch (Exception e) {
                return false;
            }
        }));
        verify(auditLogRepository).save(argThat(entity -> {
            try {
                Map<?, ?> parsedChanges = objectMapper.readValue(entity.getChangedFieldsJson(), Map.class);
                Map<?, ?> addressChange = (Map<?, ?>) parsedChanges.get("addressLine1");
                return "456 Oak Ave".equals(addressChange.get("new"));
            } catch (Exception e) {
                return false;
            }
        }));
    }

    @Test
    @DisplayName("Block 3: Malformed payload (missing userId) is swallowed, not persisted, does not crash the consumer - [MEANT TO PASS]")
    void testBlock3_malformedPayload_swallowedGracefully() {
        // Requirement Cites: [Story 4.3] (a single bad message must not kill the Kafka consumer thread)
        Map<String, Object> payload = Map.of("eventType", "PHONE_CHANGE"); // no "userId" key at all

        assertThatCode(() -> profileAuditListener.consumeProfileUpdate(payload)).doesNotThrowAnyException();

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Block 4: Distinct events for different users produce distinct, non-cross-contaminated records - [MEANT TO PASS]")
    void testBlock4_multipleEvents_doNotCrossContaminate() {
        // Requirement Cites: [Story 4.3 - AC2, AC3]
        Map<String, Object> firstEvent = Map.of(
                "userId", "300", "eventType", "PHONE_CHANGE",
                "changes", Map.of("phoneNumber", Map.of("old", "111", "new", "222")));
        Map<String, Object> secondEvent = Map.of(
                "userId", "400", "eventType", "ADDRESS_CHANGE",
                "changes", Map.of("addressLine1", Map.of("old", "A St", "new", "B St")));

        profileAuditListener.consumeProfileUpdate(firstEvent);
        profileAuditListener.consumeProfileUpdate(secondEvent);

        verify(auditLogRepository, times(1)).save(argThat(e -> e.getUserId().equals(300L)));
        verify(auditLogRepository, times(1)).save(argThat(e -> "PHONE_CHANGE".equals(e.getEventType())));
        verify(auditLogRepository, times(1)).save(argThat(e -> e.getUserId().equals(400L)));
        verify(auditLogRepository, times(1)).save(argThat(e -> "ADDRESS_CHANGE".equals(e.getEventType())));
    }

    /* ==========================================================
       FINAL BLOCK: AC4 - Structural Immutability
       ========================================================== */

    @Test
    @DisplayName("Final Block: AuditLogEntity exposes no setters - the append-only contract is enforced at the Java layer, not just by convention - [MEANT TO PASS]")
    void testFinalAC_auditLogEntityHasNoSetters() {
        // Requirement Cites: [Story 4.3 - AC4] ("must never update or delete these records")
        // A structural guarantee: even if a future change adds a delete/update code path
        // elsewhere, this test fails the moment AuditLogEntity itself grows a setter,
        // catching a regression against AC4 at compile-adjacent test time rather than in prod.
        Method[] methods = AuditLogEntity.class.getDeclaredMethods();
        boolean hasSetter = Arrays.stream(methods).anyMatch(m -> m.getName().startsWith("set"));

        assertThat(hasSetter)
                .as("AuditLogEntity must remain immutable (getters only) to satisfy FR4.3 AC4")
                .isFalse();
    }
}

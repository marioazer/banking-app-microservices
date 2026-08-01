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

@SpringBootTest
class AuditServiceTestSuite {

    @Autowired
    private ProfileAuditListener profileAuditListener;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuditLogRepository auditLogRepository;

    // checking the basic happy path, a well formed profile update event gets mapped and saved correctly
    // build a payload map by hand the way a real kafka message would deserialize, user id, event type,
    // and a nested changes map showing the old and new phone number
    // call consumeprofileupdate directly like the real kafka listener would
    // verify the saved entity has the right user id, the right event type, a non null timestamp,
    // and that the new phone number actually shows up in the serialized changed fields json
    @Test
    @DisplayName("Block 1: Valid ProfileUpdatedEvent payload is mapped and persisted correctly - [MEANT TO PASS]")
    void testBlock1_validPayload_mapsAndPersistsEntity() {
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

    // digging deeper into the json serialization itself, not just checking a substring like the last test
    // build a payload for an address change with both an old and a new address line
    // call consumeprofileupdate directly
    // then actually parse the saved changed_fields_json back out with the object mapper
    // and confirm both the old address value and the new address value round trip correctly
    @Test
    @DisplayName("Block 2: The changes object is faithfully serialized into changed_fields_json - [MEANT TO PASS]")
    void testBlock2_changesObjectSerializedToJson() {
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

    // defensive test for a broken message that is missing the userId key entirely
    // build a payload map with only an eventType, no userId at all, like a corrupted kafka message
    // call consumeprofileupdate and expect no exception to escape, the consumer thread has to survive
    // then verify the repository never got a save call, a bad message should not create a bad audit row
    @Test
    @DisplayName("Block 3: Malformed payload (missing userId) is swallowed, not persisted, does not crash the consumer - [MEANT TO PASS]")
    void testBlock3_malformedPayload_swallowedGracefully() {
        Map<String, Object> payload = Map.of("eventType", "PHONE_CHANGE"); // no "userId" key at all

        assertThatCode(() -> profileAuditListener.consumeProfileUpdate(payload)).doesNotThrowAnyException();

        verify(auditLogRepository, never()).save(any());
    }

    // making sure processing two different users' events back to back does not mix up their data
    // build two separate payloads, one for user 300 changing a phone number, one for user 400 changing an address
    // call consumeprofileupdate for each event one after the other
    // verify a record saved for user 300 with phone_change, and a separate record for user 400 with address_change
    // using times(1) on each so we know exactly one row went out per user, nothing merged or duplicated
    @Test
    @DisplayName("Block 4: Distinct events for different users produce distinct, non-cross-contaminated records - [MEANT TO PASS]")
    void testBlock4_multipleEvents_doNotCrossContaminate() {
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

    // structural test instead of a behavioral one, using reflection to inspect the entity class itself
    // pull every declared method off of auditlogentity through java reflection
    // check whether any of those method names start with set, meaning a setter exists
    // assert that no setter was found at all
    // this way if someone adds a setter later to fix some unrelated bug, this test catches the regression
    // immediately instead of relying on everyone remembering the append only rule by convention
    @Test
    @DisplayName("Final Block: AuditLogEntity exposes no setters - the append-only contract is enforced at the Java layer, not just by convention - [MEANT TO PASS]")
    void testFinalAC_auditLogEntityHasNoSetters() {
        Method[] methods = AuditLogEntity.class.getDeclaredMethods();
        boolean hasSetter = Arrays.stream(methods).anyMatch(m -> m.getName().startsWith("set"));

        assertThat(hasSetter)
                .as("AuditLogEntity must remain immutable (getters only) to satisfy FR4.3 AC4")
                .isFalse();
    }
}

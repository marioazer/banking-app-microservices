package com.example.profileservice;

import com.example.profileservice.dto.AlertPreferencesDto;
import com.example.profileservice.dto.UpdateContactInfoRequestDto;
import com.example.profileservice.model.KycOverrideAuditLog;
import com.example.profileservice.model.KycStatus;
import com.example.profileservice.model.UserProfile;
import com.example.profileservice.repository.KycOverrideAuditLogRepository;
import com.example.profileservice.repository.PreferenceRepository;
import com.example.profileservice.repository.UserProfileRepository;
import com.example.profileservice.service.ProfileManagementService;
import com.example.profileservice.service.ProfileManagementService.KycStatusUpdatedEvent;
import com.example.profileservice.service.ProfileManagementService.ProfileUpdatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:profiletestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "kyc.vendor.webhook.secret=SuperSecretVendorKey123!"
})
class ProfileServiceTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfileManagementService profileManagementService;

    @MockBean
    private UserProfileRepository userProfileRepository;

    @MockBean
    private KycOverrideAuditLogRepository auditLogRepository;

    @MockBean
    private PreferenceRepository preferenceRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private UserProfile mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new UserProfile();
        mockUser.setId(100L);
        mockUser.setPhoneNumber("+14155552671");
        mockUser.setAddressLine1("123 Financial Way");
        mockUser.setCity("New York");
        mockUser.setState("NY");
        mockUser.setZipCode("10001");
        mockUser.setKycStatus(KycStatus.PENDING_VERIFICATION);
    }

    /* ==========================================================
       USER STORY 3.1: Initial "Pending" State Enforcement
       ========================================================== */

    @Test
    @DisplayName("Block 1: Query KYC Status Returns Error for Non-Existent User - [MEANT TO FAIL]")
    void testBlock1_GetKycStatus_UserNotFound_ReturnsError() throws Exception {
        given(userProfileRepository.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/profiles/999/kyc-status"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Query Active KYC Status - [MEANT TO PASS]")
    void testFinalAC_GetKycStatus_ReturnsCurrentPendingState() throws Exception {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        mockMvc.perform(get("/api/v1/profiles/100/kyc-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    /* ==========================================================
       USER STORY 3.2: Async Webhook for KYC Approval
       ========================================================== */

    @Test
    @DisplayName("Block 1: Webhook Rejects Request Missing HMAC Signature Header - [MEANT TO FAIL]")
    void testBlock1_Webhook_MissingSignature_Returns401() throws Exception {
        String payloadJson = objectMapper.writeValueAsString(Map.of("userId", "100", "status", "APPROVED"));

        mockMvc.perform(post("/api/v1/webhooks/kyc-update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Missing X-Signature header"));
    }

    @Test
    @DisplayName("Block 2: Webhook Rejects Invalid HMAC Signature - [MEANT TO FAIL]")
    void testBlock2_Webhook_InvalidSignature_Returns401() throws Exception {
        String payloadJson = objectMapper.writeValueAsString(Map.of("userId", "100", "status", "APPROVED"));

        mockMvc.perform(post("/api/v1/webhooks/kyc-update")
                .header("X-Signature", "InvalidSignatureValue123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid webhook signature"));
    }

    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Valid Webhook Updates KYC to APPROVED - [MEANT TO PASS]")
    void testFinalAC_Webhook_ValidSignature_UpdatesKycToApproved() throws Exception {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        String payloadJson = objectMapper.writeValueAsString(Map.of("userId", "100", "status", "APPROVED"));
        String validHmac = calculateHmac(payloadJson, "SuperSecretVendorKey123!");

        mockMvc.perform(post("/api/v1/webhooks/kyc-update")
                .header("X-Signature", validHmac)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadJson))
                .andExpect(status().isOk());

        assertThat(mockUser.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        verify(userProfileRepository).save(mockUser);
    }

    /* ==========================================================
       USER STORY 3.3: Broadcasting the Status Change (Kafka)
       ========================================================== */

    @Test
    @DisplayName("Block 1: Process Webhook Status Unchanged Does Not Broadcast Kafka Event - [MEANT TO PASS]")
    void testBlock1_ProcessWebhook_StatusUnchanged_IdempotentNoKafkaEvent() {
        mockUser.setKycStatus(KycStatus.APPROVED);
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        profileManagementService.processKycWebhook(100L, KycStatus.APPROVED);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - KYC Status Transition Broadcasts Kafka Event - [MEANT TO PASS]")
    void testFinalAC_ProcessWebhook_StatusChanged_PublishesKycStatusUpdatedEvent() {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        profileManagementService.processKycWebhook(100L, KycStatus.APPROVED);

        verify(userProfileRepository).save(mockUser);
        verify(kafkaTemplate).send(eq("kyc-events"), eq("100"), any(KycStatusUpdatedEvent.class));
    }

    /* ==========================================================
       USER STORY 3.4: Manual Admin Override
       ========================================================== */

    @Test
    @DisplayName("Block 1: Admin Override Fails When Reason Text is Blank - [MEANT TO FAIL]")
    @WithMockUser(username = "500", roles = {"COMPLIANCE_OFFICER"})
    void testBlock1_AdminOverride_MissingReason_ReturnsBadRequest() throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of("status", "APPROVED", "reason", "  "));

        mockMvc.perform(patch("/api/v1/admin/profiles/100/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Override reason is mandatory"));
    }

    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Admin Override Updates DB, Audits, and Broadcasts Kafka Event - [MEANT TO PASS]")
    @WithMockUser(username = "500", roles = {"COMPLIANCE_OFFICER"})
    void testFinalAC_AdminOverride_ValidRequest_SavesAuditLogAndPublishesKafka() throws Exception {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        String requestJson = objectMapper.writeValueAsString(Map.of("status", "APPROVED", "reason", "Manual verification of physical passport."));

        mockMvc.perform(patch("/api/v1/admin/profiles/100/kyc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("KYC status manually overridden by compliance officer"));

        verify(auditLogRepository).save(any(KycOverrideAuditLog.class));
        verify(userProfileRepository).save(mockUser);
        verify(kafkaTemplate).send(eq("kyc-events"), eq("100"), any(KycStatusUpdatedEvent.class));
    }

    /* ==========================================================
       USER STORY 4.1: Secure Profile Update API
       ========================================================== */

    @Test
    @DisplayName("Block 1: Contact Info Update Rejects Invalid International Phone Format - [MEANT TO FAIL]")
    @WithMockUser(username = "100")
    void testBlock1_UpdateContactInfo_InvalidPhoneNumber_ReturnsBadRequest() throws Exception {
        UpdateContactInfoRequestDto dto = new UpdateContactInfoRequestDto();
        dto.setPhoneNumber("INVALID_PHONE_123");
        dto.setAddressLine1("123 Main St");
        dto.setCity("Boston");
        dto.setState("MA");
        dto.setZipCode("02108");

        mockMvc.perform(put("/api/v1/profiles/me/contact-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Valid Contact Info Update Persists to Database - [MEANT TO PASS]")
    @WithMockUser(username = "100")
    void testFinalAC_UpdateContactInfo_ValidDto_SavesUserProfile() throws Exception {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        UpdateContactInfoRequestDto dto = new UpdateContactInfoRequestDto();
        dto.setPhoneNumber("+12025550143");
        dto.setAddressLine1("456 Innovation Blvd");
        dto.setCity("San Jose");
        dto.setState("CA");
        dto.setZipCode("95110");

        mockMvc.perform(put("/api/v1/profiles/me/contact-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"));

        assertThat(mockUser.getPhoneNumber()).isEqualTo("+12025550143");
        assertThat(mockUser.getAddressLine1()).isEqualTo("456 Innovation Blvd");
        verify(userProfileRepository).save(mockUser);
    }

    /* ==========================================================
       USER STORY 4.2: Broadcasting the Profile Update Event
       ========================================================== */

    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Profile Update Publishes ProfileUpdatedEvent to Kafka - [MEANT TO PASS]")
    void testFinalAC_UpdateContactInfo_PublishesProfileUpdatedEventToKafka() {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        UpdateContactInfoRequestDto dto = new UpdateContactInfoRequestDto();
        dto.setPhoneNumber("+12025550143");
        dto.setAddressLine1("789 Security Way");
        dto.setCity("Austin");
        dto.setState("TX");
        dto.setZipCode("73301");

        profileManagementService.updateContactInfo(100L, dto);

        verify(kafkaTemplate).send(eq("profile-events"), eq("100"), any(ProfileUpdatedEvent.class));
    }

    /* ==========================================================
       USER STORY 9.1: User Preference Management (Alert Threshold)
       ========================================================== */

    @Test
    @DisplayName("Block: Update alert threshold with a valid, complete payload persists the preference - [MEANT TO PASS]")
    @WithMockUser(username = "100", authorities = {"SCOPE_FULL_AUTH"})
    void testBlock_UpdateAlertThreshold_ValidPayload_Persists() throws Exception {
        // Requirement Cites: [Story 9.1 - AC1, AC2]
        given(preferenceRepository.findByUserId(100L)).willReturn(Optional.empty());

        AlertPreferencesDto dto = new AlertPreferencesDto(new BigDecimal("250.00"), true, "America/New_York");

        mockMvc.perform(put("/api/v1/profile/alerts/threshold")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(preferenceRepository).save(argThat(entity ->
                entity.getUserId().equals(100L) && entity.getAlertThresholdAmount().compareTo(new BigDecimal("250.00")) == 0));
    }

    @Test
    @DisplayName("Block: Invalid IANA timezone identifier is rejected - [MEANT TO FAIL]")
    @WithMockUser(username = "100", authorities = {"SCOPE_FULL_AUTH"})
    void testBlock_UpdateAlertThreshold_InvalidTimezone_ReturnsBadRequest() throws Exception {
        // Requirement Cites: [Story 10.1 - AC2] (timezone validated as a real IANA identifier)
        AlertPreferencesDto dto = new AlertPreferencesDto(new BigDecimal("100.00"), true, "Not/A_Real_Zone");

        mockMvc.perform(put("/api/v1/profile/alerts/threshold")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Block [GAP]: Setting only the threshold still requires an unrelated daily-summary/timezone payload - [MEANT TO PASS, DOCUMENTS AN API-DESIGN GAP]")
    @WithMockUser(username = "100")
    void testBlock_Gap_ThresholdOnlyUpdate_StillRequiresDailySummaryFields() throws Exception {
        // Requirement Cites: [Story 9.1 - AC1] ("expose an endpoint... to set alert_threshold_amount")
        // Both /threshold and /daily-summary share one AlertPreferencesDto with all three fields
        // @NotNull/@NotBlank, so a client that only wants to change the alert threshold is forced
        // to also resend a valid dailySummaryEnabled + timezone (and vice versa for the
        // daily-summary endpoint), even though each story only asks for its own concern.
        // This asserts the CURRENT (coupled) behavior, not the AC-implied independent one.
        String thresholdOnlyPayload = "{\"alertThresholdAmount\": 250.00}";

        mockMvc.perform(put("/api/v1/profile/alerts/threshold")
                .contentType(MediaType.APPLICATION_JSON)
                .content(thresholdOnlyPayload))
                .andExpect(status().isBadRequest());
    }

    /* ==========================================================
       USER STORY 10.1: Opt-in Preferences (Daily Balance Summary)
       ========================================================== */

    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Daily summary opt-in persists enabled flag and timezone - [MEANT TO PASS]")
    @WithMockUser(username = "100", authorities = {"SCOPE_FULL_AUTH"})
    void testFinalAC_UpdateDailySummarySettings_ValidPayload_Persists() throws Exception {
        // Requirement Cites: [Story 10.1 - AC1, AC2, AC3]
        given(preferenceRepository.findByUserId(100L)).willReturn(Optional.empty());

        AlertPreferencesDto dto = new AlertPreferencesDto(new BigDecimal("100.00"), true, "Europe/London");

        mockMvc.perform(put("/api/v1/profile/alerts/daily-summary")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").doesNotExist()); // endpoint returns a plain string body, not JSON

        verify(preferenceRepository).save(argThat(entity ->
                entity.getUserId().equals(100L)
                        && Boolean.TRUE.equals(entity.getDailySummaryEnabled())
                        && "Europe/London".equals(entity.getTimezone())));
    }

    @Test
    @DisplayName("Block: Pre-Auth token is denied on alert preference endpoints - [MEANT TO FAIL]")
    void testBlock_AlertPreferences_Unauthenticated_Denied() throws Exception {
        // Requirement Cites: [class-level @PreAuthorize("hasAuthority('SCOPE_FULL_AUTH')") on PreferenceController]
        AlertPreferencesDto dto = new AlertPreferencesDto(new BigDecimal("100.00"), true, "UTC");

        mockMvc.perform(put("/api/v1/profile/alerts/threshold")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().is4xxClientError());
    }

    /* ==========================================================
       HELPER UTILITIES
       ========================================================== */

    private String calculateHmac(String data, String key) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }
}
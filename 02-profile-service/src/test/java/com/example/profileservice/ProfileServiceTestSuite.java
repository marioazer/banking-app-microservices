package com.example.profileservice;

import com.example.profileservice.dto.UpdateAlertThresholdRequestDto;
import com.example.profileservice.dto.UpdateContactInfoRequestDto;
import com.example.profileservice.dto.UpdateDailySummaryRequestDto;
import com.example.profileservice.model.KycOverrideAuditLog;
import com.example.profileservice.model.KycStatus;
import com.example.profileservice.model.UserPreferenceEntity;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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

    // shared fixture that runs before every test, builds one baseline user profile to reuse
    // I give it a real id, phone number and address so downstream code has real looking data to work with
    // kyc status starts out pending since that is the default state a brand new profile should be in
    // individual tests below are free to mutate this mockUser further before their own assertions run
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

    // checking what happens when someone asks for the kyc status of a user id that does not exist
    // stub the repository so looking up id 999 comes back completely empty
    // hit the kyc-status endpoint for that same missing id
    // right now that surfaces as a 500 since nothing catches the missing user case gracefully yet
    @Test
    @DisplayName("Block 1: Query KYC Status Returns Error for Non-Existent User - [MEANT TO FAIL]")
    void testBlock1_GetKycStatus_UserNotFound_ReturnsError() throws Exception {
        given(userProfileRepository.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/profiles/999/kyc-status"))
                .andExpect(status().isInternalServerError());
    }

    // happy path check that the kyc-status endpoint reports back whatever state the user is actually in
    // stub the repository so looking up user 100 returns our fixture user from setUp
    // hit the kyc-status endpoint for that user
    // expect a 200 ok and the status field to say pending_verification, matching the fixture
    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Query Active KYC Status - [MEANT TO PASS]")
    void testFinalAC_GetKycStatus_ReturnsCurrentPendingState() throws Exception {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        mockMvc.perform(get("/api/v1/profiles/100/kyc-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    // making sure the kyc webhook refuses a request with no signature header at all
    // build a normal looking webhook payload for a user being approved
    // post it straight to the webhook endpoint without adding the x-signature header
    // it should be rejected with 401 unauthorized
    // and the error message should call out specifically that the signature header is missing
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

    // similar to the last test but this time a signature header is present, it is just wrong
    // build the same kind of webhook payload as before
    // post it along with a made up x-signature value that was never actually computed with the real key
    // it should still be rejected with 401 unauthorized
    // and the error message this time should say the signature itself is invalid, not missing
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

    // happy path for the webhook, this time the signature is computed correctly
    // stub the repository to return our fixture user so there is something to update
    // build the payload then run it through the same hmac sha256 algorithm the real vendor would use
    // post the payload along with that correctly computed signature header
    // expect a plain 200 ok back
    // then confirm the user's kyc status actually flipped to approved and got saved
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

    // checking that re-processing the same status does not spam out a duplicate kafka event
    // set the fixture user's kyc status to approved already, like the webhook already ran once before
    // call processkycwebhook again with that exact same approved status
    // since nothing actually changed, kafkatemplate.send should never get called at all
    @Test
    @DisplayName("Block 1: Process Webhook Status Unchanged Does Not Broadcast Kafka Event - [MEANT TO PASS]")
    void testBlock1_ProcessWebhook_StatusUnchanged_IdempotentNoKafkaEvent() {
        mockUser.setKycStatus(KycStatus.APPROVED);
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        profileManagementService.processKycWebhook(100L, KycStatus.APPROVED);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    // this time the status is actually changing so a kafka event should go out
    // fixture user starts out pending from setUp, so approving it is a real transition
    // call processkycwebhook with approved as the new status
    // verify the user profile got saved with the new status
    // and verify a kycstatusupdatedevent was published to the kyc-events topic keyed by user id
    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - KYC Status Transition Broadcasts Kafka Event - [MEANT TO PASS]")
    void testFinalAC_ProcessWebhook_StatusChanged_PublishesKycStatusUpdatedEvent() {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        profileManagementService.processKycWebhook(100L, KycStatus.APPROVED);

        verify(userProfileRepository).save(mockUser);
        verify(kafkaTemplate).send(eq("kyc-events"), eq("100"), any(KycStatusUpdatedEvent.class));
    }

    // making sure a compliance officer cannot manually override kyc status without giving a real reason
    // withmockuser here simulates a logged in compliance officer, user id 500 with the right role
    // build a request where the reason field is just blank spaces, not an actual explanation
    // patch it to the admin kyc override endpoint
    // expect a 400 bad request with an error saying the override reason is mandatory
    @Test
    @DisplayName("Block 1: Admin Override Fails When Reason Text is Blank - [MEANT TO FAIL]")
    void testBlock1_AdminOverride_MissingReason_ReturnsBadRequest() throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of("status", "APPROVED", "reason", "  "));

        mockMvc.perform(patch("/api/v1/admin/profiles/100/kyc")
                .with(jwt().jwt(j -> j.claim("userId", 500L)).authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Override reason is mandatory"));
    }

    // happy path for a compliance officer manually overriding a user's kyc status with a real reason
    // stub the repository to return the fixture user so there is something to actually update
    // this time give a real reason string, manual verification of a physical passport
    // patch that request to the admin override endpoint as the mocked compliance officer
    // expect 200 ok with a message confirming the manual override happened
    // then confirm three separate side effects, an audit log entry got saved, the profile got saved,
    // and a kafka event went out on kyc-events so other services hear about the change
    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Admin Override Updates DB, Audits, and Broadcasts Kafka Event - [MEANT TO PASS]")
    void testFinalAC_AdminOverride_ValidRequest_SavesAuditLogAndPublishesKafka() throws Exception {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        String requestJson = objectMapper.writeValueAsString(Map.of("status", "APPROVED", "reason", "Manual verification of physical passport."));

        mockMvc.perform(patch("/api/v1/admin/profiles/100/kyc")
                .with(jwt().jwt(j -> j.claim("userId", 500L)).authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("KYC status manually overridden by compliance officer"));

        verify(auditLogRepository).save(any(KycOverrideAuditLog.class));
        verify(userProfileRepository).save(mockUser);
        verify(kafkaTemplate).send(eq("kyc-events"), eq("100"), any(KycStatusUpdatedEvent.class));
    }

    // checking validation on the contact info update endpoint rejects a bad phone number format
    // withmockuser simulates the logged in owner of profile 100 making this request themselves
    // build a dto with an obviously invalid phone value alongside otherwise normal address fields
    // put that dto to the contact-info endpoint
    // expect a 400 bad request since the phone number does not match the expected international format
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

    // happy path for updating contact info with an actually valid dto
    // stub the repository so user 100 resolves to our fixture profile
    // build a dto with a properly formatted phone number and a new address
    // put that to the contact-info endpoint as the same logged in user
    // expect 200 ok with a profile updated successfully message
    // then confirm the in memory mockUser object itself picked up the new phone and address
    // and that the repository actually got told to save it
    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Valid Contact Info Update Persists to Database - [MEANT TO PASS]")
    void testFinalAC_UpdateContactInfo_ValidDto_SavesUserProfile() throws Exception {
        given(userProfileRepository.findById(100L)).willReturn(Optional.of(mockUser));

        UpdateContactInfoRequestDto dto = new UpdateContactInfoRequestDto();
        dto.setPhoneNumber("+12025550143");
        dto.setAddressLine1("456 Innovation Blvd");
        dto.setCity("San Jose");
        dto.setState("CA");
        dto.setZipCode("95110");

        mockMvc.perform(put("/api/v1/profiles/me/contact-info")
                .with(jwt().jwt(j -> j.claim("userId", 100L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"));

        assertThat(mockUser.getPhoneNumber()).isEqualTo("+12025550143");
        assertThat(mockUser.getAddressLine1()).isEqualTo("456 Innovation Blvd");
        verify(userProfileRepository).save(mockUser);
    }

    // this one calls the service layer directly instead of going through mockmvc
    // stub the repository so user 100 resolves to the fixture profile
    // build a contact info dto with new values and call updatecontactinfo on the service
    // afterward verify a profileupdatedevent got published to the profile-events topic keyed by user id
    // this is the event the audit service and notification service both end up listening for downstream
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

    // checking the basic happy path for setting an alert threshold for the first time
    // withmockuser here includes the scope_full_auth authority since this endpoint requires a full session
    // stub the preference repository so this user has no existing preference row yet
    // put a simple dto with just a dollar amount to the threshold endpoint
    // expect 200 ok back
    // then confirm the saved entity has the right user id and the right threshold amount attached
    @Test
    @DisplayName("Block: Update alert threshold with a valid payload persists the preference - [MEANT TO PASS]")
    void testBlock_UpdateAlertThreshold_ValidPayload_Persists() throws Exception {
        given(preferenceRepository.findByUserId(100L)).willReturn(Optional.empty());

        UpdateAlertThresholdRequestDto dto = new UpdateAlertThresholdRequestDto(new BigDecimal("250.00"));

        mockMvc.perform(put("/api/v1/profile/alerts/threshold")
                .with(jwt().jwt(j -> j.claim("scope", "FULL_AUTH").claim("userId", 100L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(preferenceRepository).save(argThat(entity -> entity.getUserId().equals(100L)));
        verify(preferenceRepository).save(argThat(entity -> entity.getAlertThresholdAmount().compareTo(new BigDecimal("250.00")) == 0));
    }

    // confirming the threshold endpoint no longer needs the daily summary fields tagging along
    // stub the repository so this user has no existing preference row saved yet
    // send a raw json payload with only the alertthresholdamount key and nothing else
    // expect 200 ok since the dto for this endpoint is now split from the daily summary one
    // then check that a brand new user still gets sane defaults for the untouched fields,
    // a hundred dollar default is not what we check here, just that summary is off and timezone is utc
    @Test
    @DisplayName("Block (fixed): Threshold-only payload is sufficient - daily-summary fields are no longer required - [MEANT TO PASS]")
    void testBlock_UpdateAlertThreshold_ThresholdOnlyPayload_NoLongerRequiresDailySummaryFields() throws Exception {
        // /threshold and /daily-summary now use separate DTOs (UpdateAlertThresholdRequestDto /
        // UpdateDailySummaryRequestDto), so a client updating only the threshold no longer has to
        // resend an unrelated dailySummaryEnabled/timezone. Replaces the old test that documented
        // the field coupling as a gap.
        given(preferenceRepository.findByUserId(100L)).willReturn(Optional.empty());
        String thresholdOnlyPayload = "{\"alertThresholdAmount\": 250.00}";

        mockMvc.perform(put("/api/v1/profile/alerts/threshold")
                .with(jwt().jwt(j -> j.claim("scope", "FULL_AUTH").claim("userId", 100L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(thresholdOnlyPayload))
                .andExpect(status().isOk());

        // New users get the same defaults as the V3 migration's column defaults for the
        // untouched fields (100.00 / false / UTC), not null.
        verify(preferenceRepository).save(argThat(entity -> entity.getAlertThresholdAmount().compareTo(new BigDecimal("250.00")) == 0));
        verify(preferenceRepository).save(argThat(entity -> Boolean.FALSE.equals(entity.getDailySummaryEnabled())));
        verify(preferenceRepository).save(argThat(entity -> "UTC".equals(entity.getTimezone())));
    }

    // making sure updating just the threshold does not clobber daily summary settings someone already set
    // build an existing preference entity by hand with daily summary already turned on and a real timezone
    // stub the repository to return that existing row when this user is looked up
    // put a new threshold value to the threshold endpoint
    // expect 200 ok
    // then confirm the new threshold saved but the daily summary flag and timezone are exactly as before
    @Test
    @DisplayName("Block: Updating the threshold leaves an existing daily-summary preference untouched - [MEANT TO PASS]")
    void testBlock_UpdateAlertThreshold_PreservesExistingDailySummarySettings() throws Exception {
        UserPreferenceEntity existing = new UserPreferenceEntity();
        existing.setUserId(100L);
        existing.setAlertThresholdAmount(new BigDecimal("100.00"));
        existing.setDailySummaryEnabled(true);
        existing.setTimezone("Europe/London");
        given(preferenceRepository.findByUserId(100L)).willReturn(Optional.of(existing));

        UpdateAlertThresholdRequestDto dto = new UpdateAlertThresholdRequestDto(new BigDecimal("300.00"));

        mockMvc.perform(put("/api/v1/profile/alerts/threshold")
                .with(jwt().jwt(j -> j.claim("scope", "FULL_AUTH").claim("userId", 100L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(preferenceRepository).save(argThat(entity -> entity.getAlertThresholdAmount().compareTo(new BigDecimal("300.00")) == 0));
        verify(preferenceRepository).save(argThat(entity -> Boolean.TRUE.equals(entity.getDailySummaryEnabled())));
        verify(preferenceRepository).save(argThat(entity -> "Europe/London".equals(entity.getTimezone())));
    }

    // checking that a made up timezone string gets rejected instead of silently accepted
    // build a daily summary dto with enabled true but a timezone value that is not a real iana zone
    // put that to the daily-summary endpoint
    // expect a 400 bad request since the timezone has to be a real identifier like america/new_york
    @Test
    @DisplayName("Block: Invalid IANA timezone identifier is rejected - [MEANT TO FAIL]")
    void testBlock_UpdateDailySummary_InvalidTimezone_ReturnsBadRequest() throws Exception {
        UpdateDailySummaryRequestDto dto = new UpdateDailySummaryRequestDto(true, "Not/A_Real_Zone");

        mockMvc.perform(put("/api/v1/profile/alerts/daily-summary")
                .with(jwt().jwt(j -> j.claim("scope", "FULL_AUTH").claim("userId", 100L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // happy path for opting into the daily balance summary email with a real timezone
    // stub the repository so this user has no existing preference row yet
    // build a dto with enabled true and a real iana timezone, europe/london
    // put that to the daily-summary endpoint
    // expect 200 ok, and this endpoint intentionally returns a plain string not json, so we check
    // that the jsonpath for a message field simply does not exist rather than expecting real json
    // last confirm the saved entity has the right user id, enabled flag and timezone
    @Test
    @DisplayName("Final Block: Acceptance Criteria Verification - Daily summary opt-in persists enabled flag and timezone - [MEANT TO PASS]")
    void testFinalAC_UpdateDailySummarySettings_ValidPayload_Persists() throws Exception {
        given(preferenceRepository.findByUserId(100L)).willReturn(Optional.empty());

        UpdateDailySummaryRequestDto dto = new UpdateDailySummaryRequestDto(true, "Europe/London");

        mockMvc.perform(put("/api/v1/profile/alerts/daily-summary")
                .with(jwt().jwt(j -> j.claim("scope", "FULL_AUTH").claim("userId", 100L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").doesNotExist()); // endpoint returns a plain string body, not JSON

        verify(preferenceRepository).save(argThat(entity -> entity.getUserId().equals(100L)));
        verify(preferenceRepository).save(argThat(entity -> Boolean.TRUE.equals(entity.getDailySummaryEnabled())));
        verify(preferenceRepository).save(argThat(entity -> "Europe/London".equals(entity.getTimezone())));
    }

    // the mirror image of the earlier test, this time daily summary changes should not touch the threshold
    // build an existing preference entity by hand with a real threshold value already set
    // stub the repository to return that existing row for this user
    // put new daily summary settings, enabled true with a different timezone, asia/tokyo
    // expect 200 ok
    // then confirm the threshold amount saved is untouched while summary and timezone did update
    @Test
    @DisplayName("Block: Updating daily-summary settings leaves an existing alert threshold untouched - [MEANT TO PASS]")
    void testBlock_UpdateDailySummary_PreservesExistingAlertThreshold() throws Exception {
        UserPreferenceEntity existing = new UserPreferenceEntity();
        existing.setUserId(100L);
        existing.setAlertThresholdAmount(new BigDecimal("500.00"));
        existing.setDailySummaryEnabled(false);
        existing.setTimezone("UTC");
        given(preferenceRepository.findByUserId(100L)).willReturn(Optional.of(existing));

        UpdateDailySummaryRequestDto dto = new UpdateDailySummaryRequestDto(true, "Asia/Tokyo");

        mockMvc.perform(put("/api/v1/profile/alerts/daily-summary")
                .with(jwt().jwt(j -> j.claim("scope", "FULL_AUTH").claim("userId", 100L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(preferenceRepository).save(argThat(entity -> entity.getAlertThresholdAmount().compareTo(new BigDecimal("500.00")) == 0));
        verify(preferenceRepository).save(argThat(entity -> Boolean.TRUE.equals(entity.getDailySummaryEnabled())));
        verify(preferenceRepository).save(argThat(entity -> "Asia/Tokyo".equals(entity.getTimezone())));
    }

    // checking that hitting the alert preference endpoints without a proper session is rejected
    // notice there is no withmockuser annotation on this one at all, unlike the other preference tests
    // build a normal looking threshold dto anyway
    // put it to the threshold endpoint with no authentication attached
    // expect some kind of 4xx client error back, matching the class level preauthorize check on the controller
    @Test
    @DisplayName("Block: Pre-Auth token is denied on alert preference endpoints - [MEANT TO FAIL]")
    void testBlock_AlertPreferences_Unauthenticated_Denied() throws Exception {
        UpdateAlertThresholdRequestDto dto = new UpdateAlertThresholdRequestDto(new BigDecimal("100.00"));

        mockMvc.perform(put("/api/v1/profile/alerts/threshold")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().is4xxClientError());
    }

    // happy path: notification-service asks for a user's preferences and gets back exactly
    // what's stored, no authentication required since this is an internal service-to-service call
    @Test
    @DisplayName("Block: GET alerts/{userId} returns the stored preferences unauthenticated - [MEANT TO PASS]")
    void testBlock_GetPreferences_ExistingRow_ReturnsStoredValues() throws Exception {
        UserPreferenceEntity existing = new UserPreferenceEntity();
        existing.setUserId(100L);
        existing.setAlertThresholdAmount(new BigDecimal("250.00"));
        existing.setDailySummaryEnabled(true);
        existing.setTimezone("Europe/London");
        given(preferenceRepository.findByUserId(100L)).willReturn(Optional.of(existing));

        mockMvc.perform(get("/api/v1/profile/alerts/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(100))
                .andExpect(jsonPath("$.alertThresholdAmount").value(250.00))
                .andExpect(jsonPath("$.dailySummaryEnabled").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/London"));
    }

    // a user with no preference row yet should still get a coherent response (the documented
    // defaults), not a 404 - and this lookup must never persist anything on its own
    @Test
    @DisplayName("Block: GET alerts/{userId} returns documented defaults for a user with no row yet - [MEANT TO PASS]")
    void testBlock_GetPreferences_NoRow_ReturnsDefaultsWithoutPersisting() throws Exception {
        given(preferenceRepository.findByUserId(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/profile/alerts/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertThresholdAmount").value(100.00))
                .andExpect(jsonPath("$.dailySummaryEnabled").value(false))
                .andExpect(jsonPath("$.timezone").value("UTC"));

        verify(preferenceRepository, never()).save(any());
    }

    // the daily-summary batch job asks for every user opted in for one specific timezone -
    // confirm the response shape and that it's also reachable with no authentication
    @Test
    @DisplayName("Block: GET daily-summary-users filters by timezone and opt-in flag - [MEANT TO PASS]")
    void testBlock_GetUsersForDailySummary_ReturnsOptedInUsersForTimezone() throws Exception {
        UserPreferenceEntity optedIn = new UserPreferenceEntity();
        optedIn.setUserId(200L);
        optedIn.setAlertThresholdAmount(new BigDecimal("100.00"));
        optedIn.setDailySummaryEnabled(true);
        optedIn.setTimezone("America/New_York");
        given(preferenceRepository.findByDailySummaryEnabledTrueAndTimezone("America/New_York"))
                .willReturn(List.of(optedIn));

        mockMvc.perform(get("/api/v1/profile/alerts/daily-summary-users").param("timezone", "America/New_York"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(200));
    }

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
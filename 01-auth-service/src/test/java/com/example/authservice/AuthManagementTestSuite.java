package com.example.authservice;

import com.example.authservice.model.BlacklistedToken;
import com.example.authservice.model.RecognizedDevice;
import com.example.authservice.model.RefreshToken;
import com.example.authservice.model.TwoFactorCode;
import com.example.authservice.model.User;
import com.example.authservice.repository.BlacklistedTokenRepository;
import com.example.authservice.repository.RecognizedDeviceRepository;
import com.example.authservice.repository.RefreshTokenRepository;
import com.example.authservice.repository.TwoFactorCodeRepository;
import com.example.authservice.security.TokenType;
import com.example.authservice.service.AuthSecurityService;
import com.example.authservice.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class AuthManagementTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthSecurityService authSecurityService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private AuthenticationProvider authenticationProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private RecognizedDeviceRepository deviceRepository;

    @MockBean
    private TwoFactorCodeRepository twoFactorCodeRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = mock(User.class);
        given(mockUser.getUsername()).willReturn("johndoe");
        given(mockUser.getId()).willReturn(1L);
        given(mockUser.getPhoneNumber()).willReturn("+15551234567");

        given(userDetailsService.loadUserByUsername("johndoe")).willReturn(mockUser);
    }

    /* ==========================================================
       USER STORY 1.1: Device Fingerprinting & Recognition
       ========================================================== */

    @Test
    @DisplayName("Block 1: Device Repository Query Returns Empty for Unrecognized Cookie - [MEANT TO FAIL]")
    void testBlock1_UnrecognizedDevice_ReturnsFalse() {
        given(deviceRepository.findByUserIdAndDeviceHash(eq(1L), any(String.class)))
                .willReturn(Optional.empty());

        boolean recognized = authSecurityService.isDeviceRecognized(1L, "invalid-device-cookie-123");

        assertThat(recognized).isFalse();
        verify(deviceRepository).findByUserIdAndDeviceHash(eq(1L), any(String.class));
    }

    @Test
    @DisplayName("Block 2: Device Repository Query Returns Match for Valid Cookie - [MEANT TO PASS]")
    void testBlock2_RecognizedDevice_ReturnsTrue() {
        given(deviceRepository.findByUserIdAndDeviceHash(eq(1L), any(String.class)))
                .willReturn(Optional.of(new RecognizedDevice(1L, "hashed-cookie")));

        boolean recognized = authSecurityService.isDeviceRecognized(1L, "valid-device-cookie-123");

        assertThat(recognized).isTrue();
        verify(deviceRepository).findByUserIdAndDeviceHash(eq(1L), any(String.class));
    }

    @Test
    @DisplayName("Final Block: E2E Login Unrecognized Device Triggers 2FA & Issues Pre-Auth Token - [MEANT TO PASS]")
    void testFinalAC_LoginUnrecognizedDevice_Requires2FA() throws Exception {
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(mockUser);
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(authentication);
        given(deviceRepository.findByUserIdAndDeviceHash(eq(1L), any())).willReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"johndoe\",\"password\":\"SecurePass123!\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("2FA_REQUIRED"))
                .andExpect(jsonPath("$.pre_auth_token").exists());
    }

    /* ==========================================================
       USER STORY 1.3: SMS 2FA via Kafka Integration
       ========================================================== */

    @Test
    @DisplayName("Block 1: Trigger SMS 2FA Clears Old Codes via Repository and Publishes to Kafka - [MEANT TO PASS]")
    void testBlock1_TriggerSms2fa_DeletesOldCodeAndPublishesKafka() {
        authSecurityService.triggerSms2fa(1L, "+15551234567");

        verify(twoFactorCodeRepository).deleteByUserId(1L);
        verify(twoFactorCodeRepository).save(any(TwoFactorCode.class));
        verify(kafkaTemplate).send(eq("notification-events"), any(String.class));
    }

    @Test
    @DisplayName("Final Block: E2E Verify SMS 2FA Success Returns Session JWT & Device Cookie - [MEANT TO PASS]")
    void testFinalAC_VerifySms2fa_Success() throws Exception {
        String preAuthToken = jwtService.generateToken(mockUser, TokenType.PRE_AUTH);
        
        TwoFactorCode validCode = new TwoFactorCode(1L, hashString("123456"));
        given(twoFactorCodeRepository.findByUserId(1L)).willReturn(Optional.of(validCode));

        mockMvc.perform(post("/api/v1/auth/verify-2fa/sms")
                .header("Authorization", "Bearer " + preAuthToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(header().exists("Set-Cookie"));

        verify(twoFactorCodeRepository).delete(validCode);
    }

    /* ==========================================================
       USER STORY 1.4 & 2.1: JWT Issuance & Security Boundaries
       ========================================================== */

    @Test
    @DisplayName("Block 1: PRE_AUTH Token Restricted from Accessing Protected Endpoints - [MEANT TO FAIL]")
    void testBlock1_PreAuthToken_DeniedProtectedAccess() throws Exception {
        String preAuthToken = jwtService.generateToken(mockUser, TokenType.PRE_AUTH);

        mockMvc.perform(get("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + preAuthToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Partial authentication. 2FA verification required."));
    }

    @Test
    @DisplayName("Final Block: FULL_AUTH Token Expiration and Authorization Check - [MEANT TO PASS]")
    void testFinalAC_FullAuthToken_AccessAllowed() throws Exception {
        String fullAuthToken = jwtService.generateToken(mockUser, TokenType.FULL_AUTH);

        assertThat(jwtService.extractTokenType(fullAuthToken)).isEqualTo(TokenType.FULL_AUTH);
        assertThat(jwtService.extractUsername(fullAuthToken)).isEqualTo("johndoe");

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + fullAuthToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    /* ==========================================================
       USER STORY 2.2: Sliding Session (Activity Refresh)
       ========================================================== */

    @Test
    @DisplayName("Block 1: Refresh Session Fails When Cookie Missing - [MEANT TO FAIL]")
    void testBlock1_RefreshSession_MissingCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Refresh token missing"));
    }

    @Test
    @DisplayName("Block 2: Refresh Session Fails When Token Revoked in Database - [MEANT TO FAIL]")
    void testBlock2_RefreshSession_RevokedToken() throws Exception {
        String rawRefreshToken = "raw-refresh-token-uuid-123";
        String hashedToken = hashString(rawRefreshToken);

        RefreshToken revokedToken = new RefreshToken(1L, hashedToken);
        revokedToken.revoke();

        given(refreshTokenRepository.findByTokenHash(hashedToken)).willReturn(Optional.of(revokedToken));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("Refresh-Token", rawRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Refresh token expired or revoked"));
    }

    @Test
    @DisplayName("Final Block: Valid Refresh Token Issues Brand New FULL_AUTH Access Token - [MEANT TO PASS]")
    void testFinalAC_RefreshSession_Success() throws Exception {
        String rawRefreshToken = "raw-refresh-token-uuid-999";
        String hashedToken = hashString(rawRefreshToken);

        RefreshToken activeToken = new RefreshToken(1L, hashedToken);
        given(refreshTokenRepository.findByTokenHash(hashedToken)).willReturn(Optional.of(activeToken));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("Refresh-Token", rawRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());
    }

    /* ==========================================================
       USER STORY 2.4: Explicit Logout & Token Blacklisting
       ========================================================== */

    @Test
    @DisplayName("Block 1: Logout User Revokes Refresh Tokens and Blacklists JTI - [MEANT TO PASS]")
    void testBlock1_LogoutSession_RevokesAndBlacklists() {
        String jti = "test-jwt-uuid-jti-1001";
        Date expiresAt = new Date(System.currentTimeMillis() + 900000);

        authSecurityService.logoutUserSession(1L, jti, expiresAt);

        verify(refreshTokenRepository).revokeAllUserTokens(1L);
        verify(blacklistedTokenRepository).save(any(BlacklistedToken.class));
    }

    @Test
    @DisplayName("Block 2: Scheduled Purge Executes Expired Blacklist Token Delete Query - [MEANT TO PASS]")
    void testBlock2_PurgeExpiredBlacklistTokens() {
        authSecurityService.purgeExpiredBlacklistTokens();

        verify(blacklistedTokenRepository).deleteAllExpiredTokensSince(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Final Block: Subsequent Request Using Blacklisted JTI Denied By JwtAuthenticationFilter - [MEANT TO FAIL]")
    void testFinalAC_BlacklistedJwt_DeniedByFilter() throws Exception {
        String fullAuthToken = jwtService.generateToken(mockUser, TokenType.FULL_AUTH);
        String jti = jwtService.extractJti(fullAuthToken);

        given(blacklistedTokenRepository.existsById(jti)).willReturn(true);

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + fullAuthToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("{\"error\": \"Token has been revoked. Please log in again.\"}"));

        verify(blacklistedTokenRepository).existsById(jti);
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedHash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash", e);
        }
    }
}
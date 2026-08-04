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
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.TokenType;
import com.example.authservice.service.AuthSecurityService;
import com.example.authservice.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.mockito.Mockito.never;
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

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    private UserRepository userRepository;

    @MockBean
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private User mockUser;

    // this runs before every single test in the class to set up one shared fake user
    // mockito mock() gives me a fake user object instead of a real db backed one
    // then I stub out the getters it will need, username, id and phone number
    // last I stub the user details service so spring security can load this fake user by username
    // saves every test below from repeating this same setup over and over
    @BeforeEach
    void setUp() {
        mockUser = mock(User.class);
        given(mockUser.getUsername()).willReturn("johndoe");
        given(mockUser.getId()).willReturn(1L);
        given(mockUser.getPhoneNumber()).willReturn("+15551234567");

        given(userDetailsService.loadUserByUsername("johndoe")).willReturn(mockUser);
    }

    // checking that an unknown device cookie gets correctly reported as not recognized
    // stub the device repository so looking up this user id with any hash string returns nothing
    // call isdevicerecognized directly on the service with a made up cookie value
    // since the repo came back empty the method should report false
    // and verify the repository method actually got called with those arguments
    @Test
    @DisplayName("Block 1: Device Repository Query Returns Empty for Unrecognized Cookie - [MEANT TO FAIL]")
    void testBlock1_UnrecognizedDevice_ReturnsFalse() {
        given(deviceRepository.findByUserIdAndDeviceHash(eq(1L), any(String.class)))
                .willReturn(Optional.empty());

        boolean recognized = authSecurityService.isDeviceRecognized(1L, "invalid-device-cookie-123");

        assertThat(recognized).isFalse();
        verify(deviceRepository).findByUserIdAndDeviceHash(eq(1L), any(String.class));
    }

    // same idea as the last test but flipped, this time the device should come back recognized
    // stub the repository to return an actual recognizeddevice record for this user and hash
    // call isdevicerecognized with a cookie value that is supposed to match that record
    // this time it should come back true since the repo actually had a match
    // and confirm the repository lookup was invoked with the right arguments
    @Test
    @DisplayName("Block 2: Device Repository Query Returns Match for Valid Cookie - [MEANT TO PASS]")
    void testBlock2_RecognizedDevice_ReturnsTrue() {
        given(deviceRepository.findByUserIdAndDeviceHash(eq(1L), any(String.class)))
                .willReturn(Optional.of(new RecognizedDevice(1L, "hashed-cookie")));

        boolean recognized = authSecurityService.isDeviceRecognized(1L, "valid-device-cookie-123");

        assertThat(recognized).isTrue();
        verify(deviceRepository).findByUserIdAndDeviceHash(eq(1L), any(String.class));
    }

    // full end to end style test hitting the real login endpoint through mockmvc
    // mock out the authentication manager so it succeeds and returns our fake user as the principal
    // also stub the device repository to say this device has never been seen before
    // post a real json body to /api/v1/auth/login the same way an actual client would
    // expect a 202 accepted status back instead of a normal 200 since 2fa still has to happen
    // and expect the response body to say 2fa_required and to include a pre auth token
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

    // testing that kicking off sms 2fa actually does three things in one call
    // call triggersms2fa directly with a user id and a phone number
    // first it should delete any old 2fa code that is still sitting around for that user
    // then it should save a brand new twofactorcode row for the fresh code
    // and finally it should publish a message out to kafka on the notification events topic
    @Test
    @DisplayName("Block 1: Trigger SMS 2FA Clears Old Codes via Repository and Publishes to Kafka - [MEANT TO PASS]")
    void testBlock1_TriggerSms2fa_DeletesOldCodeAndPublishesKafka() {
        authSecurityService.triggerSms2fa(1L, "+15551234567");

        verify(twoFactorCodeRepository).deleteByUserId(1L);
        verify(twoFactorCodeRepository).save(any(TwoFactorCode.class));
        verify(kafkaTemplate).send(eq("notification-events"), any(String.class));
    }

    // end to end test for successfully verifying an sms 2fa code
    // generate a real pre auth jwt for the mock user using the actual jwtservice, not a fake one
    // stub the repository to return a valid twofactorcode that matches what gets submitted below
    // post the code to verify-2fa/sms using that pre auth token as the bearer header
    // expect status ok, a success field, an access token, and a set-cookie header for the device
    // last, make sure the code that just got used is actually deleted so it cannot be replayed
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

    // checking that a pre auth token by itself cannot get into a fully protected endpoint
    // generate a pre auth token, this is the partial token issued before 2fa is completed
    // hit the logout endpoint using that pre auth token as the bearer token
    // it should get blocked with a forbidden status
    // and the error message should explain that full 2fa verification is still required
    @Test
    @DisplayName("Block 1: PRE_AUTH Token Restricted from Accessing Protected Endpoints - [MEANT TO FAIL]")
    void testBlock1_PreAuthToken_DeniedProtectedAccess() throws Exception {
        String preAuthToken = jwtService.generateToken(mockUser, TokenType.PRE_AUTH);

        mockMvc.perform(get("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + preAuthToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Partial authentication. 2FA verification required."));
    }

    // checking a fully authenticated token works the way it is supposed to end to end
    // generate a full auth token for the mock user, this is the real post 2fa token
    // confirm the token type that comes back out is full_auth and the username matches
    // then actually use that token to call the logout endpoint
    // expect a 200 ok back along with a logged out successfully message
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

    // making sure the refresh endpoint fails cleanly when there is no cookie at all
    // call /api/v1/auth/refresh with no refresh token cookie attached to the request
    // expect a 401 unauthorized status back
    // and the error message should say the refresh token is missing
    @Test
    @DisplayName("Block 1: Refresh Session Fails When Cookie Missing - [MEANT TO FAIL]")
    void testBlock1_RefreshSession_MissingCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Refresh token missing"));
    }

    // making sure a revoked refresh token cannot be used to pull a new session
    // build a raw refresh token string and hash it the same way the real service would
    // create a refreshtoken entity with that hash and immediately call revoke() on it
    // stub the repository so looking up that hash returns this already revoked token
    // hit the refresh endpoint with the raw token attached as a cookie
    // expect 401 unauthorized with an error message saying the token is expired or revoked
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

    // happy path test for refreshing a session using a still valid refresh token
    // build a raw token and its hashed form the same way the service does internally
    // stub the repository to return an active, non revoked token for that hash
    // hit the refresh endpoint passing the raw token back as a cookie
    // expect status ok and a brand new access token sitting in the response body
    @Test
    @DisplayName("Final Block: Valid Refresh Token Issues Brand New FULL_AUTH Access Token - [MEANT TO PASS]")
    void testFinalAC_RefreshSession_Success() throws Exception {
        String rawRefreshToken = "raw-refresh-token-uuid-999";
        String hashedToken = hashString(rawRefreshToken);

        RefreshToken activeToken = new RefreshToken(1L, hashedToken);
        given(refreshTokenRepository.findByTokenHash(hashedToken)).willReturn(Optional.of(activeToken));
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("Refresh-Token", rawRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());
    }

    // testing that logging out actually cleans up both refresh tokens and the jwt itself
    // make up a jti string and an expiration date about fifteen minutes out
    // call logoutusersession directly with the user id, jti and expiration
    // verify it revoked every one of this user's refresh tokens
    // and verify it saved a new blacklistedtoken row for the jti so it cannot be reused
    @Test
    @DisplayName("Block 1: Logout User Revokes Refresh Tokens and Blacklists JTI - [MEANT TO PASS]")
    void testBlock1_LogoutSession_RevokesAndBlacklists() {
        String jti = "test-jwt-uuid-jti-1001";
        Date expiresAt = new Date(System.currentTimeMillis() + 900000);

        authSecurityService.logoutUserSession(1L, jti, expiresAt);

        verify(refreshTokenRepository).revokeAllUserTokens(1L);
        verify(blacklistedTokenRepository).save(any(BlacklistedToken.class));
    }

    // testing the scheduled cleanup job for the jwt blacklist table
    // call purgeexpiredblacklisttokens directly the same way the scheduler would
    // verify it calls deleteallexpiredtokenssince on the repository
    // passing some localdatetime as the cutoff, the exact instant does not matter here
    @Test
    @DisplayName("Block 2: Scheduled Purge Executes Expired Blacklist Token Delete Query - [MEANT TO PASS]")
    void testBlock2_PurgeExpiredBlacklistTokens() {
        authSecurityService.purgeExpiredBlacklistTokens();

        verify(blacklistedTokenRepository).deleteAllExpiredTokensSince(any(LocalDateTime.class));
    }

    // end to end test making sure a blacklisted jwt gets rejected by the security filter
    // generate a real full auth token then pull its jti back out of it
    // stub the blacklist repository so existsbyid for that jti returns true
    // hit the logout endpoint using that token as the bearer header
    // expect a 401 unauthorized with a message saying the token has been revoked
    // and confirm the filter actually checked the blacklist repository for that jti
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

    // ==========================================
    // Registration
    // ==========================================

    @Test
    @DisplayName("Register: New Username Creates Account With Hashed Password - [MEANT TO PASS]")
    void testRegister_NewUsername_CreatesAccount() throws Exception {
        given(userRepository.existsByUsername("newuser")).willReturn(false);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"SecurePass123!\",\"phoneNumber\":\"+15551234567\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getUsername()).isEqualTo("newuser");
        assertThat(savedUser.getValue().getPassword()).isNotEqualTo("SecurePass123!");
        assertThat(passwordEncoder.matches("SecurePass123!", savedUser.getValue().getPassword())).isTrue();
    }

    // confirms registration publishes a UserRegistered event so profile-service/account-service
    // can provision their own initial rows for this user - stubs save() to mimic Hibernate
    // populating the generated id on the passed-in entity, since a plain mock wouldn't do that
    @Test
    @DisplayName("Register: New Username Publishes UserRegistered Event To Kafka - [MEANT TO PASS]")
    void testRegister_NewUsername_PublishesUserRegisteredEvent() throws Exception {
        given(userRepository.existsByUsername("newuser")).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"SecurePass123!\",\"phoneNumber\":\"+15551234567\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("user-events"), payload.capture());
        assertThat(payload.getValue()).contains("\"userId\":\"99\"");
        assertThat(payload.getValue()).contains("\"username\":\"newuser\"");
        assertThat(payload.getValue()).contains("\"phoneNumber\":\"+15551234567\"");
    }

    @Test
    @DisplayName("Register: Duplicate Username Rejected With Conflict - [MEANT TO FAIL]")
    void testRegister_DuplicateUsername_Rejected() throws Exception {
        given(userRepository.existsByUsername("johndoe")).willReturn(true);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"johndoe\",\"password\":\"SecurePass123!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username is already taken"));

        verify(kafkaTemplate, never()).send(eq("user-events"), any(String.class));
    }

    @Test
    @DisplayName("Register: Missing Username Or Password Rejected - [MEANT TO FAIL]")
    void testRegister_MissingFields_Rejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(kafkaTemplate, never()).send(eq("user-events"), any(String.class));
    }

    @Test
    @DisplayName("Register: Short Password Rejected - [MEANT TO FAIL]")
    void testRegister_ShortPassword_Rejected() throws Exception {
        given(userRepository.existsByUsername("newuser")).willReturn(false);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
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
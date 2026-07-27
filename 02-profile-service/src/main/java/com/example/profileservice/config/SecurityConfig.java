package com.example.profileservice.config;

import com.example.profileservice.security.KycWebhookFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

// @EnableMethodSecurity is what actually makes @PreAuthorize on the controllers take effect,
// without it those annotations would just sit there completely ignored
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final KycWebhookFilter kycWebhookFilter;

    // Same base64-encoded HMAC secret auth-service signs tokens with (shared via the
    // JWT_SECRET_KEY k8s secret in prod, see application-prod.yml) so this service can verify
    // a token's signature on its own, without ever calling back to auth-service.
    @Value("${application.security.jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    public SecurityConfig(KycWebhookFilter kycWebhookFilter) {
        this.kycWebhookFilter = kycWebhookFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // webhooks and the kyc-status lookup stay open at the spring security layer since
            // they are protected by other means instead, the webhook by its own hmac signature
            // filter below, and kyc-status because it is meant for internal service to service calls
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/webhooks/**").permitAll()
                .requestMatchers("/api/v1/profiles/*/kyc-status").permitAll()
                // Internal, service-to-service reads for notification-service (FR9.2/FR10.2) -
                // restricted to GET so the PUT /threshold and /daily-summary endpoints right next
                // to them stay behind SCOPE_FULL_AUTH as before.
                .requestMatchers(HttpMethod.GET, "/api/v1/profile/alerts/*", "/api/v1/profile/alerts/daily-summary-users").permitAll()
                // Swagger/OpenAPI UI - documentation, not application data
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(kycWebhookFilter, UsernamePasswordAuthenticationFilter.class)
            // Validates the bearer token against the JwtDecoder bean below and populates the
            // SecurityContext with a JwtAuthenticationToken, whose authorities come from the
            // token's "scope" claim (e.g. "FULL_AUTH" -> SCOPE_FULL_AUTH) — needed for
            // PreferenceController's class-level @PreAuthorize check to ever pass.
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
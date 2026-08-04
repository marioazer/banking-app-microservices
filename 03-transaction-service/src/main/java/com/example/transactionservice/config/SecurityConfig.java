package com.example.transactionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Same base64-encoded HMAC secret auth-service signs tokens with (shared via the
    // JWT_SECRET_KEY k8s secret in prod, see application-prod.properties) so this service can
    // verify a token's signature on its own, without ever calling back to auth-service.
    @Value("${application.security.jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // Allow the Angular dev server (and later, its deployed origin) to call this API
            // cross-origin, including sending the Authorization header on credentialed requests.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Method-level @PreAuthorize on TransferController enforces SCOPE_FULL_AUTH;
                // require authentication here so anonymous callers are rejected outright.
                // notice InternalFraudController has no matching exemption here, meaning it
                // technically still requires authentication too, just not a specific scope
                // Swagger/OpenAPI UI - documentation, not application data
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Validates the bearer token against the JwtDecoder bean below and populates the
            // SecurityContext with a JwtAuthenticationToken, whose authorities come from the
            // token's "scope" claim (e.g. "FULL_AUTH" -> SCOPE_FULL_AUTH).
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

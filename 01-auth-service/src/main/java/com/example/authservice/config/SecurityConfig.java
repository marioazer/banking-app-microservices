package com.example.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.authservice.security.JwtAuthenticationFilter;

// @Configuration marks this as a class spring reads at startup to build beans from, @EnableWebSecurity
// turns on spring security's web support at all, without it none of this filter chain setup would matter
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    // @Bean here means spring calls this method once at startup and keeps the returned
    // securityfilterchain object around as a managed bean, this is what actually wires the rules below
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Disable CSRF as we are using a stateless REST API with JWTs
            // learned csrf protection exists for cookie based browser sessions, a jwt bearer
            // token api like this one is not vulnerable the same way so it is safe to turn off here
            .csrf(csrf -> csrf.disable())
            
            // 2. Configure endpoint routing rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints that do not require an Access Token
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/verify-2fa/**").permitAll()
                .requestMatchers("/api/v1/auth/refresh").permitAll()
                // Swagger/OpenAPI UI - documentation, not application data
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                
                // The logout endpoint explicitly requires authentication per FR2.4
                .requestMatchers("/api/v1/auth/logout").authenticated()
                
                // Any other backend endpoints require authentication
                .anyRequest().authenticated()
            )
            
            // 3. Enforce stateless session management
            // stateless means spring never creates or reads an httpsession for these requests,
            // every request has to prove who it is with the jwt on its own, nothing remembered server side
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 4. Register our AuthenticationProvider (handles password hashing checks)
            .authenticationProvider(authenticationProvider)
            
            // 5. Inject our custom JWT filter BEFORE the default Spring Security filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
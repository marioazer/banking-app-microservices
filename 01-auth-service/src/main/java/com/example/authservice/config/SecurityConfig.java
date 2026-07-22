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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Disable CSRF as we are using a stateless REST API with JWTs
            .csrf(csrf -> csrf.disable())
            
            // 2. Configure endpoint routing rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints that do not require an Access Token
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/verify-2fa/**").permitAll()
                .requestMatchers("/api/v1/auth/refresh").permitAll()
                
                // The logout endpoint explicitly requires authentication per FR2.4
                .requestMatchers("/api/v1/auth/logout").authenticated()
                
                // Any other backend endpoints require authentication
                .anyRequest().authenticated()
            )
            
            // 3. Enforce stateless session management
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
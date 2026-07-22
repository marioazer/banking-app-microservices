package com.example.authservice.security;

import com.example.authservice.repository.BlacklistedTokenRepository;
import com.example.authservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    public JwtAuthenticationFilter(JwtService jwtService, 
                                   UserDetailsService userDetailsService, 
                                   BlacklistedTokenRepository blacklistedTokenRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // 1. Check for the Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return; // Exit filter
        }

        jwt = authHeader.substring(7);

        try {
            // 2. Extract Data
            username = jwtService.extractUsername(jwt);
            String jti = jwtService.extractJti(jwt);
            TokenType tokenType = jwtService.extractTokenType(jwt);

            // 3. Blacklist Check (FR2.4)
            if (blacklistedTokenRepository.existsById(jti)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Token has been revoked. Please log in again.\"}");
                return; // Short-circuit the request
            }

            // 4. Boundary Enforcement (FR1.4)
            // Use getRequestURI() so MockMvc and Tomcat both match correctly
            String requestPath = request.getRequestURI();
            if (tokenType == TokenType.PRE_AUTH && !requestPath.contains("/api/v1/auth/verify-2fa")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Partial authentication. 2FA verification required.\"}");
                return; // Short-circuit the request
            }

            // 5. Authenticate the User in Spring Security Context
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid or expired token.\"}");
            return;
        }

        // Proceed to the next filter or the target Controller
        filterChain.doFilter(request, response);
    }
}
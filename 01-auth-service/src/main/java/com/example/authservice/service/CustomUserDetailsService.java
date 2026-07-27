package com.example.authservice.service;

import com.example.authservice.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Real UserDetailsService backing Spring Security's login/JWT-filter authentication - previously
 * missing entirely, meaning login had no real AuthenticationProvider wired to it (only ever
 * substituted with a @MockBean in tests) and would fail to even start the application outside
 * a test context.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with username: " + username));
    }
}

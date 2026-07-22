package com.example.authservice.service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.example.authservice.security.TokenType;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    // In a real app, this is injected via application.yml and stored in a secure vault
    @Value("${application.security.jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    // Strict time limits per the requirements
    private static final long FULL_AUTH_EXPIRATION = 15 * 60 * 1000; // 15 minutes in milliseconds
    private static final long PRE_AUTH_EXPIRATION = 5 * 60 * 1000;   // 5 minutes in milliseconds

    /**
     * Generates a token with a specific type (PRE_AUTH or FULL_AUTH).
     */
    public String generateToken(UserDetails userDetails, TokenType tokenType) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("token_type", tokenType.name()); // Embed the boundary restriction

        long expirationMillis = (tokenType == TokenType.FULL_AUTH) ? FULL_AUTH_EXPIRATION : PRE_AUTH_EXPIRATION;

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setId(UUID.randomUUID().toString()) // The 'jti' claim for explicit tracking and blacklisting
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public TokenType extractTokenType(String token) {
        String typeString = extractClaim(token, claims -> claims.get("token_type", String.class));
        return TokenType.valueOf(typeString);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    public Date extractExpirationDate(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                // If the token is expired, this will automatically throw an ExpiredJwtException
                .parseClaimsJws(token) 
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
package com.dtao.seminarbooking.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.UUID;

/**
 * Helper for issuing and validating JWTs. Uses HS256.
 * jwt.secret should be at least 32 bytes; if shorter we derive a 32-byte key via SHA-256.
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:change_this_secret_at_least_32_chars}")
    private String jwtSecret;

    // default 1 hour
    @Value("${jwt.expiration-ms:3600000}")
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        try {
            byte[] keyBytes = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                keyBytes = sha256.digest(keyBytes);
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception ex) {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                byte[] keyBytes = sha256.digest((jwtSecret == null ? "default_secret" : jwtSecret).getBytes(StandardCharsets.UTF_8));
                return Keys.hmacShaKeyFor(keyBytes);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to build JWT signing key", e);
            }
        }
    }

    public String generateToken(org.springframework.security.core.Authentication authentication, boolean rememberMe) {
        String username = authentication.getName();
        if (username != null) username = username.trim().toLowerCase();

        long expMs = jwtExpirationMs;
        if (rememberMe) {
            long weekMs = 7L * 24L * 60L * 60L * 1000L;
            expMs = Math.min(jwtExpirationMs * 7L, weekMs * 4L);
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expMs);

        return Jwts.builder()
                .setSubject(username)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        try {
            String sub = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
            return sub == null ? null : sub.trim().toLowerCase();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public long getExpiresInSeconds(boolean rememberMe) {
        long ms = jwtExpirationMs;
        if (rememberMe) ms = Math.min(jwtExpirationMs * 7L, 4L * 7L * 24L * 60L * 60L * 1000L);
        return ms / 1000L;
    }
}

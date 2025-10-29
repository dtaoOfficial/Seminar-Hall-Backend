package com.dtao.seminarbooking.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * ✅ Secure JWT Provider
 * - Uses HS256 with minimum 32-byte secret
 * - Includes role, issuer, audience, and unique ID (jti)
 * - Handles remember-me token lifetime
 * - Backward compatible with old 2-arg generateToken()
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:change_this_secret_at_least_32_chars}")
    private String jwtSecret;

    // Default expiration (1 hour)
    @Value("${jwt.expiration-ms:3600000}")
    private long jwtExpirationMs;

    @Value("${jwt.issuer:dtao-seminar-backend}")
    private String issuer;

    @Value("${jwt.audience:dtao-seminar-frontend}")
    private String audience;

    /** 🔒 Create secure HMAC key (32-byte minimum) */
    private SecretKey getSigningKey() {
        try {
            byte[] keyBytes = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                keyBytes = sha256.digest(keyBytes);
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build JWT signing key", ex);
        }
    }

    /** ✅ Generate JWT with username + explicit role */
    public String generateToken(Authentication authentication, boolean rememberMe, String role) {
        String username = authentication.getName();
        if (username != null) username = username.trim().toLowerCase();

        long expMs = jwtExpirationMs;
        if (rememberMe) {
            long weekMs = 7L * 24L * 60L * 60L * 1000L; // 1 week
            expMs = Math.min(jwtExpirationMs * 7L, weekMs * 4L); // max 4 weeks
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expMs);

        String token = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .addClaims(Map.of(
                        "role", role,
                        "rememberMe", rememberMe
                ))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();

        System.out.printf("[JwtTokenProvider] Token created for user=%s, role=%s, expiresIn=%dmin%n",
                username, role, expMs / 60000);
        return token;
    }

    /** ✅ Backward-compatible version (for older controllers) */
    public String generateToken(Authentication authentication, boolean rememberMe) {
        String role = "DEPARTMENT"; // default fallback
        try {
            role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(a -> a.getAuthority().replace("ROLE_", "").toUpperCase())
                    .orElse("DEPARTMENT");
        } catch (Exception ignored) { }
        return generateToken(authentication, rememberMe, role);
    }

    /** ✅ Extract username safely */
    public String getUsernameFromToken(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /** ✅ Extract role safely */
    public String getRoleFromToken(String token) {
        try {
            Object role = getClaims(token).get("role");
            return role == null ? null : role.toString().toUpperCase();
        } catch (Exception e) {
            return null;
        }
    }

    /** ✅ Common claims parser */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .requireAudience(audience)
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** ✅ Validate token integrity and expiration */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            getClaims(token); // parse + validate
            return true;
        } catch (ExpiredJwtException ex) {
            System.err.println("⚠️ JWT expired: " + ex.getMessage());
        } catch (JwtException | IllegalArgumentException ex) {
            System.err.println("❌ Invalid JWT: " + ex.getMessage());
        }
        return false;
    }

    /** ✅ Get expiration in seconds */
    public long getExpiresInSeconds(boolean rememberMe) {
        long ms = jwtExpirationMs;
        if (rememberMe)
            ms = Math.min(jwtExpirationMs * 7L, 4L * 7L * 24L * 60L * 60L * 1000L);
        return ms / 1000L;
    }

    /** ✅ Validate that token role matches required */
    public boolean validateUserRole(String token, String requiredRole) {
        String role = getRoleFromToken(token);
        return role != null && role.equalsIgnoreCase(requiredRole);
    }
}

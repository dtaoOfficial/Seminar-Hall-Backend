package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.security.CustomUserDetailsService;
import com.dtao.seminarbooking.security.JwtTokenProvider;
import com.dtao.seminarbooking.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private OtpService otpService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // <<--- fixed import: use the CustomUserDetailsService from the security package
    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    // ========================
    // 🔐 PASSWORD RECOVERY FLOW
    // ========================
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        if (otpService.generateOtp(email)) {
            // Neutral message to prevent user enumeration
            return ResponseEntity.ok(Map.of("message", "OTP sent to email if account exists."));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Email not found"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        String otp = req.get("otp");

        String status = otpService.verifyOtp(email, otp);
        return switch (status) {
            case "VALID" -> ResponseEntity.ok(Map.of("message", "OTP verified. You can reset password now."));
            case "EXPIRED" -> ResponseEntity.badRequest().body(Map.of("error", "OTP expired. Please request a new one."));
            case "INVALID" -> ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP. Please try again."));
            case "NO_TOKEN" -> ResponseEntity.badRequest().body(Map.of("error", "No active OTP found. Please request again."));
            case "USER_NOT_FOUND" -> ResponseEntity.badRequest().body(Map.of("error", "No account found for this email."));
            default -> ResponseEntity.badRequest().body(Map.of("error", "Verification failed."));
        };
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        String newPassword = req.get("newPassword");
        if (otpService.resetPassword(email, newPassword)) {
            return ResponseEntity.ok(Map.of("message", "Password updated. You can login now."));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Failed to reset password"));
    }

    // =========================
    // 🔁 JWT REFRESH TOKEN FLOW
    // =========================
    /**
     * Frontend calls: api.post("/auth/refresh-token")
     * Expects Authorization header: "Bearer <token>"
     *
     * Returns: { token: "...", expiresIn: <seconds> }
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestHeader(value = "Authorization", required = false) String header) {
        try {
            String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
            }

            String username = jwtTokenProvider.getUsernameFromToken(token);
            if (username == null || username.isBlank()) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid token payload"));
            }

            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("error", "User not found"));
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // false = not rememberMe (short session). Match your login behavior as needed.
            String newToken = jwtTokenProvider.generateToken(auth, false);
            long expiresIn = jwtTokenProvider.getExpiresInSeconds(false);

            log.info("Refreshed token for user={}", username);
            return ResponseEntity.ok(Map.of("token", newToken, "expiresIn", expiresIn));
        } catch (Exception ex) {
            log.error("refreshToken failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "Server error during token refresh"));
        }
    }
}

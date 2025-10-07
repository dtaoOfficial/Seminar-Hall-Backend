package com.dtao.seminarbooking.controller;

import com.dtao.seminarbooking.service.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private OtpService otpService;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        if (otpService.generateOtp(email)) {
            // Neutral message to avoid user enumeration
            return ResponseEntity.ok(Map.of("message", "OTP sent to email if account exists."));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Email not found"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> req) {
        String email = req.get("email");
        String otp = req.get("otp");

        String status = otpService.verifyOtp(email, otp);

        switch (status) {
            case "VALID":
                return ResponseEntity.ok(Map.of("message", "OTP verified. You can reset password now."));
            case "EXPIRED":
                return ResponseEntity.badRequest().body(Map.of("error", "OTP expired. Please request a new one."));
            case "INVALID":
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP. Please try again."));
            case "NO_TOKEN":
                return ResponseEntity.badRequest().body(Map.of("error", "No active OTP found. Please request again."));
            case "USER_NOT_FOUND":
                return ResponseEntity.badRequest().body(Map.of("error", "No account found for this email."));
            default:
                return ResponseEntity.badRequest().body(Map.of("error", "Verification failed."));
        }
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
}

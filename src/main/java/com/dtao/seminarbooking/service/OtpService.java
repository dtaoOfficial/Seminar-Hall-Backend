package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.OtpToken;
import com.dtao.seminarbooking.model.User;
import com.dtao.seminarbooking.repo.OtpTokenRepository;
import com.dtao.seminarbooking.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class OtpService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final SecureRandom random = new SecureRandom();

    // Generate OTP & send to email
    public boolean generateOtp(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) return false;

        User user = maybeUser.get();

        // Invalidate all old tokens
        List<OtpToken> oldTokens = otpTokenRepository.findByUserIdAndUsedIsFalse(user.getId());
        for (OtpToken t : oldTokens) {
            t.setUsed(true);
            otpTokenRepository.save(t);
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", random.nextInt(1_000_000));
        String otpHash = passwordEncoder.encode(otp);

        // Save new OTP valid for 5 minutes
        OtpToken otpToken = new OtpToken(user.getId(), otpHash, Instant.now().plus(5, ChronoUnit.MINUTES));
        otpTokenRepository.save(otpToken);

        // Send mail (returns boolean but we still return true even if email fails to avoid leaking user presence)
        emailService.sendOtp(user.getEmail(), otp);

        return true;
    }

    // Verify OTP with detailed status
    // returns: "VALID", "EXPIRED", "INVALID", "NO_TOKEN", "USER_NOT_FOUND"
    public String verifyOtp(String email, String enteredOtp) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) return "USER_NOT_FOUND";

        User user = maybeUser.get();

        List<OtpToken> tokens = otpTokenRepository.findByUserIdAndUsedIsFalse(user.getId());
        if (tokens.isEmpty()) return "NO_TOKEN";

        // Pick the latest OTP (by expiresAt)
        OtpToken token = tokens.stream()
                .max(Comparator.comparing(OtpToken::getExpiresAt))
                .orElse(null);

        if (token == null) return "NO_TOKEN";
        if (token.getExpiresAt().isBefore(Instant.now())) return "EXPIRED";

        if (passwordEncoder.matches(enteredOtp, token.getOtpHash())) {
            // Mark all tokens as used
            for (OtpToken t : tokens) {
                t.setUsed(true);
                otpTokenRepository.save(t);
            }
            return "VALID";
        }

        return "INVALID";
    }

    // Reset password after OTP verified
    public boolean resetPassword(String email, String newPassword) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) return false;

        User user = maybeUser.get();

        // Hash and save new password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark all tokens as used
        List<OtpToken> tokens = otpTokenRepository.findByUserIdAndUsedIsFalse(user.getId());
        for (OtpToken t : tokens) {
            t.setUsed(true);
            otpTokenRepository.save(t);
        }

        return true;
    }
}

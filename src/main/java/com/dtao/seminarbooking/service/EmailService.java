package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.HallOperator;
import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * EmailService — now uses Brevo HTTP API (no SMTP needed)
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final BrevoClient brevoClient;

    @Value("${app.mail.from:no-reply@yourdomain.com}")
    private String mailFrom;

    @Value("${app.mail.owner:KURAPARTHI MAHESWAR REDDY}")
    private String ownerName;

    @Value("${app.mail.company:DTAO OFFICIAL}")
    private String companyName;

    @Value("${app.mail.website:https://nhcehallbooking.netlify.app/}")
    private String websiteUrl;

    public EmailService(BrevoClient brevoClient) {
        this.brevoClient = Objects.requireNonNull(brevoClient, "brevoClient must not be null");
    }

    // -------------------- OTP --------------------
    public boolean sendOtp(String toEmail, String otp) {
        if (!validEmail(toEmail) || otp == null) return false;
        String subject = "Seminar Booking - Password Reset OTP";
        String html = "<html><body style='font-family:Arial;padding:18px'>" +
                "<h2>OTP Verification</h2>" +
                "<p>Your OTP is <strong>" + escape(otp) + "</strong></p>" +
                "<p>Valid for 5 minutes.</p>" +
                "<p>If you did not request this, please ignore.</p>" +
                "</body></html>";
        return brevoClient.sendEmail(companyName, mailFrom, List.of(toEmail), subject, html);
    }

    // -------------------- Welcome --------------------
    @Async
    public CompletableFuture<Boolean> sendWelcomeEmail(User user) {
        if (user == null || !validEmail(user.getEmail())) return done(false);
        String to = user.getEmail();
        String name = user.getName() == null ? "User" : user.getName();

        String subject = "Welcome to the Seminar Booking Portal — " + companyName;
        String html = "<html><body style='font-family:Arial;padding:18px'>" +
                "<h2 style='color:#0b5ed7'>Welcome to " + escape(companyName) + "</h2>" +
                "<p>Dear " + escape(name) + ",</p>" +
                "<p>Thank you for registering on our Seminar Booking portal.</p>" +
                "<p>Email: " + escape(to) + "</p>" +
                "<p>Visit: <a href='" + websiteUrl + "'>" + websiteUrl + "</a></p>" +
                footer() + "</body></html>";

        return done(brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html));
    }

    // -------------------- Booking created --------------------
    @Async
    public CompletableFuture<Boolean> sendBookingCreatedEmail(Seminar s) {
        if (s == null || !validEmail(s.getEmail())) return done(false);
        String to = s.getEmail();
        String subject = "Seminar booking received — " + safe(s.getHallName());

        String html = "<html><body style='font-family:Arial;padding:18px'>" +
                "<h2 style='color:#0b5ed7'>Seminar Booking Received</h2>" +
                "<p>Dear " + escape(s.getBookingName()) + ",</p>" +
                "<p>Your booking request has been received.</p>" +
                table(new String[][]{
                        {"Hall", safe(s.getHallName())},
                        {"Date", safe(s.getDate())},
                        {"Slot", safe(s.getSlot())},
                        {"Event", safe(s.getSlotTitle())},
                        {"Department / Contact", safe(s.getDepartment()) + " / " + safe(s.getPhone())}
                }) +
                footer() + "</body></html>";

        return done(brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html));
    }

    // -------------------- Seminar removed --------------------
    @Async
    public CompletableFuture<Boolean> sendSeminarRemovedEmail(Seminar s) {
        if (s == null || !validEmail(s.getEmail())) return done(false);
        String subject = "Seminar booking removed — " + safe(s.getHallName());
        String html = "<html><body style='font-family:Arial;padding:18px'>" +
                "<h2 style='color:#d9534f'>Seminar Booking Removed</h2>" +
                "<p>Your booking has been removed.</p>" +
                table(new String[][]{
                        {"Hall", safe(s.getHallName())},
                        {"Date", safe(s.getDate())},
                        {"Slot", safe(s.getSlot())},
                        {"Event", safe(s.getSlotTitle())}
                }) +
                footer() + "</body></html>";
        return done(brevoClient.sendEmail(companyName, mailFrom, List.of(s.getEmail()), subject, html));
    }

    // -------------------- Account removed --------------------
    @Async
    public CompletableFuture<Boolean> sendAccountRemovedEmail(User user) {
        if (user == null || !validEmail(user.getEmail())) return done(false);
        String subject = "Account removed — " + companyName;
        String html = "<html><body style='font-family:Arial;padding:18px'>" +
                "<h2 style='color:#d9534f'>Account Removed</h2>" +
                "<p>Dear " + escape(user.getName()) + ",</p>" +
                "<p>Your account (" + escape(user.getEmail()) + ") has been removed from the Seminar Booking portal.</p>" +
                footer() + "</body></html>";
        return done(brevoClient.sendEmail(companyName, mailFrom, List.of(user.getEmail()), subject, html));
    }

    // -------------------- helpers --------------------
    private CompletableFuture<Boolean> done(boolean ok) {
        return CompletableFuture.completedFuture(ok);
    }

    private String footer() {
        return "<hr style='border:none;border-top:1px solid #eee;margin:12px 0'/>" +
                "<p style='font-size:13px'>Owner/Admin: <strong>" + escape(ownerName) + "</strong><br/>" +
                "Company: <strong>" + escape(companyName) + "</strong><br/>" +
                "Visit: <a href='" + websiteUrl + "'>" + websiteUrl + "</a></p>";
    }

    private String table(String[][] rows) {
        StringBuilder sb = new StringBuilder("<table style='border-collapse:collapse;width:100%'>");
        for (String[] r : rows) {
            sb.append("<tr><td style='border:1px solid #eee;padding:6px'><strong>")
                    .append(escape(r[0])).append("</strong></td><td style='border:1px solid #eee;padding:6px'>")
                    .append(escape(r[1])).append("</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private boolean validEmail(String e) {
        return e != null && e.contains("@") && e.length() <= 254;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}

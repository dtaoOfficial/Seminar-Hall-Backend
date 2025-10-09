package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.HallOperator;
import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Centralised email service used across controllers.
 * - async methods return CompletableFuture<Boolean>
 * - synchronous OTP sender returns boolean
 * - HTML messages are polite & professional
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@yourdomain.com}")
    private String mailFrom;

    @Value("${app.mail.owner:KURAPARTHI MAHESWAR REDDY}")
    private String ownerName;

    @Value("${app.mail.company:DTAO OFFICIAL}")
    private String companyName;

    @Value("${app.mail.website:https://dtaoofficial.netlify.app/}")
    private String websiteUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null");
    }

    // -------------------- OTP (synchronous - returns immediate boolean) --------------------
    public boolean sendOtp(String toEmail, String otp) {
        if (!validEmail(toEmail) || otp == null) {
            logger.warn("sendOtp called with invalid arguments (toEmail={}, otpNull={})", toEmail, otp == null);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String txt = "Your OTP is: " + escapePlain(otp) + "\nValid for 5 minutes.\n\nIf you did not request this, please ignore this email.";
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject("Seminar Booking - Password Reset OTP");
            helper.setText(txt, false); // plain text
            mailSender.send(message);
            logger.info("[EmailService] OTP email sent to {} (from={})", toEmail, mailFrom);
            return true;
        } catch (MailException ex) {
            logger.error("[EmailService] Failed to send OTP email to {}: {}", toEmail, ex.getMessage(), ex);
            return false;
        } catch (Exception ex) {
            logger.error("[EmailService] Unexpected OTP send error for {}: {}", toEmail, ex.getMessage(), ex);
            return false;
        }
    }

    // -------------------- Welcome (async) --------------------
    @Async
    public CompletableFuture<Boolean> sendWelcomeEmail(User user) {
        if (user == null || !validEmail(user.getEmail())) return CompletableFuture.completedFuture(false);
        String to = user.getEmail();
        String name = user.getName() == null ? "User" : user.getName();

        String subject = "Welcome to the Seminar Booking Portal — " + companyName;
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#111;background:#fff;padding:18px;\">")
                .append("<div style=\"max-width:650px;margin:0 auto;border:1px solid #e6e6e6;padding:18px;border-radius:6px;\">")
                .append("<h2 style=\"color:#0b5ed7;margin:0 0 8px 0;\">Welcome to ").append(escape(companyName)).append("</h2>")
                .append("<p>Dear ").append(escape(name)).append(",</p>")
                .append("<p>Thank you for creating an account on the Seminar Booking portal. You can now request seminar halls and manage your bookings easily.</p>")
                .append("<p><strong>Account details</strong><br/>Email: ").append(escape(to)).append("</p>")
                .append("<p>Visit: <a href=\"").append(websiteUrl).append("\">").append(websiteUrl).append("</a></p>")
                .append("<hr style=\"border:none;border-top:1px solid #eee\"/>")
                .append("<p style=\"font-size:13px;color:#444\">Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>")
                .append("Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("<p style=\"font-size:12px;color:#888;margin-top:6px\">If you did not request this account, please ignore this email or contact the administration.</p>")
                .append("</div></body></html>");

        return runSendHtmlMailAsync(to, subject, html.toString());
    }

    // -------------------- Booking created (async) --------------------
    @Async
    public CompletableFuture<Boolean> sendBookingCreatedEmail(Seminar s) {
        if (s == null || !validEmail(s.getEmail())) return CompletableFuture.completedFuture(false);
        String to = s.getEmail();
        String subject = "Seminar booking received — " + safe(s.getHallName());

        boolean isApproved = false;
        String status = s.getStatus() == null ? "" : s.getStatus().toUpperCase();
        if ("APPROVED".equals(status)) isApproved = true;
        else {
            String createdBy = s.getCreatedBy() == null ? "" : s.getCreatedBy().trim();
            if ("ADMIN".equalsIgnoreCase(createdBy)) isApproved = true;
        }

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px;background:#fff'>");

        if (isApproved) {
            html.append("<div style='background:#e9f7ef;border:1px solid #c7efd3;padding:10px;border-radius:6px;margin-bottom:12px'>")
                    .append("<strong style='color:#2f8a4b'>Approved & applied by admin</strong>")
                    .append("<div style='font-size:13px;color:#444;margin-top:6px'>Your booking has been approved by the administrator.</div>")
                    .append("</div>");
        }

        html.append("<h2 style='color:#0b5ed7'>Seminar Booking Received</h2>")
                .append("<p>Dear ").append(escape(s.getBookingName() == null ? "User" : s.getBookingName())).append(",</p>")
                .append("<p>Your booking request has been successfully received. Details below:</p>")
                .append("<table style='width:100%;border-collapse:collapse'>")
                .append(rowTd("Hall", safe(s.getHallName())))
                .append(rowTd("Date", safe(s.getDate())))
                .append(rowTd("Slot", safe(s.getSlot())))
                .append(rowTd("Event", safe(s.getSlotTitle())))
                .append(rowTd("Department / Contact", safe(s.getDepartment()) + " / " + safe(s.getPhone())))
                .append("</table>");

        if (isApproved)
            html.append("<p style='margin-top:12px;color:#333'>Status: <strong style='color:green'>APPROVED</strong></p>");
        else
            html.append("<p style='margin-top:12px;color:#333'>Our admin team will review this request and notify you when the status changes.</p>");

        html.append("<hr style='border:none;border-top:1px solid #eee'/>")
                .append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName))
                .append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("<p style='font-size:13px'>Visit: <a href='").append(websiteUrl).append("'>").append(websiteUrl).append("</a></p>")
                .append("</div></body></html>");

        return runSendHtmlMailAsync(to, subject, html.toString());
    }

    // -------------------- Seminar removed (async) --------------------
    @Async
    public CompletableFuture<Boolean> sendSeminarRemovedEmail(Seminar s) {
        if (s == null || !validEmail(s.getEmail())) return CompletableFuture.completedFuture(false);
        String to = s.getEmail();
        String subject = "Seminar booking removed — " + safe(s.getHallName());

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>")
                .append("<h2 style='color:#d9534f'>Seminar Booking Removed</h2>")
                .append("<p>Dear ").append(escape(s.getBookingName() == null ? "User" : s.getBookingName())).append(",</p>")
                .append("<p>Your booking has been removed from the portal. Details below:</p>")
                .append("<table style='width:100%;border-collapse:collapse'>")
                .append(rowTd("Hall", safe(s.getHallName())))
                .append(rowTd("Date", safe(s.getDate())))
                .append(rowTd("Slot", safe(s.getSlot())))
                .append(rowTd("Event", safe(s.getSlotTitle())))
                .append("</table>")
                .append("<p>If you have questions, please contact your department or the college administration.</p>")
                .append("<hr style='border:none;border-top:1px solid #eee'/>")
                .append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName))
                .append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("</div></body></html>");

        return runSendHtmlMailAsync(to, subject, html.toString());
    }

    // -------------------- Account removed (async) --------------------
    // This is the method your controllers were calling (added here).
    @Async
    public CompletableFuture<Boolean> sendAccountRemovedEmail(User user) {
        if (user == null || !validEmail(user.getEmail())) return CompletableFuture.completedFuture(false);
        String to = user.getEmail();
        String subject = "Account removed from Seminar Booking portal — " + companyName;

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>")
                .append("<h2 style='color:#d9534f'>Account Removed</h2>")
                .append("<p>Dear ").append(escape(user.getName() == null ? "User" : user.getName())).append(",</p>")
                .append("<p>We are writing to confirm that your account associated with <strong>").append(escape(user.getEmail())).append("</strong> has been removed from the Seminar Booking portal.</p>")
                .append("<p>If you believe this was done in error, please contact your department or the administration to request assistance.</p>")
                .append("<hr style='border:none;border-top:1px solid #eee'/>")
                .append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName))
                .append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("<p style='font-size:13px'>Visit: <a href='").append(websiteUrl).append("'>").append(websiteUrl).append("</a></p>")
                .append("</div></body></html>");

        return runSendHtmlMailAsync(to, subject, html.toString());
    }

    // -------------------- Hall head notifications (async) --------------------
    @Async
    public CompletableFuture<Boolean> sendHallHeadBookingCreatedEmail(HallOperator head, Seminar s) {
        if (head == null || !validEmail(head.getHeadEmail()) || s == null) return CompletableFuture.completedFuture(false);
        String subject = "New booking requested for " + safe(s.getHallName());

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>")
                .append("<h2 style='color:#0b5ed7'>New Booking Request</h2>")
                .append("<p>Hello ").append(escape(head.getHeadName())).append(",</p>")
                .append("<p>A new booking has been created for your hall. Details:</p>")
                .append("<table style='width:100%;border-collapse:collapse'>")
                .append(rowTd("Hall", safe(s.getHallName())))
                .append(rowTd("Date", safe(s.getDate())))
                .append(rowTd("Slot", safe(s.getSlot())))
                .append(rowTd("Event", safe(s.getSlotTitle())))
                .append(rowTd("Booked by", safe(s.getBookingName()) + " (" + safe(s.getEmail()) + ")"))
                .append(rowTd("Location", "Seminar Hall Block A, New Horizon College (please coordinate internally)"))
                .append("</table>")
                .append("<p>Please coordinate with the requester as needed. You may view full details in the admin portal.</p>")
                .append("<hr style='border:none;border-top:1px solid #eee'/>")
                .append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("</div></body></html>");

        return runSendHtmlMailAsync(head.getHeadEmail(), subject, html.toString());
    }

    @Async
    public CompletableFuture<Boolean> sendHallHeadBookingApprovedEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || !validEmail(head.getHeadEmail()) || s == null) return CompletableFuture.completedFuture(false);
        String subject = "Booking confirmed for " + safe(s.getHallName());
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>")
                .append("<h2 style='color:#28a745'>Booking Confirmed</h2>")
                .append("<p>Hello ").append(escape(head.getHeadName())).append(",</p>")
                .append("<p>The following booking has been confirmed by admin:</p>")
                .append("<table style='width:100%;border-collapse:collapse'>")
                .append(rowTd("Hall", safe(s.getHallName())))
                .append(rowTd("Date", safe(s.getDate())))
                .append(rowTd("Slot", safe(s.getSlot())))
                .append(rowTd("Event", safe(s.getSlotTitle())))
                .append(rowTd("Booked by", safe(s.getBookingName()) + " (" + safe(s.getEmail()) + ")"))
                .append(rowTd("Location", "Seminar Hall Block A, New Horizon College"));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Admin remarks", escape(reason)));
        html.append("</table>")
                .append("<p>Please prepare the hall accordingly and coordinate with the requester.</p>")
                .append("<hr style='border:none;border-top:1px solid #eee'/>")
                .append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("</div></body></html>");
        return runSendHtmlMailAsync(head.getHeadEmail(), subject, html.toString());
    }

    @Async
    public CompletableFuture<Boolean> sendHallHeadBookingRejectedEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || !validEmail(head.getHeadEmail()) || s == null) return CompletableFuture.completedFuture(false);
        String subject = "Booking rejected for " + safe(s.getHallName());
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>")
                .append("<h2 style='color:#dc3545'>Booking Rejected</h2>")
                .append("<p>Hello ").append(escape(head.getHeadName())).append(",</p>")
                .append("<p>The following booking was rejected by admin:</p>")
                .append("<table style='width:100%;border-collapse:collapse'>")
                .append(rowTd("Hall", safe(s.getHallName())))
                .append(rowTd("Date", safe(s.getDate())))
                .append(rowTd("Slot", safe(s.getSlot())))
                .append(rowTd("Event", safe(s.getSlotTitle())))
                .append(rowTd("Booked by", safe(s.getBookingName()) + " (" + safe(s.getEmail()) + ")"));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Reason", escape(reason)));
        html.append("</table>")
                .append("<hr style='border:none;border-top:1px solid #eee'/>")
                .append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("</div></body></html>");
        return runSendHtmlMailAsync(head.getHeadEmail(), subject, html.toString());
    }

    @Async
    public CompletableFuture<Boolean> sendHallHeadBookingCancelledEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || !validEmail(head.getHeadEmail()) || s == null) return CompletableFuture.completedFuture(false);
        String subject = "Booking cancelled for " + safe(s.getHallName());
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>")
                .append("<h2 style='color:#fd7e14'>Booking Cancelled</h2>")
                .append("<p>Hello ").append(escape(head.getHeadName())).append(",</p>")
                .append("<p>The following booking has been cancelled:</p>")
                .append("<table style='width:100%;border-collapse:collapse'>")
                .append(rowTd("Hall", safe(s.getHallName())))
                .append(rowTd("Date", safe(s.getDate())))
                .append(rowTd("Slot", safe(s.getSlot())))
                .append(rowTd("Event", safe(s.getSlotTitle())));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Reason", escape(reason)));
        html.append("</table>")
                .append("<hr style='border:none;border-top:1px solid #eee'/>")
                .append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("</div></body></html>");
        return runSendHtmlMailAsync(head.getHeadEmail(), subject, html.toString());
    }

    // -------------------- Generic status notification (async) --------------------
    @Async
    public CompletableFuture<Boolean> sendStatusNotification(String toEmail, Seminar seminar, String newStatus, String reason) {
        if (!validEmail(toEmail)) return CompletableFuture.completedFuture(false);
        String status = newStatus == null ? "UPDATE" : newStatus.toUpperCase();
        String subject = "Seminar Booking Update — " + (seminar == null ? "" : safe(seminar.getHallName()));
        if ("APPROVED".equals(status)) subject = "Seminar Booking Confirmed — " + safe(seminar.getHallName());
        if ("REJECTED".equals(status)) subject = "Seminar Booking Rejected — " + safe(seminar.getHallName());
        if ("CANCELLED".equals(status)) subject = "Seminar Booking Cancelled — " + safe(seminar.getHallName());
        if ("CANCEL_REQUESTED".equals(status)) subject = "Seminar Cancellation Requested — " + safe(seminar.getHallName());

        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#111;background:#fff;padding:18px;\">")
                .append("<div style=\"max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:18px;border-radius:8px;\">")
                .append("<h2 style=\"color:#0b5ed7;margin:0 0 12px 0;\">Seminar Booking Notification</h2>")
                .append("<p>Hello,</p>")
                .append("<p>There is an update on your seminar booking:</p>")
                .append("<table style=\"width:100%;border-collapse:collapse;margin-top:10px\">")
                .append(rowTd("Hall", safe(seminar.getHallName())))
                .append(rowTd("Date", safe(seminar.getDate())))
                .append(rowTd("Slot", safe(seminar.getSlot())))
                .append(rowTd("Event", safe(seminar.getSlotTitle())))
                .append(rowTd("Status", escape(status)));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Remarks", escape(reason)));
        html.append("</table>")
                .append("<div style=\"margin-top:14px\">");

        switch (status) {
            case "APPROVED":
                html.append("<p>Your booking has been <strong style=\"color:green\">APPROVED</strong>. Please download your booking card or contact the admin for more details.</p>");
                break;
            case "REJECTED":
                html.append("<p>Your booking request has been <strong style=\"color:red\">REJECTED</strong>.</p>");
                break;
            case "CANCEL_REQUESTED":
                html.append("<p>A cancellation has been requested. Admin will review it shortly.</p>");
                break;
            case "CANCELLED":
                html.append("<p>Your booking has been <strong style=\"color:orange\">CANCELLED</strong> by the admin.</p>");
                break;
            default:
                html.append("<p>The booking status has changed to: <strong>").append(escape(status)).append("</strong>.</p>");
                break;
        }

        html.append("</div>")
                .append("<hr style=\"border:none;border-top:1px solid #eee;margin:18px 0\"/>")
                .append("<p style=\"font-size:13px;color:#444\">Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>")
                .append("<p style=\"font-size:13px\">Visit: <a href=\"").append(websiteUrl).append("\">").append(websiteUrl).append("</a></p>")
                .append("</div></body></html>");

        return runSendHtmlMailAsync(toEmail, subject, html.toString());
    }

    // -------------------- low-level HTML sender (synchronous) --------------------
    private boolean sendHtmlMail(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // send HTML
            mailSender.send(message);
            logger.info("[EmailService] HTML email sent to {} subject={}", toEmail, subject);
            return true;
        } catch (MailException ex) {
            logger.error("[EmailService] Failed to send HTML email to {} subject={}: {}", toEmail, subject, ex.getMessage(), ex);
            return false;
        } catch (Exception ex) {
            logger.error("[EmailService] Unexpected error sending HTML email to {} subject={}: {}", toEmail, subject, ex.getMessage(), ex);
            return false;
        }
    }

    // -------------------- helper wrapper to run synchronous sendHtmlMail in async methods --------------------
    private CompletableFuture<Boolean> runSendHtmlMailAsync(String toEmail, String subject, String htmlBody) {
        try {
            boolean ok = sendHtmlMail(toEmail, subject, htmlBody);
            return CompletableFuture.completedFuture(ok);
        } catch (TaskRejectedException tre) {
            logger.error("[EmailService] Async executor rejected task for sending email to {} subject={}", toEmail, subject, tre);
            return CompletableFuture.completedFuture(false);
        } catch (Exception ex) {
            logger.error("[EmailService] Unexpected error wrapping sendHtmlMail: {}", ex.getMessage(), ex);
            return CompletableFuture.completedFuture(false);
        }
    }

    // -------------------- small helpers --------------------
    private String rowTd(String key, String value) {
        return "<tr><td style='padding:6px;border:1px solid #f0f0f0'><strong>" + escape(key) + "</strong></td><td style='padding:6px;border:1px solid #f0f0f0'>" + escape(value) + "</td></tr>";
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // used for plain text OTP
    private String escapePlain(String s) {
        if (s == null) return "";
        return s.replace("\r", "").replace("\n", ""); // avoid header/newline injection
    }

    private boolean validEmail(String e) {
        if (e == null) return false;
        String t = e.trim();
        return !t.isEmpty() && t.contains("@") && t.length() <= 254;
    }
}

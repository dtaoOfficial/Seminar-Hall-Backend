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
 * EmailService using Brevo HTTP API (via BrevoClient).
 * Contains all methods used by controllers so compile succeeds.
 */
@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

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

    // -------------------- OTP (synchronous) --------------------
    public boolean sendOtp(String toEmail, String otp) {
        if (!validEmail(toEmail) || otp == null) {
            logger.warn("sendOtp called with invalid args: toEmail={}, otpNull={}", toEmail, otp == null);
            return false;
        }
        String subject = "Seminar Booking - Password Reset OTP";
        String html = "<html><body style='font-family:Arial,Helvetica,sans-serif;padding:18px'>" +
                "<h2>OTP for Seminar Booking</h2>" +
                "<p>Your OTP is: <strong>" + escape(otp) + "</strong></p>" +
                "<p>Valid for 5 minutes.</p>" +
                "</body></html>";
        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(toEmail), subject, html);
        logger.info("[EmailService] sendOtp -> {} for {}", ok, toEmail);
        return ok;
    }

    // -------------------- Welcome --------------------
    @Async
    public CompletableFuture<Boolean> sendWelcomeEmail(User user) {
        if (user == null || !validEmail(user.getEmail())) return done(false);
        String to = user.getEmail();
        String name = user.getName() == null ? "User" : user.getName();

        String subject = "Welcome to the Seminar Booking Portal — " + companyName;
        String html = "<html><body style='font-family:Arial,Helvetica,sans-serif;color:#111;padding:18px;'>" +
                "<div style='max-width:650px;margin:0 auto;border:1px solid #e6e6e6;padding:18px;border-radius:6px;'>" +
                "<h2 style='color:#0b5ed7'>Welcome to " + escape(companyName) + "</h2>" +
                "<p>Dear " + escape(name) + ",</p>" +
                "<p>Thank you for creating an account on the Seminar Booking portal.</p>" +
                "<p><strong>Account</strong><br/>Email: " + escape(to) + "</p>" +
                "<p>Visit: <a href='" + websiteUrl + "'>" + websiteUrl + "</a></p>" +
                footerHtml() + "</div></body></html>";

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html);
        logger.info("[EmailService] sendWelcomeEmail -> {} for {}", ok, to);
        return done(ok);
    }

    // -------------------- Booking created --------------------
    @Async
    public CompletableFuture<Boolean> sendBookingCreatedEmail(Seminar s) {
        if (s == null || !validEmail(s.getEmail())) return done(false);
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

        html.append(footerHtml())
                .append("</div></body></html>");

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html.toString());
        logger.info("[EmailService] sendBookingCreatedEmail -> {} for {}", ok, to);
        return done(ok);
    }

    // -------------------- Seminar removed --------------------
    @Async
    public CompletableFuture<Boolean> sendSeminarRemovedEmail(Seminar s) {
        if (s == null || !validEmail(s.getEmail())) return done(false);
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
                .append(footerHtml())
                .append("</div></body></html>");

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html.toString());
        logger.info("[EmailService] sendSeminarRemovedEmail -> {} for {}", ok, to);
        return done(ok);
    }

    // -------------------- Account removed --------------------
    @Async
    public CompletableFuture<Boolean> sendAccountRemovedEmail(User user) {
        if (user == null || !validEmail(user.getEmail())) return done(false);
        String to = user.getEmail();
        String subject = "Account removed from Seminar Booking portal — " + companyName;

        String html = "<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>" +
                "<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>" +
                "<h2 style='color:#d9534f'>Account Removed</h2>" +
                "<p>Dear " + escape(user.getName() == null ? "User" : user.getName()) + ",</p>" +
                "<p>We are writing to confirm that your account associated with <strong>" + escape(user.getEmail()) + "</strong> has been removed from the Seminar Booking portal.</p>" +
                footerHtml() + "</div></body></html>";

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html);
        logger.info("[EmailService] sendAccountRemovedEmail -> {} for {}", ok, to);
        return done(ok);
    }

    // -------------------- Hall head notifications --------------------
    @Async
    public CompletableFuture<Boolean> sendHallHeadBookingCreatedEmail(HallOperator head, Seminar s) {
        if (head == null || !validEmail(head.getHeadEmail()) || s == null) return done(false);
        String to = head.getHeadEmail();
        String subject = "New booking requested for " + safe(s.getHallName());

        String html = "<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>"
                + "<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>"
                + "<h2 style='color:#0b5ed7'>New Booking Request</h2>"
                + "<p>Hello " + escape(head.getHeadName()) + ",</p>"
                + "<p>A new booking has been created for your hall. Details:</p>"
                + "<table style='width:100%;border-collapse:collapse'>"
                + rowTd("Hall", safe(s.getHallName()))
                + rowTd("Date", safe(s.getDate()))
                + rowTd("Slot", safe(s.getSlot()))
                + rowTd("Event", safe(s.getSlotTitle()))
                + rowTd("Booked by", safe(s.getBookingName()) + " (" + safe(s.getEmail()) + ")")
                + rowTd("Location", "Seminar Hall Block A, New Horizon College (please coordinate internally)")
                + "</table>"
                + "<p>Please coordinate with the requester as needed. You may view full details in the admin portal.</p>"
                + footerHtml() + "</div></body></html>";

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html);
        logger.info("[EmailService] sendHallHeadBookingCreatedEmail -> {} for {}", ok, to);
        return done(ok);
    }

    @Async
    public CompletableFuture<Boolean> sendHallHeadBookingApprovedEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || !validEmail(head.getHeadEmail()) || s == null) return done(false);
        String to = head.getHeadEmail();
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
        html.append("</table>").append(footerHtml()).append("</div></body></html>");

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html.toString());
        logger.info("[EmailService] sendHallHeadBookingApprovedEmail -> {} for {}", ok, to);
        return done(ok);
    }

    @Async
    public CompletableFuture<Boolean> sendHallHeadBookingRejectedEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || !validEmail(head.getHeadEmail()) || s == null) return done(false);
        String to = head.getHeadEmail();
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
        html.append("</table>").append(footerHtml()).append("</div></body></html>");

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html.toString());
        logger.info("[EmailService] sendHallHeadBookingRejectedEmail -> {} for {}", ok, to);
        return done(ok);
    }

    @Async
    public CompletableFuture<Boolean> sendHallHeadBookingCancelledEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || !validEmail(head.getHeadEmail()) || s == null) return done(false);
        String to = head.getHeadEmail();
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
        html.append("</table>").append(footerHtml()).append("</div></body></html>");

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html.toString());
        logger.info("[EmailService] sendHallHeadBookingCancelledEmail -> {} for {}", ok, to);
        return done(ok);
    }

    // -------------------- Generic status notification --------------------
    @Async
    public CompletableFuture<Boolean> sendStatusNotification(String toEmail, Seminar seminar, String newStatus, String reason) {
        if (!validEmail(toEmail)) return done(false);
        String status = newStatus == null ? "UPDATE" : newStatus.toUpperCase();
        String subject = "Seminar Booking Update — " + (seminar == null ? "" : safe(seminar.getHallName()));
        if ("APPROVED".equals(status)) subject = "Seminar Booking Confirmed — " + safe(seminar.getHallName());
        if ("REJECTED".equals(status)) subject = "Seminar Booking Rejected — " + safe(seminar.getHallName());
        if ("CANCELLED".equals(status)) subject = "Seminar Booking Cancelled — " + safe(seminar.getHallName());
        if ("CANCEL_REQUESTED".equals(status)) subject = "Seminar Cancellation Requested — " + safe(seminar.getHallName());

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;color:#111;background:#fff;padding:18px;'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:18px;border-radius:8px;'>")
                .append("<h2 style='color:#0b5ed7;margin:0 0 12px 0;'>Seminar Booking Notification</h2>")
                .append("<p>Hello,</p>")
                .append("<p>There is an update on your seminar booking:</p>")
                .append("<table style='width:100%;border-collapse:collapse;margin-top:10px'>")
                .append(rowTd("Hall", safe(seminar.getHallName())))
                .append(rowTd("Date", safe(seminar.getDate())))
                .append(rowTd("Slot", safe(seminar.getSlot())))
                .append(rowTd("Event", safe(seminar.getSlotTitle())))
                .append(rowTd("Status", escape(status)));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Remarks", escape(reason)));
        html.append("</table>").append("<div style='margin-top:14px'>");

        switch (status) {
            case "APPROVED":
                html.append("<p>Your booking has been <strong style='color:green'>APPROVED</strong>.</p>");
                break;
            case "REJECTED":
                html.append("<p>Your booking request has been <strong style='color:red'>REJECTED</strong>.</p>");
                break;
            case "CANCEL_REQUESTED":
                html.append("<p>A cancellation has been requested. Admin will review it shortly.</p>");
                break;
            case "CANCELLED":
                html.append("<p>Your booking has been <strong style='color:orange'>CANCELLED</strong> by the admin.</p>");
                break;
            default:
                html.append("<p>The booking status has changed to: <strong>").append(escape(status)).append("</strong>.</p>");
                break;
        }

        html.append("</div>").append(footerHtml()).append("</div></body></html>");

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(toEmail), subject, html.toString());
        logger.info("[EmailService] sendStatusNotification -> {} for {}", ok, toEmail);
        return done(ok);
    }

    // -------------------- helpers --------------------
    private CompletableFuture<Boolean> done(boolean val) {
        return CompletableFuture.completedFuture(val);
    }

    private String rowTd(String key, String value) {
        return "<tr><td style='padding:6px;border:1px solid #f0f0f0'><strong>" + escape(key) + "</strong></td><td style='padding:6px;border:1px solid #f0f0f0'>" + escape(value) + "</td></tr>";
    }

    private String footerHtml() {
        return "<hr style='border:none;border-top:1px solid #eee'/>" +
                "<p style='font-size:13px'>Owner / Admin: <strong>" + escape(ownerName) + "</strong><br/>Company: <strong>" + escape(companyName) + "</strong></p>" +
                "<p style='font-size:13px'>Visit: <a href='" + websiteUrl + "'>" + websiteUrl + "</a></p>";
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private boolean validEmail(String e) {
        if (e == null) return false;
        String t = e.trim();
        return !t.isEmpty() && t.contains("@") && t.length() <= 254;
    }

    // -------------------- New Feature: Welcome email for new Hall Operator --------------------
    @Async
    public CompletableFuture<Boolean> sendWelcomeEmailForOperator(HallOperator operator) {
        if (operator == null || !validEmail(operator.getHeadEmail())) return done(false);
        String to = operator.getHeadEmail();
        String name = operator.getHeadName() == null ? "Operator" : operator.getHeadName();

        String subject = "Welcome as Hall Operator — " + companyName;

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:18px;color:#111;'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:18px;border-radius:8px;'>")
                .append("<h2 style='color:#0b5ed7;'>Welcome to ").append(escape(companyName)).append("</h2>")
                .append("<p>Hello ").append(escape(name)).append(",</p>")
                .append("<p>You have been successfully added as a Hall Operator.</p>")
                .append("<p><strong>Assigned Halls:</strong></p><ul>");

        if (operator.getHallNames() != null && !operator.getHallNames().isEmpty()) {
            for (String hall : operator.getHallNames()) {
                html.append("<li>").append(escape(hall)).append("</li>");
            }
        } else {
            html.append("<li>No halls assigned yet</li>");
        }

        html.append("</ul>")
                .append("<p>You will receive notifications whenever a seminar booking is made in your assigned halls.</p>")
                .append("<p>Access the admin portal here: <a href='").append(websiteUrl).append("'>").append(websiteUrl).append("</a></p>")
                .append(footerHtml())
                .append("</div></body></html>");

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, List.of(to), subject, html.toString());
        logger.info("[EmailService] sendWelcomeEmailForOperator -> {} for {}", ok, to);
        return done(ok);
    }

    // -------------------- New Feature: Notify all hall operators on booking --------------------
    @Async
    public CompletableFuture<Boolean> notifyOperatorBookingEmail(List<HallOperator> operators, Seminar seminar) {
        if (operators == null || operators.isEmpty() || seminar == null) return done(false);

        String subject = "New Seminar Booking — " + safe(seminar.getHallName());
        StringBuilder html = new StringBuilder();

        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:18px;color:#111;'>")
                .append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:18px;border-radius:8px;'>")
                .append("<h2 style='color:#0b5ed7;'>New Booking Created</h2>")
                .append("<p>A new seminar booking has been made. Details below:</p>")
                .append("<table style='width:100%;border-collapse:collapse;'>")
                .append(rowTd("Hall", safe(seminar.getHallName())))
                .append(rowTd("Date", safe(seminar.getDate())))
                .append(rowTd("Slot", safe(seminar.getSlot())))
                .append(rowTd("Event", safe(seminar.getSlotTitle())))
                .append(rowTd("Booked By", safe(seminar.getBookingName()) + " (" + safe(seminar.getEmail()) + ")"))
                .append("</table>")
                .append("<p>Please review this booking in your operator dashboard or coordinate as necessary.</p>")
                .append(footerHtml())
                .append("</div></body></html>");

        // collect all valid operator emails
        List<String> emails = operators.stream()
                .map(HallOperator::getHeadEmail)
                .filter(this::validEmail)
                .toList();

        if (emails.isEmpty()) return done(false);

        boolean ok = brevoClient.sendEmail(companyName, mailFrom, emails, subject, html.toString());
        logger.info("[EmailService] notifyOperatorBookingEmail -> sent to {} operators", emails.size());
        return done(ok);
    }


}

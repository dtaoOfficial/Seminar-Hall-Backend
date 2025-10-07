package com.dtao.seminarbooking.service;

import com.dtao.seminarbooking.model.HallOperator;
import com.dtao.seminarbooking.model.Seminar;
import com.dtao.seminarbooking.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@yourdomain.com}")
    private String mailFrom;

    @Value("${app.mail.owner:KURAPARTHI MAHESWAR REDDY}")
    private String ownerName;

    @Value("${app.mail.company:DTAO OFFICIAL}")
    private String companyName;

    @Value("${app.mail.website:https://dtaoofficial.netlify.app/}")
    private String websiteUrl;

    // -------------------- OTP (existing) --------------------
    public boolean sendOtp(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "utf-8");
            String txt = "Your OTP is: " + otp + "\nValid for 5 minutes.\n\nIf you didn't request this, ignore this email.";
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject("Seminar Booking - Password Reset OTP");
            helper.setText(txt, false); // ✅ plain text email
            mailSender.send(message);
            System.out.println("[EmailService] OTP email sent (to=" + toEmail + ", from=" + mailFrom + ")");
            return true;
        } catch (MailException ex) {
            System.err.println("[EmailService] Failed to send OTP email to " + toEmail + ": " + ex.getMessage());
            ex.printStackTrace();
            return false;
        } catch (Exception ex) {
            System.err.println("[EmailService] Unexpected OTP send error: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }


    // -------------------- Welcome (existing) --------------------
    public boolean sendWelcomeEmail(User user) {
        if (user == null || user.getEmail() == null) return false;
        String to = user.getEmail();
        String name = user.getName() == null ? "User" : user.getName();

        String subject = "Welcome to Seminar Booking — " + companyName;
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#111;background:#fff;padding:18px;\">");
        html.append("<div style=\"max-width:650px;margin:0 auto;border:1px solid #e6e6e6;padding:18px;border-radius:6px;\">");
        html.append("<h2 style=\"color:#0b5ed7;margin:0 0 8px 0;\">Welcome to ").append(escape(companyName)).append("</h2>");
        html.append("<p>Hi <strong>").append(escape(name)).append("</strong>,</p>");
        html.append("<p>Thank you for creating an account on the Seminar Booking portal. You can now request halls and manage your bookings.</p>");
        html.append("<p><strong>Account details</strong><br/>Email: ").append(escape(to)).append("</p>");
        html.append("<p>Visit: <a href=\"").append(websiteUrl).append("\">").append(websiteUrl).append("</a></p>");
        html.append("<hr style=\"border:none;border-top:1px solid #eee\"/>");
        html.append("<p style=\"font-size:13px;color:#444\">Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>");
        html.append("Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("<p style=\"font-size:12px;color:#888;margin-top:6px\">If you didn't request this account, ignore this email.</p>");
        html.append("</div></body></html>");

        return sendHtmlMail(to, subject, html.toString());
    }

    // -------------------- Booking created (updated) --------------------
    /**
     * Send booking-received email. If the seminar is already approved (admin created),
     * this method will show a small "Approved & applied by admin" banner so a single
     * email is sufficient.
     *
     * NOTE: detection uses seminar.getStatus() == "APPROVED" OR seminar.getCreatedBy() == "ADMIN".
     */
    public boolean sendBookingCreatedEmail(Seminar s) {
        if (s == null || s.getEmail() == null) return false;
        String to = s.getEmail();
        String subject = "Seminar booking received — " + safe(s.getHallName());

        // determine if admin-applied
        boolean isApproved = false;
        String status = s.getStatus() == null ? "" : s.getStatus().toUpperCase();
        if ("APPROVED".equals(status)) {
            isApproved = true;
        } else {
            // also allow detection by createdBy header if your code sets it
            String createdBy = s.getCreatedBy() == null ? "" : s.getCreatedBy().trim();
            if ("ADMIN".equalsIgnoreCase(createdBy)) isApproved = true;
        }

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>");
        html.append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px;background:#fff'>");

        // If already approved show top banner
        if (isApproved) {
            html.append("<div style='background:#e9f7ef;border:1px solid #c7efd3;padding:10px;border-radius:6px;margin-bottom:12px'>");
            html.append("<strong style='color:#2f8a4b'>Approved & applied by admin</strong>");
            html.append("<div style='font-size:13px;color:#444;margin-top:6px'>This booking was created by the admin and has been approved automatically.</div>");
            html.append("</div>");
        }

        html.append("<h2 style='color:#0b5ed7'>Seminar Booking Received</h2>");
        html.append("<p>Dear ").append(escape(s.getBookingName() == null ? "User" : s.getBookingName())).append(",</p>");
        html.append("<p>Your booking request was successfully received. Details:</p>");
        html.append("<table style='width:100%;border-collapse:collapse'>");
        html.append(rowTd("Hall", safe(s.getHallName())));
        html.append(rowTd("Date", safe(s.getDate())));
        html.append(rowTd("Slot", safe(s.getSlot())));
        html.append(rowTd("Event", safe(s.getSlotTitle())));
        html.append(rowTd("Department / Contact", safe(s.getDepartment()) + " / " + safe(s.getPhone())));
        html.append("</table>");

        // If created by admin and approved, add a small note for clarity (redundant with banner but helpful)
        if (isApproved) {
            html.append("<p style='margin-top:12px;color:#333'>Status: <strong style='color:green'>APPROVED</strong> (applied by admin)</p>");
        } else {
            html.append("<p style='margin-top:12px;color:#333'>Admin will review and you will be notified when the status changes.</p>");
        }

        html.append("<hr style='border:none;border-top:1px solid #eee'/>");
        html.append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("<p style='font-size:13px'>Visit: <a href='").append(websiteUrl).append("'>").append(websiteUrl).append("</a></p>");
        html.append("</div></body></html>");

        return sendHtmlMail(to, subject, html.toString());
    }

    // -------------------- Seminar removed (existing) --------------------
    public boolean sendSeminarRemovedEmail(Seminar s) {
        if (s == null || s.getEmail() == null) return false;
        String to = s.getEmail();
        String subject = "Seminar booking removed — " + safe(s.getHallName());

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>");
        html.append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>");
        html.append("<h2 style='color:#d9534f'>Seminar Booking Removed</h2>");
        html.append("<p>Your booking has been removed from the portal. Details:</p>");
        html.append("<table style='width:100%;border-collapse:collapse'>");
        html.append(rowTd("Hall", safe(s.getHallName())));
        html.append(rowTd("Date", safe(s.getDate())));
        html.append(rowTd("Slot", safe(s.getSlot())));
        html.append(rowTd("Event", safe(s.getSlotTitle())));
        html.append("</table>");
        html.append("<p>If you have questions, please contact your department or the college administration.</p>");
        html.append("<hr style='border:none;border-top:1px solid #eee'/>");
        html.append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("</div></body></html>");

        return sendHtmlMail(to, subject, html.toString());
    }

    // -------------------- Account removed (existing) --------------------
    public boolean sendAccountRemovedEmail(User user) {
        if (user == null || user.getEmail() == null) return false;
        String to = user.getEmail();
        String subject = "Your account removed from Seminar Booking portal";

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>");
        html.append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>");
        html.append("<h2 style='color:#d9534f'>Account Removed</h2>");
        html.append("<p>Dear ").append(escape(user.getName())).append(",</p>");
        html.append("<p>Your account with email <strong>").append(escape(user.getEmail())).append("</strong> has been removed from the Seminar Booking portal.</p>");
        html.append("<p>If you have any questions, contact the college administration at New Horizon College of Engineering.</p>");
        html.append("<hr style='border:none;border-top:1px solid #eee'/>");
        html.append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("<p style='font-size:13px'>Visit: <a href='").append(websiteUrl).append("'>").append(websiteUrl).append("</a></p>");
        html.append("</div></body></html>");

        return sendHtmlMail(to, subject, html.toString());
    }

    // -------------------- Hall head notifications (existing) --------------------
    public boolean sendHallHeadBookingCreatedEmail(HallOperator head, Seminar s) {
        if (head == null || head.getHeadEmail() == null) return false;
        String subject = "New booking requested for " + safe(s.getHallName());
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>");
        html.append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>");
        html.append("<h2 style='color:#0b5ed7'>New Booking Request</h2>");
        html.append("<p>Hello ").append(escape(head.getHeadName())).append(",</p>");
        html.append("<p>A new booking has been created for your hall. Details:</p>");
        html.append("<table style='width:100%;border-collapse:collapse'>");
        html.append(rowTd("Hall", safe(s.getHallName())));
        html.append(rowTd("Date", safe(s.getDate())));
        html.append(rowTd("Slot", safe(s.getSlot())));
        html.append(rowTd("Event", safe(s.getSlotTitle())));
        html.append(rowTd("Booked by", safe(s.getBookingName()) + " (" + safe(s.getEmail()) + ")"));
        html.append(rowTd("Location", "Seminar Hall Block A, New Horizon College (replace with real address)"));
        html.append("</table>");
        html.append("<p>Please coordinate as needed with the requester. You can visit the admin portal to view more details.</p>");
        html.append("<hr style='border:none;border-top:1px solid #eee'/>");
        html.append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("</div></body></html>");

        return sendHtmlMail(head.getHeadEmail(), subject, html.toString());
    }

    public boolean sendHallHeadBookingApprovedEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || head.getHeadEmail() == null) return false;
        String subject = "Booking confirmed for " + safe(s.getHallName());
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>");
        html.append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>");
        html.append("<h2 style='color:#28a745'>Booking Confirmed</h2>");
        html.append("<p>Hello ").append(escape(head.getHeadName())).append(",</p>");
        html.append("<p>The following booking has been confirmed by admin:</p>");
        html.append("<table style='width:100%;border-collapse:collapse'>");
        html.append(rowTd("Hall", safe(s.getHallName())));
        html.append(rowTd("Date", safe(s.getDate())));
        html.append(rowTd("Slot", safe(s.getSlot())));
        html.append(rowTd("Event", safe(s.getSlotTitle())));
        html.append(rowTd("Booked by", safe(s.getBookingName()) + " (" + safe(s.getEmail()) + ")"));
        html.append(rowTd("Location", "Seminar Hall Block A, New Horizon College (replace with real address)"));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Admin remarks", escape(reason)));
        html.append("</table>");
        html.append("<p>Please prepare the hall accordingly and coordinate with the requester.</p>");
        html.append("<hr style='border:none;border-top:1px solid #eee'/>");
        html.append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("</div></body></html>");

        return sendHtmlMail(head.getHeadEmail(), subject, html.toString());
    }

    public boolean sendHallHeadBookingRejectedEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || head.getHeadEmail() == null) return false;
        String subject = "Booking rejected for " + safe(s.getHallName());
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>");
        html.append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>");
        html.append("<h2 style='color:#dc3545'>Booking Rejected</h2>");
        html.append("<p>Hello ").append(escape(head.getHeadName())).append(",</p>");
        html.append("<p>The following booking was rejected by admin:</p>");
        html.append("<table style='width:100%;border-collapse:collapse'>");
        html.append(rowTd("Hall", safe(s.getHallName())));
        html.append(rowTd("Date", safe(s.getDate())));
        html.append(rowTd("Slot", safe(s.getSlot())));
        html.append(rowTd("Event", safe(s.getSlotTitle())));
        html.append(rowTd("Booked by", safe(s.getBookingName()) + " (" + safe(s.getEmail()) + ")"));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Reason", escape(reason)));
        html.append("</table>");
        html.append("<hr style='border:none;border-top:1px solid #eee'/>");
        html.append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("</div></body></html>");

        return sendHtmlMail(head.getHeadEmail(), subject, html.toString());
    }

    public boolean sendHallHeadBookingCancelledEmail(HallOperator head, Seminar s, String reason) {
        if (head == null || head.getHeadEmail() == null) return false;
        String subject = "Booking cancelled for " + safe(s.getHallName());
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,Helvetica,sans-serif;padding:14px;color:#111'>");
        html.append("<div style='max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:16px;border-radius:8px'>");
        html.append("<h2 style='color:#fd7e14'>Booking Cancelled</h2>");
        html.append("<p>Hello ").append(escape(head.getHeadName())).append(",</p>");
        html.append("<p>The following booking has been cancelled:</p>");
        html.append("<table style='width:100%;border-collapse:collapse'>");
        html.append(rowTd("Hall", safe(s.getHallName())));
        html.append(rowTd("Date", safe(s.getDate())));
        html.append(rowTd("Slot", safe(s.getSlot())));
        html.append(rowTd("Event", safe(s.getSlotTitle())));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Reason", escape(reason)));
        html.append("</table>");
        html.append("<hr style='border:none;border-top:1px solid #eee'/>");
        html.append("<p style='font-size:13px'>Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("</div></body></html>");

        return sendHtmlMail(head.getHeadEmail(), subject, html.toString());
    }

    // -------------------- Generic status notification (existing) --------------------
    public boolean sendStatusNotification(String toEmail, Seminar seminar, String newStatus, String reason) {
        if (toEmail == null || toEmail.isBlank()) return false;

        String status = newStatus == null ? "UPDATE" : newStatus.toUpperCase();
        String subject = "Seminar Booking Update — " + (seminar == null ? "" : seminar.getHallName());
        if ("APPROVED".equals(status)) subject = "Seminar Booking Confirmed — " + safe(seminar.getHallName());
        if ("REJECTED".equals(status)) subject = "Seminar Booking Rejected — " + safe(seminar.getHallName());
        if ("CANCELLED".equals(status)) subject = "Seminar Booking Cancelled — " + safe(seminar.getHallName());
        if ("CANCEL_REQUESTED".equals(status)) subject = "Seminar Cancellation Requested — " + safe(seminar.getHallName());

        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#111;background:#fff;padding:18px;\">");
        html.append("<div style=\"max-width:720px;margin:0 auto;border:1px solid #eaeaea;padding:18px;border-radius:8px;\">");
        html.append("<h2 style=\"color:#0b5ed7;margin:0 0 12px 0;\">Seminar Booking Notification</h2>");
        html.append("<p>Hello,</p>");
        html.append("<p>There is an update on your seminar booking:</p>");
        html.append("<table style=\"width:100%;border-collapse:collapse;margin-top:10px\">");
        html.append(rowTd("Hall", safe(seminar.getHallName())));
        html.append(rowTd("Date", safe(seminar.getDate())));
        html.append(rowTd("Slot", safe(seminar.getSlot())));
        html.append(rowTd("Event", safe(seminar.getSlotTitle())));
        html.append(rowTd("Status", escape(status)));
        if (reason != null && !reason.isBlank()) html.append(rowTd("Remarks", escape(reason)));
        html.append("</table>");
        html.append("<div style=\"margin-top:14px\">");
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
        html.append("</div>");
        html.append("<hr style=\"border:none;border-top:1px solid #eee;margin:18px 0\"/>");
        html.append("<p style=\"font-size:13px;color:#444\">Owner / Admin: <strong>").append(escape(ownerName)).append("</strong><br/>Company: <strong>").append(escape(companyName)).append("</strong></p>");
        html.append("<p style=\"font-size:13px\">Visit: <a href=\"").append(websiteUrl).append("\">").append(websiteUrl).append("</a></p>");
        html.append("</div></body></html>");

        return sendHtmlMail(toEmail, subject, html.toString());
    }

    // -------------------- Low-level HTML sender --------------------
    private boolean sendHtmlMail(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "utf-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=utf-8");
            mailSender.send(message);
            System.out.println("[EmailService] HTML email sent to " + toEmail + " subject=" + subject);
            return true;
        } catch (MailException ex) {
            System.err.println("[EmailService] Failed to send HTML email to " + toEmail + ": " + ex.getMessage());
            ex.printStackTrace();
            return false;
        } catch (Exception ex) {
            System.err.println("[EmailService] Unexpected error sending HTML email to " + toEmail + ": " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    // -------------------- helpers --------------------
    private String rowTd(String key, String value) {
        return "<tr><td style='padding:6px;border:1px solid #f0f0f0'><strong>" + escape(key) + "</strong></td><td style='padding:6px;border:1px solid #f0f0f0'>" + escape(value) + "</td></tr>";
    }

    private String safe(String s) {
        return s == null ? "—" : s;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

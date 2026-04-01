package com.workshop.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends HTML email notifications for booking lifecycle events.
 *
 * Set {@code notification.email.enabled=true} and configure {@code spring.mail.*}
 * to activate real delivery. When disabled (default in dev), emails are logged
 * at DEBUG level instead so the rest of the flow is exercised without an SMTP server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${notification.email.enabled:false}")
    private boolean enabled;

    @Value("${notification.email.from:noreply@ticketbooking.local}")
    private String from;

    /**
     * Send a plain HTML email.
     *
     * @param to        recipient address (derived from customerId in demo)
     * @param subject   email subject
     * @param htmlBody  HTML body
     */
    public void send(String to, String subject, String htmlBody) {
        if (!enabled) {
            log.debug("Email disabled — skipping delivery to={} subject={}", to, subject);
            return;
        }
        if (to == null || to.isBlank() || to.equals("unknown")) {
            log.debug("No recipient address for email subject={}", subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("Email sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.warn("Failed to send email to={} subject={}: {}", to, subject, e.getMessage());
        }
    }

    // ── Convenience factory methods ───────────────────────────────────────────

    public void sendBookingConfirmed(String customerId, String bookingId, String seatNumber) {
        send(toAddress(customerId),
                "Your booking is confirmed — " + bookingId,
                html("Booking Confirmed",
                        "Your booking <b>" + bookingId + "</b> for seat <b>" + seatNumber + "</b> has been confirmed.",
                        "#28a745"));
    }

    public void sendTicketIssued(String customerId, String bookingId, String ticketNumber) {
        send(toAddress(customerId),
                "Your ticket is ready — " + ticketNumber,
                html("Ticket Issued",
                        "Your ticket <b>" + ticketNumber + "</b> for booking <b>" + bookingId + "</b> is ready. Enjoy the show!",
                        "#007bff"));
    }

    public void sendBookingCancelled(String customerId, String bookingId, String reason) {
        send(toAddress(customerId),
                "Your booking has been cancelled — " + bookingId,
                html("Booking Cancelled",
                        "Your booking <b>" + bookingId + "</b> has been cancelled. Reason: " + reason,
                        "#dc3545"));
    }

    public void sendPaymentRefunded(String customerId, String bookingId, String reason) {
        send(toAddress(customerId),
                "Payment refunded for booking — " + bookingId,
                html("Payment Refunded",
                        "Your payment for booking <b>" + bookingId + "</b> has been refunded. Reason: " + reason,
                        "#fd7e14"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** In the demo, customerId is the username; derive a local email address from it. */
    private String toAddress(String customerId) {
        if (customerId == null || customerId.isBlank() || customerId.equals("unknown")) return "unknown";
        return customerId.contains("@") ? customerId : customerId + "@ticketbooking.local";
    }

    private String html(String title, String body, String accentColor) {
        return """
                <!DOCTYPE html>
                <html><body style="font-family:sans-serif;max-width:600px;margin:auto;padding:24px">
                  <div style="border-left:4px solid %s;padding-left:16px">
                    <h2 style="color:%s;margin-top:0">%s</h2>
                    <p>%s</p>
                  </div>
                  <hr style="margin-top:32px"/>
                  <p style="font-size:12px;color:#888">Ticket Booking Workshop — automated notification</p>
                </body></html>
                """.formatted(accentColor, accentColor, title, body);
    }
}

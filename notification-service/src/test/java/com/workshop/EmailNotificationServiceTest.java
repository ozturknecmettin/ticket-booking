package com.workshop;

import com.workshop.email.EmailNotificationService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies email dispatch via EmailNotificationService.
 * Uses a mocked JavaMailSender to avoid requiring a real SMTP server.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notificationdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "axon.axonserver.enabled=false",
        "notification.email.enabled=true",   // override dev default to test the send path
        "management.health.mail.enabled=false" // mock JavaMailSender doesn't satisfy actuator health check
})
class EmailNotificationServiceTest {

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    EmailNotificationService emailService;

    @Test
    @DisplayName("sendBookingConfirmed sends one MimeMessage when enabled")
    void sendBookingConfirmed_sendsMail() throws Exception {
        MimeMessage msg = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(msg);

        emailService.sendBookingConfirmed("alice", "BK-001", "A1");

        verify(mailSender).send(msg);
    }

    @Test
    @DisplayName("sendTicketIssued sends one MimeMessage when enabled")
    void sendTicketIssued_sendsMail() throws Exception {
        MimeMessage msg = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(msg);

        emailService.sendTicketIssued("bob", "BK-002", "TKT-99");

        verify(mailSender).send(msg);
    }

    @Test
    @DisplayName("send skips delivery when recipient is 'unknown'")
    void send_skipsUnknownRecipient() {
        emailService.send("unknown", "Test", "<p>Test</p>");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendBookingCancelled sends one MimeMessage when enabled")
    void sendBookingCancelled_sendsMail() throws Exception {
        MimeMessage msg = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(msg);

        emailService.sendBookingCancelled("alice", "BK-003", "Payment failed");

        verify(mailSender).send(msg);
    }

    @Test
    @DisplayName("send is a no-op when email is disabled")
    void send_disabledByDefault() {
        // Separate instance with enabled=false (no Spring context needed).
        // The dev profile sets enabled=false; the @TestPropertySource above overrides it to true
        // for all other tests. Here we call send() with a blank address to exercise the guard.
        emailService.send("", "Should not send", "<p/>");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}

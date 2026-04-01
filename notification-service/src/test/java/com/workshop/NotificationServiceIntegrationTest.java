package com.workshop;

import com.workshop.events.BookingEvents;
import com.workshop.events.PaymentEvents;
import com.workshop.projection.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notificationdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.health.mail.enabled=false",
        "axon.axonserver.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NotificationServiceIntegrationTest {

    @MockBean
    JavaMailSender mailSender; // prevent Spring from connecting to an SMTP server in tests

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private org.axonframework.eventhandling.gateway.EventGateway eventGateway;

    @BeforeEach
    void clearNotifications() {
        notificationRepository.deleteAll();
    }

    @Test
    void bookingConfirmedEvent_createsNotification() throws Exception {
        String bookingId = "bk-conf-" + UUID.randomUUID().toString().substring(0, 6);
        eventGateway.publish(new BookingEvents.BookingConfirmed(
                bookingId, "show-001", "customer-test-001"));

        Thread.sleep(300);

        var notifications = notificationRepository.findByBookingId(bookingId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType()).isEqualTo("BOOKING_CONFIRMED");
    }

    @Test
    void ticketIssuedEvent_createsNotification() throws Exception {
        String bookingId = "bk-tkt-" + UUID.randomUUID().toString().substring(0, 6);
        eventGateway.publish(new BookingEvents.TicketIssued(
                bookingId, "TKT-XYZ", "customer-test-002"));

        Thread.sleep(300);

        var notifications = notificationRepository.findByBookingId(bookingId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType()).isEqualTo("TICKET_ISSUED");
        assertThat(notifications.get(0).getMessage()).contains("TKT-XYZ");
    }

    @Test
    void bookingCancelledEvent_createsNotification() throws Exception {
        String bookingId = "bk-cxl-" + UUID.randomUUID().toString().substring(0, 6);
        eventGateway.publish(new BookingEvents.BookingCancelled(
                bookingId, "show-001", "customer-test-003", "Payment failed"));

        Thread.sleep(300);

        var notifications = notificationRepository.findByBookingId(bookingId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType()).isEqualTo("BOOKING_CANCELLED");
        assertThat(notifications.get(0).getMessage()).contains("Payment failed");
    }

    @Test
    void paymentRefundedEvent_createsNotification() throws Exception {
        String bookingId = "bk-ref-" + UUID.randomUUID().toString().substring(0, 6);
        eventGateway.publish(new PaymentEvents.PaymentRefunded(
                "pay-" + UUID.randomUUID().toString().substring(0, 6), bookingId, "customer-test-004", "Refunded by customer"));

        Thread.sleep(300);

        var notifications = notificationRepository.findByBookingId(bookingId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType()).isEqualTo("PAYMENT_REFUNDED");
        assertThat(notifications.get(0).getMessage()).contains("Refunded by customer");
    }

    @Test
    void getRecentNotifications_returnsOk() throws Exception {
        mockMvc.perform(get("/api/notifications/recent"))
                .andExpect(status().isOk());
    }

    @Test
    void getNotificationsByCustomer_returnsNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications/customer/customer-001"))
                .andExpect(status().isOk());
    }

    @Test
    void getNotificationsByBooking() throws Exception {
        mockMvc.perform(get("/api/notifications/booking/booking-001"))
                .andExpect(status().isOk());
    }

    @Test
    void bookingAmendedEvent_createsNotification() throws Exception {
        String bookingId = "bk-amd-" + UUID.randomUUID().toString().substring(0, 6);
        eventGateway.publish(new BookingEvents.BookingAmended(
                bookingId, "show-001", "customer-test-005"));

        Thread.sleep(300);

        var notifications = notificationRepository.findByBookingId(bookingId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType()).isEqualTo("BOOKING_AMENDED");
        assertThat(notifications.get(0).getMessage()).contains(bookingId);
    }

    @Test
    void sseStream_startsAsyncDispatch() throws Exception {
        // SseEmitter is an open stream — it never "completes", so we only verify
        // that the SSE endpoint registers an async context (i.e. the emitter was created).
        var result = mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(result.getRequest().isAsyncStarted()).isTrue();
    }
}

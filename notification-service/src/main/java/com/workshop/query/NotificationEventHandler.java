package com.workshop.query;

import com.workshop.dto.NotificationResponse;
import com.workshop.email.EmailNotificationService;
import com.workshop.events.BookingEvents;
import com.workshop.events.PaymentEvents;
import com.workshop.projection.NotificationProjection;
import com.workshop.projection.NotificationRepository;
import com.workshop.sse.NotificationSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ProcessingGroup("notification-projections")
public class NotificationEventHandler {

    private final NotificationRepository notificationRepository;
    private final NotificationSseService sseService;
    private final EmailNotificationService emailService;

    @EventHandler
    public void on(BookingEvents.BookingConfirmed event) {
        String message = String.format("Your booking %s has been confirmed!", event.bookingId());
        saveNotification(event.bookingId(), event.customerId(), "BOOKING_CONFIRMED", message);
        emailService.sendBookingConfirmed(event.customerId(), event.bookingId(), "");
        log.info("Notification sent: BOOKING_CONFIRMED for booking {}", event.bookingId());
    }

    @EventHandler
    public void on(BookingEvents.BookingCancelled event) {
        String message = String.format(
                "Your booking %s has been cancelled. Reason: %s", event.bookingId(), event.reason());
        saveNotification(event.bookingId(), event.customerId(), "BOOKING_CANCELLED", message);
        emailService.sendBookingCancelled(event.customerId(), event.bookingId(), event.reason());
        log.info("Notification sent: BOOKING_CANCELLED for booking {}", event.bookingId());
    }

    @EventHandler
    public void on(BookingEvents.TicketIssued event) {
        String message = String.format(
                "Your ticket %s for booking %s is ready!", event.ticketNumber(), event.bookingId());
        saveNotification(event.bookingId(), event.customerId(), "TICKET_ISSUED", message);
        emailService.sendTicketIssued(event.customerId(), event.bookingId(), event.ticketNumber());
        log.info("Notification sent: TICKET_ISSUED for booking {} ticket={}", event.bookingId(), event.ticketNumber());
    }

    @EventHandler
    public void on(BookingEvents.BookingAmended event) {
        String message = String.format("Your booking %s has been updated with new seat selections.", event.bookingId());
        saveNotification(event.bookingId(), event.customerId(), "BOOKING_AMENDED", message);
        log.info("Notification sent: BOOKING_AMENDED for booking {}", event.bookingId());
    }

    @EventHandler
    public void on(PaymentEvents.PaymentRefunded event) {
        String message = String.format(
                "Payment for booking %s has been refunded. Reason: %s", event.bookingId(), event.reason());
        saveNotification(event.bookingId(), event.customerId(), "PAYMENT_REFUNDED", message);
        emailService.sendPaymentRefunded(event.customerId(), event.bookingId(), event.reason());
        log.info("Notification sent: PAYMENT_REFUNDED for booking {}", event.bookingId());
    }

    @QueryHandler
    public List<NotificationProjection> handle(NotificationQueries.GetNotificationsByCustomer query) {
        return notificationRepository.findByCustomerIdOrderBySentAtDesc(query.customerId());
    }

    @QueryHandler
    public List<NotificationProjection> handle(NotificationQueries.GetNotificationsByBooking query) {
        return notificationRepository.findByBookingId(query.bookingId());
    }

    private void saveNotification(String bookingId, String customerId, String type, String message) {
        NotificationProjection saved = notificationRepository.save(NotificationProjection.builder()
                .bookingId(bookingId)
                .customerId(customerId)
                .notificationType(type)
                .message(message)
                .sentAt(Instant.now())
                .build());
        sseService.push(new NotificationResponse(
                saved.getNotificationId(), bookingId, customerId, type, message, saved.getSentAt()));
    }
}

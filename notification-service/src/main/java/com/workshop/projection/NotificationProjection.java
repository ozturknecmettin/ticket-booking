package com.workshop.projection;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id")
    private String notificationId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "notification_type", nullable = false)
    private String notificationType; // BOOKING_CONFIRMED, BOOKING_CANCELLED, TICKET_ISSUED

    @Column(nullable = false)
    private String message;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Version
    private Long version;
}

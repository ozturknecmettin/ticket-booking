package com.workshop.projection;

import com.workshop.dto.SeatItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingProjection {

    @Id
    @Column(name = "booking_id")
    private String bookingId;

    @Column(name = "show_id", nullable = false)
    private String showId;

    @Column(name = "seats", columnDefinition = "TEXT", nullable = false)
    @Convert(converter = SeatItemListConverter.class)
    private List<SeatItem> seats;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String status; // INITIATED, CONFIRMED, CANCELLED, TICKET_ISSUED, REFUNDED

    @Column(name = "ticket_number")
    private String ticketNumber;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;
}

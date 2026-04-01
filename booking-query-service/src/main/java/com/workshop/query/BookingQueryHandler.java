package com.workshop.query;

import com.workshop.commands.BookingCommands;
import com.workshop.commands.ShowCommands;
import com.workshop.events.BookingEvents;
import com.workshop.events.PaymentEvents;
import com.workshop.projection.BookingProjection;
import com.workshop.projection.BookingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


@Component
@Slf4j
@ProcessingGroup("booking-projections")
public class BookingQueryHandler {

    private final BookingRepository bookingRepository;
    private final CommandGateway commandGateway;

    private final Counter bookingsInitiated;
    private final Counter bookingsConfirmed;
    private final Counter bookingsCancelled;
    private final Counter ticketsIssued;

    public BookingQueryHandler(BookingRepository bookingRepository,
                               CommandGateway commandGateway,
                               MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository;
        this.commandGateway = commandGateway;
        this.bookingsInitiated = Counter.builder("bookings.initiated")
                .description("Total bookings initiated").register(meterRegistry);
        this.bookingsConfirmed = Counter.builder("bookings.confirmed")
                .description("Total bookings confirmed").register(meterRegistry);
        this.bookingsCancelled = Counter.builder("bookings.cancelled")
                .description("Total bookings cancelled").register(meterRegistry);
        this.ticketsIssued = Counter.builder("tickets.issued")
                .description("Total tickets issued").register(meterRegistry);
    }

    // ── Event handlers ───────────────────────────────────────────────────────

    @EventHandler
    public void on(BookingEvents.BookingInitiated event) {
        Instant now = Instant.now();
        bookingRepository.save(BookingProjection.builder()
                .bookingId(event.bookingId())
                .showId(event.showId())
                .seats(event.seats())
                .customerId(event.customerId())
                .totalAmount(event.totalAmount())
                .status("INITIATED")
                .createdAt(now)
                .updatedAt(now)
                .build());
        bookingsInitiated.increment();
        log.info("Read model created for booking {}", event.bookingId());
    }

    @EventHandler
    public void on(BookingEvents.BookingConfirmed event) {
        bookingRepository.findById(event.bookingId()).ifPresent(p -> {
            p.setStatus("CONFIRMED");
            p.setUpdatedAt(Instant.now());
            bookingRepository.save(p);
        });
        bookingsConfirmed.increment();
    }

    @EventHandler
    public void on(BookingEvents.BookingCancelled event) {
        bookingRepository.findById(event.bookingId()).ifPresent(p -> {
            p.setStatus("CANCELLED");
            p.setUpdatedAt(Instant.now());
            bookingRepository.save(p);
        });
        bookingsCancelled.increment();
    }

    @EventHandler
    public void on(BookingEvents.TicketIssued event) {
        bookingRepository.findById(event.bookingId()).ifPresent(p -> {
            p.setStatus("TICKET_ISSUED");
            p.setTicketNumber(event.ticketNumber());
            p.setUpdatedAt(Instant.now());
            bookingRepository.save(p);
        });
        ticketsIssued.increment();
    }

    @EventHandler
    public void on(PaymentEvents.PaymentRefunded event) {
        bookingRepository.findById(event.bookingId()).ifPresent(booking -> {
            log.info("Payment refunded for booking {} — dispatching RefundBooking", booking.getBookingId());
            commandGateway.send(new BookingCommands.RefundBooking(
                    booking.getBookingId(), event.reason()));
        });
    }

    @EventHandler
    public void on(BookingEvents.BookingAmended event) {
        bookingRepository.findById(event.bookingId()).ifPresent(p -> {
            p.setSeats(event.newSeats());
            p.setUpdatedAt(Instant.now());
            bookingRepository.save(p);
            log.info("Booking {} projection seats updated after amendment", event.bookingId());
        });
    }

    @EventHandler
    public void on(BookingEvents.BookingRefunded event) {
        bookingRepository.findById(event.bookingId()).ifPresent(p -> {
            p.setStatus("REFUNDED");
            p.setUpdatedAt(Instant.now());
            bookingRepository.save(p);
            log.info("Booking {} projection updated to REFUNDED — releasing {} seat(s) on show {}",
                    event.bookingId(), event.seats().size(), event.showId());
        });
        event.seats().forEach(seat ->
                commandGateway.send(new ShowCommands.ReleaseSeat(
                        event.showId(), seat.seatNumber(), event.bookingId()))
        );
    }

    // ── Query handlers ───────────────────────────────────────────────────────

    @QueryHandler
    public Optional<BookingProjection> handle(BookingQueries.GetBooking query) {
        return bookingRepository.findById(query.bookingId());
    }

    @QueryHandler
    public List<BookingProjection> handle(BookingQueries.GetBookingsByCustomer query) {
        return bookingRepository.findByCustomerId(query.customerId());
    }

    @QueryHandler
    public List<BookingProjection> handle(BookingQueries.GetBookingsByShow query) {
        return bookingRepository.findByShowId(query.showId());
    }
}

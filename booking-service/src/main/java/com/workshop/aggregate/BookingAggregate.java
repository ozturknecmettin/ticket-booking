package com.workshop.aggregate;

import com.workshop.commands.BookingCommands;
import com.workshop.dto.SeatItem;
import com.workshop.events.BookingEvents;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.util.List;

@Aggregate(snapshotTriggerDefinition = "bookingSnapshotTrigger")
@NoArgsConstructor
@Slf4j
public class BookingAggregate {

    @AggregateIdentifier
    private String bookingId;
    private String showId;
    private List<SeatItem> seats;
    private String customerId;
    private BigDecimal totalAmount;
    private BookingStatus status;

    private enum BookingStatus { INITIATED, CONFIRMED, CANCELLED, TICKET_ISSUED, REFUNDED }

    @CommandHandler
    public BookingAggregate(BookingCommands.InitiateBooking cmd) {
        AggregateLifecycle.apply(new BookingEvents.BookingInitiated(
                cmd.bookingId(), cmd.showId(), cmd.seats(),
                cmd.customerId(), cmd.totalAmount()
        ));
    }

    @CommandHandler
    public void handle(BookingCommands.ConfirmBooking cmd) {
        if (status != BookingStatus.INITIATED) {
            throw new IllegalStateException("Booking cannot be confirmed in state: " + status);
        }
        AggregateLifecycle.apply(new BookingEvents.BookingConfirmed(bookingId, showId, customerId));
    }

    @CommandHandler
    public void handle(BookingCommands.CancelBooking cmd) {
        if (status == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }
        if (status == BookingStatus.TICKET_ISSUED) {
            throw new IllegalStateException("Cannot cancel after ticket has been issued");
        }
        AggregateLifecycle.apply(new BookingEvents.BookingCancelled(bookingId, showId, customerId, cmd.reason()));
    }

    @CommandHandler
    public void handle(BookingCommands.IssueTicket cmd) {
        if (status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Ticket can only be issued for confirmed bookings");
        }
        AggregateLifecycle.apply(new BookingEvents.TicketIssued(bookingId, cmd.ticketNumber(), customerId));
    }

    @CommandHandler
    public void handle(BookingCommands.RefundBooking cmd) {
        if (status != BookingStatus.TICKET_ISSUED) {
            throw new IllegalStateException("Refund is only allowed for ticket-issued bookings, current status: " + status);
        }
        AggregateLifecycle.apply(new BookingEvents.BookingRefunded(bookingId, showId, seats, cmd.reason()));
    }

    @CommandHandler
    public void handle(BookingCommands.AmendBooking cmd) {
        if (status != BookingStatus.INITIATED) {
            throw new IllegalStateException("Booking can only be amended in INITIATED state, current: " + status);
        }
        if (cmd.newSeats() == null || cmd.newSeats().isEmpty()) {
            throw new IllegalArgumentException("Amendment must specify at least one seat");
        }
        AggregateLifecycle.apply(new BookingEvents.BookingAmended(
                bookingId, showId, seats, cmd.newSeats(), customerId));
    }

    @EventSourcingHandler
    public void on(BookingEvents.BookingInitiated event) {
        this.bookingId = event.bookingId();
        this.showId = event.showId();
        this.seats = event.seats();
        this.customerId = event.customerId();
        this.totalAmount = event.totalAmount();
        this.status = BookingStatus.INITIATED;
        log.info("Booking initiated: {} seats={}", bookingId, seats.size());
    }

    @EventSourcingHandler
    public void on(BookingEvents.BookingConfirmed event) {
        this.status = BookingStatus.CONFIRMED;
        log.info("Booking confirmed: {}", bookingId);
    }

    @EventSourcingHandler
    public void on(BookingEvents.BookingCancelled event) {
        this.status = BookingStatus.CANCELLED;
        log.info("Booking cancelled: {} reason={}", bookingId, event.reason());
    }

    @EventSourcingHandler
    public void on(BookingEvents.TicketIssued event) {
        this.status = BookingStatus.TICKET_ISSUED;
        log.info("Ticket issued: {} ticket={}", bookingId, event.ticketNumber());
    }

    @EventSourcingHandler
    public void on(BookingEvents.BookingRefunded event) {
        this.status = BookingStatus.REFUNDED;
        log.info("Booking refunded: {} reason={}", bookingId, event.reason());
    }

    @EventSourcingHandler
    public void on(BookingEvents.BookingAmended event) {
        this.seats = event.newSeats();
        log.info("Booking amended: {} seats {} → {}", bookingId,
                event.oldSeats().size(), event.newSeats().size());
    }
}

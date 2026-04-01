package com.workshop.aggregate;

import com.workshop.commands.ShowCommands;
import com.workshop.events.ShowEvents;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.factory.annotation.Autowired;

import com.workshop.dto.PriceZone;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Aggregate
@NoArgsConstructor
@Slf4j
public class ShowAggregate {

    @AggregateIdentifier
    private String showId;
    private String title;
    private String venue;
    private int totalSeats;
    private BigDecimal ticketPrice;
    private LocalDateTime eventDate;
    private String category;
    private String artistName;
    private String description;
    private Integer durationMinutes;
    private List<PriceZone> priceZones;
    private Set<String> reservedSeats = new HashSet<>();
    /** seatNumber → bookingId for seats currently under a time-limited hold */
    private Map<String, String> heldSeats = new HashMap<>();
    private boolean cancelled;

    @Autowired(required = false)
    @JsonIgnore
    private DeadlineManager deadlineManager;

    @CommandHandler
    public ShowAggregate(ShowCommands.CreateShow cmd) {
        if (cmd.totalSeats() <= 0) {
            throw new IllegalArgumentException("Total seats must be positive");
        }
        AggregateLifecycle.apply(new ShowEvents.ShowCreated(
                cmd.showId(), cmd.title(), cmd.venue(), cmd.totalSeats(),
                cmd.ticketPrice(), cmd.eventDate(),
                cmd.category(), cmd.artistName(), cmd.description(), cmd.durationMinutes(),
                cmd.priceZones()
        ));
    }

    @CommandHandler
    public void handle(ShowCommands.ReserveSeat cmd) {
        if (cancelled) throw new IllegalStateException("Show is cancelled");
        if (reservedSeats.contains(cmd.seatNumber())) {
            throw new IllegalStateException("Seat " + cmd.seatNumber() + " is already reserved");
        }
        if (reservedSeats.size() >= totalSeats) {
            throw new IllegalStateException("Show is fully booked");
        }
        AggregateLifecycle.apply(new ShowEvents.SeatReserved(showId, cmd.seatNumber(), cmd.bookingId()));
    }

    @CommandHandler
    public void handle(ShowCommands.ReleaseSeat cmd) {
        if (!reservedSeats.contains(cmd.seatNumber())) {
            throw new IllegalStateException("Seat " + cmd.seatNumber() + " is not reserved");
        }
        AggregateLifecycle.apply(new ShowEvents.SeatReleased(showId, cmd.seatNumber(), cmd.bookingId()));
    }

    @CommandHandler
    public void handle(ShowCommands.CancelShow cmd) {
        if (cancelled) throw new IllegalStateException("Show is already cancelled");
        AggregateLifecycle.apply(new ShowEvents.ShowCancelled(showId, cmd.reason()));
    }

    @CommandHandler
    public void handle(ShowCommands.HoldSeat cmd) {
        if (cancelled) throw new IllegalStateException("Show is cancelled");
        if (reservedSeats.contains(cmd.seatNumber())) {
            throw new IllegalStateException("Seat " + cmd.seatNumber() + " is already reserved");
        }
        if (heldSeats.containsKey(cmd.seatNumber())) {
            throw new IllegalStateException("Seat " + cmd.seatNumber() + " is already on hold");
        }
        if (reservedSeats.size() + heldSeats.size() >= totalSeats) {
            throw new IllegalStateException("Show is fully booked");
        }
        AggregateLifecycle.apply(new ShowEvents.SeatHeld(showId, cmd.seatNumber(), cmd.bookingId()));
        if (deadlineManager != null) {
            deadlineManager.schedule(Duration.ofSeconds(cmd.durationSeconds()),
                    "hold-deadline", cmd.seatNumber() + "|" + cmd.bookingId());
        }
    }

    @CommandHandler
    public void handle(ShowCommands.ConfirmHold cmd) {
        if (!cmd.bookingId().equals(heldSeats.get(cmd.seatNumber()))) {
            throw new IllegalStateException(
                    "No active hold for booking " + cmd.bookingId() + " on seat " + cmd.seatNumber());
        }
        AggregateLifecycle.apply(new ShowEvents.HoldConfirmed(showId, cmd.seatNumber(), cmd.bookingId()));
        if (deadlineManager != null) {
            deadlineManager.cancelAllWithinScope("hold-deadline");
        }
    }

    @CommandHandler
    public void handle(ShowCommands.ExpireHold cmd) {
        if (cmd.bookingId().equals(heldSeats.get(cmd.seatNumber()))) {
            AggregateLifecycle.apply(new ShowEvents.HoldExpired(showId, cmd.seatNumber(), cmd.bookingId()));
        }
    }

    @DeadlineHandler(deadlineName = "hold-deadline")
    public void onHoldDeadline(String payload) {
        String[] parts = payload.split("\\|", 2);
        String seatNumber = parts[0];
        String bookingId  = parts[1];
        if (bookingId.equals(heldSeats.get(seatNumber))) {
            log.warn("Hold expired for seat {} booking {} in show {}", seatNumber, bookingId, showId);
            AggregateLifecycle.apply(new ShowEvents.HoldExpired(showId, seatNumber, bookingId));
        }
    }

    @EventSourcingHandler
    public void on(ShowEvents.ShowCreated event) {
        this.showId = event.showId();
        this.title = event.title();
        this.venue = event.venue();
        this.totalSeats = event.totalSeats();
        this.ticketPrice = event.ticketPrice();
        this.eventDate = event.eventDate();
        this.category = event.category();
        this.artistName = event.artistName();
        this.description = event.description();
        this.durationMinutes = event.durationMinutes();
        this.priceZones = event.priceZones();
        this.reservedSeats = new HashSet<>();
        this.heldSeats = new HashMap<>();
        this.cancelled = false;
        log.info("Show created: {}", showId);
    }

    @EventSourcingHandler
    public void on(ShowEvents.SeatReserved event) {
        reservedSeats.add(event.seatNumber());
        log.info("Seat {} reserved in show {}", event.seatNumber(), showId);
    }

    @EventSourcingHandler
    public void on(ShowEvents.SeatReleased event) {
        reservedSeats.remove(event.seatNumber());
        log.info("Seat {} released in show {}", event.seatNumber(), showId);
    }

    @EventSourcingHandler
    public void on(ShowEvents.ShowCancelled event) {
        this.cancelled = true;
        log.info("Show {} cancelled: {}", showId, event.reason());
    }

    @EventSourcingHandler
    public void on(ShowEvents.SeatHeld event) {
        heldSeats.put(event.seatNumber(), event.bookingId());
        log.info("Seat {} held for booking {} in show {}", event.seatNumber(), event.bookingId(), showId);
    }

    @EventSourcingHandler
    public void on(ShowEvents.HoldConfirmed event) {
        heldSeats.remove(event.seatNumber());
        reservedSeats.add(event.seatNumber());
        log.info("Hold confirmed → reserved: seat {} booking {} in show {}",
                event.seatNumber(), event.bookingId(), showId);
    }

    @EventSourcingHandler
    public void on(ShowEvents.HoldExpired event) {
        heldSeats.remove(event.seatNumber());
        log.info("Hold expired: seat {} booking {} in show {}",
                event.seatNumber(), event.bookingId(), showId);
    }
}

package com.workshop.saga;

import com.workshop.commands.BookingCommands;
import com.workshop.commands.PaymentCommands;
import com.workshop.commands.ShowCommands;
import com.workshop.dto.SeatItem;
import com.workshop.events.BookingEvents;
import com.workshop.events.PaymentEvents;
import com.workshop.events.ShowEvents;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


/**
 * BookingPaymentSaga orchestrates the full booking lifecycle:
 *
 * BookingInitiated → [reserve seats sequentially in show-service]
 *   → all SeatReserved → [send RequestPayment to payment-service]
 *     → PaymentConfirmed → [send ConfirmBooking + IssueTicket]
 *     → PaymentFailed    → [send CancelBooking → releases all reserved seats]
 */
@Saga
@NoArgsConstructor
@Slf4j
public class BookingPaymentSaga {

    private static final String PAYMENT_DEADLINE = "payment-deadline";

    @Autowired
    private transient CommandGateway commandGateway;

    /**
     * Injected lazily to avoid a circular dependency: the Axon DeadlineManager bean
     * is not fully initialised when the saga prototype is first created by
     * SpringResourceInjector.  The @Lazy proxy is resolved on first method call,
     * by which time all beans are ready.
     */
    @Autowired
    @Lazy
    private transient DeadlineManager deadlineManager;

    @Value("${booking.payment-timeout-minutes:10}")
    private transient int paymentTimeoutMinutes = 10;

    // Saga state
    private String bookingId;
    private String showId;
    private String customerId;
    private String paymentId;
    private String deadlineId;
    private BigDecimal totalAmount;

    private List<SeatItem> seats;         // all seats in the order
    private List<String> reservedSeats;   // seats confirmed reserved (for rollback)
    private int seatReservationIndex;     // index of next seat to reserve

    // ── Step 1: Booking initiated → start reserving seats sequentially ───────

    @StartSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingEvents.BookingInitiated event) {
        log.info("Saga started for booking {} ({} seat(s))", event.bookingId(), event.seats().size());
        this.bookingId = event.bookingId();
        this.showId = event.showId();
        this.customerId = event.customerId();
        this.totalAmount = event.totalAmount();
        this.seats = new ArrayList<>(event.seats());
        this.reservedSeats = new ArrayList<>();
        this.seatReservationIndex = 0;

        reserveNextSeat();
    }

    private void reserveNextSeat() {
        SeatItem seat = seats.get(seatReservationIndex);
        log.info("Reserving seat {} ({}/{}) for booking {}", seat.seatNumber(),
                seatReservationIndex + 1, seats.size(), bookingId);
        commandGateway.<Void>send(new ShowCommands.ReserveSeat(showId, seat.seatNumber(), bookingId))
                .exceptionally(ex -> {
                    log.error("ReserveSeat failed for booking {} seat {}: {}",
                            bookingId, seat.seatNumber(), ex.getMessage());
                    commandGateway.send(new BookingCommands.CancelBooking(
                            bookingId, "Seat reservation failed: " + ex.getMessage()));
                    return null;
                });
    }

    // ── Step 2: Each SeatReserved → reserve next or request payment ──────────

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(ShowEvents.SeatReserved event) {
        reservedSeats.add(event.seatNumber());
        seatReservationIndex++;
        log.info("Seat {} reserved for booking {} ({}/{})",
                event.seatNumber(), bookingId, seatReservationIndex, seats.size());

        if (seatReservationIndex < seats.size()) {
            // More seats to reserve — continue sequentially
            reserveNextSeat();
        } else {
            // All seats reserved — request payment for total
            this.paymentId = bookingId + "-pay";
            SagaLifecycle.associateWith("paymentId", paymentId);

            commandGateway.<Void>send(new PaymentCommands.RequestPayment(paymentId, bookingId, customerId, totalAmount))
                    .exceptionally(ex -> {
                        log.error("RequestPayment failed for booking {}: {}", bookingId, ex.getMessage());
                        commandGateway.send(new BookingCommands.CancelBooking(
                                bookingId, "Payment request failed: " + ex.getMessage()));
                        return null;
                    });

            this.deadlineId = deadlineManager.schedule(
                    Duration.ofMinutes(paymentTimeoutMinutes), PAYMENT_DEADLINE);
            log.info("All seats reserved; payment deadline scheduled in {} min for booking {}",
                    paymentTimeoutMinutes, bookingId);
        }
    }

    // ── Step 3a: Payment confirmed → confirm booking ─────────────────────────

    @SagaEventHandler(associationProperty = "paymentId")
    public void on(PaymentEvents.PaymentConfirmed event) {
        log.info("Payment {} confirmed; confirming booking {}", paymentId, bookingId);
        cancelDeadline();
        commandGateway.send(new BookingCommands.ConfirmBooking(bookingId));
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingEvents.BookingConfirmed event) {
        log.info("Booking {} confirmed; issuing ticket", bookingId);
        String ticketNumber = "TKT-" + bookingId.substring(0, 8).toUpperCase();
        commandGateway.send(new BookingCommands.IssueTicket(bookingId, ticketNumber));
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingEvents.TicketIssued event) {
        log.info("Ticket {} issued for booking {}; saga complete", event.ticketNumber(), bookingId);
        SagaLifecycle.end();
    }

    // ── Step 3b: Payment failed → cancel booking ─────────────────────────────

    @SagaEventHandler(associationProperty = "paymentId")
    public void on(PaymentEvents.PaymentFailed event) {
        log.warn("Payment {} failed; cancelling booking {}", paymentId, bookingId);
        cancelDeadline();
        commandGateway.send(new BookingCommands.CancelBooking(bookingId, "Payment failed: " + event.reason()));
    }

    // ── Deadline: payment not confirmed in time ───────────────────────────────

    @DeadlineHandler(deadlineName = PAYMENT_DEADLINE)
    public void onPaymentDeadline() {
        log.warn("Payment deadline expired for booking {}; cancelling", bookingId);
        commandGateway.send(new BookingCommands.CancelBooking(bookingId, "Payment timeout"));
    }

    private void cancelDeadline() {
        if (deadlineId != null) {
            deadlineManager.cancelSchedule(PAYMENT_DEADLINE, deadlineId);
            deadlineId = null;
        }
    }

    // ── Booking amended → swap seats ─────────────────────────────────────────

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingEvents.BookingAmended event) {
        log.info("Booking {} amended: releasing {} old seat(s), re-reserving {} new seat(s)",
                bookingId, event.oldSeats().size(), event.newSeats().size());
        // Release all seats that were already reserved
        for (String seat : reservedSeats) {
            commandGateway.<Void>send(new ShowCommands.ReleaseSeat(showId, seat, bookingId))
                    .exceptionally(ex -> {
                        log.error("ReleaseSeat failed during amendment for booking {} seat {}: {}",
                                bookingId, seat, ex.getMessage());
                        return null;
                    });
        }
        // Reset and start re-reserving from the new seat list
        this.seats = new ArrayList<>(event.newSeats());
        this.reservedSeats = new ArrayList<>();
        this.seatReservationIndex = 0;
        reserveNextSeat();
    }

    // ── Booking cancelled → release all reserved seats ───────────────────────

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingEvents.BookingCancelled event) {
        cancelDeadline();
        // If payment was already requested, fail it so it doesn't stay in REQUESTED state
        if (paymentId != null) {
            log.info("Booking {} cancelled; failing pending payment {}", bookingId, paymentId);
            commandGateway.<Void>send(new PaymentCommands.FailPayment(paymentId, event.reason()))
                    .exceptionally(ex -> {
                        log.warn("FailPayment for cancelled booking {} failed (may already be resolved): {}",
                                bookingId, ex.getMessage());
                        return null;
                    });
        }
        log.info("Booking {} cancelled; releasing {} reserved seat(s)", bookingId, reservedSeats.size());
        for (String seat : reservedSeats) {
            commandGateway.<Void>send(new ShowCommands.ReleaseSeat(showId, seat, bookingId))
                    .exceptionally(ex -> {
                        log.error("ReleaseSeat failed for cancelled booking {} seat {}: {}",
                                bookingId, seat, ex.getMessage());
                        return null;
                    });
        }
        SagaLifecycle.end();
    }
}

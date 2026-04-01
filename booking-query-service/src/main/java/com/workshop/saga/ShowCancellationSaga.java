package com.workshop.saga;

import com.workshop.commands.BookingCommands;
import com.workshop.events.BookingEvents;
import com.workshop.events.ShowEvents;
import com.workshop.projection.BookingProjection;
import com.workshop.projection.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Orchestrates the cascade cancellation of all active bookings when a show is cancelled.
 * Tracks {@code pendingCancellations} so the saga ends only after every booking confirms cancellation.
 */
@Saga
@Slf4j
public class ShowCancellationSaga {

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient BookingRepository bookingRepository;

    private int pendingCancellations;
    private String showId;

    @StartSaga
    @SagaEventHandler(associationProperty = "showId")
    public void on(ShowEvents.ShowCancelled event) {
        this.showId = event.showId();

        List<String> terminalStatuses = List.of("CANCELLED", "TICKET_ISSUED", "REFUNDED");
        List<BookingProjection> active =
                bookingRepository.findByShowIdAndStatusNotIn(event.showId(), terminalStatuses);

        if (active.isEmpty()) {
            log.info("Show {} cancelled — no active bookings, ending saga immediately", event.showId());
            SagaLifecycle.end();
            return;
        }

        this.pendingCancellations = active.size();
        log.info("Show {} cancelled — dispatching CancelBooking for {} active booking(s)",
                event.showId(), active.size());

        active.forEach(booking -> {
            SagaLifecycle.associateWith("bookingId", booking.getBookingId());
            commandGateway.send(new BookingCommands.CancelBooking(
                    booking.getBookingId(), "Show cancelled: " + event.reason()));
        });
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingEvents.BookingCancelled event) {
        pendingCancellations--;
        log.info("Booking {} cancelled — {} pending cancellation(s) remaining for show {}",
                event.bookingId(), pendingCancellations, showId);
        if (pendingCancellations <= 0) {
            log.info("All bookings cancelled for show {} — ending ShowCancellationSaga", showId);
            SagaLifecycle.end();
        }
    }
}

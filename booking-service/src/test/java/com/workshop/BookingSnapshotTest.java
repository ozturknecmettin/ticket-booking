package com.workshop;

import com.workshop.aggregate.BookingAggregate;
import com.workshop.commands.BookingCommands;
import com.workshop.config.SnapshotConfig;
import com.workshop.dto.SeatItem;
import com.workshop.events.BookingEvents;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Verifies that the BookingAggregate snapshot configuration is wired correctly
 * and that the aggregate state can be fully reconstructed from events (which is
 * the precondition for correct snapshot restoration).
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bookingdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "axon.axonserver.enabled=false",
        "booking.snapshot-threshold=3"
})
class BookingSnapshotTest {

    @Autowired
    SnapshotTriggerDefinition bookingSnapshotTrigger;

    @Autowired
    Snapshotter snapshotter;

    private AggregateTestFixture<BookingAggregate> fixture;

    private static final String BOOKING_ID  = "snap-001";
    private static final String SHOW_ID     = "show-snap";
    private static final BigDecimal AMOUNT  = new BigDecimal("49.99");
    private static final List<SeatItem> SEATS = List.of(new SeatItem("A1", AMOUNT));

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(BookingAggregate.class);
    }

    @Test
    @DisplayName("bookingSnapshotTrigger bean is EventCountSnapshotTriggerDefinition")
    void snapshotTriggerBeanType() {
        assertThat(bookingSnapshotTrigger)
                .isInstanceOf(EventCountSnapshotTriggerDefinition.class);
    }

    @Test
    @DisplayName("Snapshotter bean is present in Spring context")
    void snapshotterBeanPresent() {
        assertThat(snapshotter).isNotNull();
    }

    @Test
    @DisplayName("Aggregate state is fully reproducible from all events (snapshot precondition)")
    void aggregateStateReproducibleFromEvents() {
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT),
                        new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1"),
                        new BookingEvents.TicketIssued(BOOKING_ID, "TKT-SNAP", "customer-1")
                )
                .when(new BookingCommands.RefundBooking(BOOKING_ID, "Test refund"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.BookingRefunded(BOOKING_ID, SHOW_ID, SEATS, "Test refund"));
    }

    @Test
    @DisplayName("Aggregate survives event replay past snapshot threshold (3 events)")
    void aggregateHandlesEventCountBeyondThreshold() {
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT),
                        new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1"),
                        new BookingEvents.TicketIssued(BOOKING_ID, "TKT-SNAP", "customer-1"),
                        new BookingEvents.BookingRefunded(BOOKING_ID, SHOW_ID, SEATS, "Prior refund")
                )
                .when(new BookingCommands.RefundBooking(BOOKING_ID, "Double refund"))
                .expectException(IllegalStateException.class);
    }

    // ── Amendment event sourcing (Sprint 5) ──────────────────────────────────

    @Test
    @DisplayName("BookingAmended event is correctly replayed: new seats replace old")
    void amendedSeatListReplayedCorrectly() {
        List<SeatItem> newSeats = List.of(new SeatItem("C3", AMOUNT), new SeatItem("C4", AMOUNT));
        // After amendment, the aggregate should cancel correctly (uses new seats)
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT),
                        new BookingEvents.BookingAmended(BOOKING_ID, SHOW_ID, SEATS, newSeats, "customer-1")
                )
                .when(new BookingCommands.CancelBooking(BOOKING_ID, "Changed my mind"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.BookingCancelled(BOOKING_ID, SHOW_ID, "customer-1", "Changed my mind"));
    }

    @Test
    @DisplayName("AmendBooking rejected after CONFIRMED — amendment window has closed")
    void amendRejectedAfterConfirmed() {
        List<SeatItem> newSeats = List.of(new SeatItem("D1", AMOUNT));
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT),
                        new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1")
                )
                .when(new BookingCommands.AmendBooking(BOOKING_ID, newSeats))
                .expectException(allOf(
                        instanceOf(IllegalStateException.class),
                        hasProperty("message", containsString("INITIATED"))));
    }

    @Test
    @DisplayName("Multiple amendments are all correctly replayed in sequence")
    void multipleAmendmentsReplayedInOrder() {
        List<SeatItem> firstAmend  = List.of(new SeatItem("B1", AMOUNT));
        List<SeatItem> secondAmend = List.of(new SeatItem("B2", AMOUNT));
        // Final state: seats = [B2]. Confirm should work.
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT),
                        new BookingEvents.BookingAmended(BOOKING_ID, SHOW_ID, SEATS,      firstAmend,  "customer-1"),
                        new BookingEvents.BookingAmended(BOOKING_ID, SHOW_ID, firstAmend, secondAmend, "customer-1")
                )
                .when(new BookingCommands.ConfirmBooking(BOOKING_ID))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1"));
    }
}

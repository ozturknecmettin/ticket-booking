package com.workshop;

import com.workshop.aggregate.BookingAggregate;
import com.workshop.commands.BookingCommands;
import com.workshop.dto.SeatItem;
import com.workshop.events.BookingEvents;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class BookingAggregateTest {

    private AggregateTestFixture<BookingAggregate> fixture;
    private static final String BOOKING_ID = "booking-001";
    private static final String SHOW_ID = "show-001";
    private static final BigDecimal UNIT_PRICE = new BigDecimal("49.99");
    private static final List<SeatItem> SEATS = List.of(new SeatItem("A1", UNIT_PRICE));

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(BookingAggregate.class);
    }

    @Test
    @DisplayName("InitiateBooking should emit BookingInitiated")
    void initiateBooking() {
        fixture.givenNoPriorActivity()
                .when(new BookingCommands.InitiateBooking(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE));
    }

    @Test
    @DisplayName("ConfirmBooking should emit BookingConfirmed when INITIATED")
    void confirmBooking() {
        fixture.given(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE))
                .when(new BookingCommands.ConfirmBooking(BOOKING_ID))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1"));
    }

    @Test
    @DisplayName("CancelBooking should emit BookingCancelled when INITIATED")
    void cancelBooking() {
        fixture.given(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE))
                .when(new BookingCommands.CancelBooking(BOOKING_ID, "Payment failed"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.BookingCancelled(BOOKING_ID, SHOW_ID, "customer-1", "Payment failed"));
    }

    @Test
    @DisplayName("IssueTicket should emit TicketIssued when CONFIRMED")
    void issueTicket() {
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE),
                        new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1")
                )
                .when(new BookingCommands.IssueTicket(BOOKING_ID, "TKT-ABC123"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.TicketIssued(BOOKING_ID, "TKT-ABC123", "customer-1"));
    }

    @Test
    @DisplayName("ConfirmBooking on already cancelled booking should fail")
    void confirmCancelledBooking() {
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE),
                        new BookingEvents.BookingCancelled(BOOKING_ID, SHOW_ID, "customer-1", "Payment failed")
                )
                .when(new BookingCommands.ConfirmBooking(BOOKING_ID))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("IssueTicket on un-confirmed booking should fail")
    void issueTicketOnUnconfirmed() {
        fixture.given(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE))
                .when(new BookingCommands.IssueTicket(BOOKING_ID, "TKT-ABC123"))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("RefundBooking on TICKET_ISSUED booking should emit BookingRefunded")
    void refundBooking_whenTicketIssued() {
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE),
                        new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1"),
                        new BookingEvents.TicketIssued(BOOKING_ID, "TKT-ABC123", "customer-1")
                )
                .when(new BookingCommands.RefundBooking(BOOKING_ID, "Customer request"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.BookingRefunded(BOOKING_ID, SHOW_ID, SEATS, "Customer request"));
    }

    @Test
    @DisplayName("RefundBooking on non-TICKET_ISSUED booking should fail")
    void refundBooking_onNonTicketIssuedState_fails() {
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE),
                        new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1")
                )
                .when(new BookingCommands.RefundBooking(BOOKING_ID, "Customer request"))
                .expectException(IllegalStateException.class);
    }

    // ── Amendment ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AmendBooking in INITIATED state emits BookingAmended")
    void amendBooking_emitsBookingAmended() {
        List<SeatItem> newSeats = List.of(new SeatItem("B1", UNIT_PRICE), new SeatItem("B2", UNIT_PRICE));
        fixture.given(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE))
                .when(new BookingCommands.AmendBooking(BOOKING_ID, newSeats))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingEvents.BookingAmended(BOOKING_ID, SHOW_ID, SEATS, newSeats, "customer-1"));
    }

    @Test
    @DisplayName("AmendBooking after CONFIRMED should fail")
    void amendBooking_afterConfirmed_fails() {
        List<SeatItem> newSeats = List.of(new SeatItem("C1", UNIT_PRICE));
        fixture.given(
                        new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE),
                        new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1")
                )
                .when(new BookingCommands.AmendBooking(BOOKING_ID, newSeats))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("AmendBooking with empty seat list should fail")
    void amendBooking_emptySeats_fails() {
        fixture.given(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", UNIT_PRICE))
                .when(new BookingCommands.AmendBooking(BOOKING_ID, List.of()))
                .expectException(IllegalArgumentException.class);
    }
}

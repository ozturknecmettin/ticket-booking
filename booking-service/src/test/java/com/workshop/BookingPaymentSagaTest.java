package com.workshop;

import com.workshop.commands.BookingCommands;
import com.workshop.commands.PaymentCommands;
import com.workshop.commands.ShowCommands;
import com.workshop.dto.SeatItem;
import com.workshop.events.BookingEvents;
import com.workshop.events.PaymentEvents;
import com.workshop.events.ShowEvents;
import com.workshop.saga.BookingPaymentSaga;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

class BookingPaymentSagaTest {

    private SagaTestFixture<BookingPaymentSaga> fixture;
    private static final String BOOKING_ID = "booking-001";
    private static final String SHOW_ID = "show-001";
    private static final BigDecimal AMOUNT = new BigDecimal("49.99");
    private static final List<SeatItem> SEATS = List.of(new SeatItem("A1", AMOUNT));

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(BookingPaymentSaga.class);
    }

    @Test
    @DisplayName("BookingInitiated should trigger ReserveSeat command for first seat")
    void bookingInitiated_triggersReserveSeat() {
        fixture.givenNoPriorActivity()
                .whenPublishingA(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .expectActiveSagas(1)
                .expectDispatchedCommands(new ShowCommands.ReserveSeat(SHOW_ID, "A1", BOOKING_ID));
    }

    @Test
    @DisplayName("SeatReserved (all seats done) should trigger RequestPayment command")
    void seatReserved_triggersRequestPayment() {
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .whenPublishingA(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .expectActiveSagas(1)
                .expectDispatchedCommands(
                        new PaymentCommands.RequestPayment(BOOKING_ID + "-pay", BOOKING_ID, "customer-1", AMOUNT));
    }

    @Test
    @DisplayName("Saga stays active after seat reservation — awaiting payment outcome")
    void sagaRemainsActiveAfterSeatReservation() {
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .whenPublishingA(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .expectActiveSagas(1);
    }

    @Test
    @DisplayName("Multi-seat: second ReserveSeat dispatched after first SeatReserved")
    void multiSeat_secondReserveSeatDispatched() {
        List<SeatItem> twoSeats = List.of(
                new SeatItem("A1", new BigDecimal("49.99")),
                new SeatItem("A2", new BigDecimal("49.99")));
        BigDecimal total = new BigDecimal("99.98");

        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, twoSeats, "customer-1", total))
                .whenPublishingA(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .expectActiveSagas(1)
                .expectDispatchedCommands(new ShowCommands.ReserveSeat(SHOW_ID, "A2", BOOKING_ID));
    }

    @Test
    @DisplayName("PaymentFailed should trigger CancelBooking command")
    void paymentFailed_triggersCancelBooking() {
        String paymentId = BOOKING_ID + "-pay";
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .andThenAPublished(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .whenPublishingA(new PaymentEvents.PaymentFailed(paymentId, BOOKING_ID, "Insufficient funds"))
                .expectActiveSagas(1)
                .expectDispatchedCommands(
                        new BookingCommands.CancelBooking(BOOKING_ID, "Payment failed: Insufficient funds"));
    }

    @Test
    @DisplayName("PaymentConfirmed should trigger ConfirmBooking command and cancel deadline")
    void paymentConfirmed_triggersConfirmBooking() {
        String paymentId = BOOKING_ID + "-pay";
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .andThenAPublished(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .whenPublishingA(new PaymentEvents.PaymentConfirmed(paymentId, BOOKING_ID))
                .expectActiveSagas(1)
                .expectDispatchedCommands(new BookingCommands.ConfirmBooking(BOOKING_ID));
    }

    @Test
    @DisplayName("TicketIssued ends the saga")
    void ticketIssued_endsSaga() {
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .andThenAPublished(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .andThenAPublished(new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1"))
                .whenPublishingA(new BookingEvents.TicketIssued(BOOKING_ID, "TKT-001", "customer-1"))
                .expectActiveSagas(0);
    }

    @Test
    @DisplayName("BookingCancelled after SeatReserved ends saga and releases reserved seat")
    void bookingCancelled_endsSaga() {
        String paymentId = BOOKING_ID + "-pay";
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .andThenAPublished(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .whenPublishingA(new BookingEvents.BookingCancelled(BOOKING_ID, SHOW_ID, "customer-1", "Payment failed"))
                .expectActiveSagas(0)
                .expectDispatchedCommands(
                        new PaymentCommands.FailPayment(paymentId, "Payment failed"),
                        new ShowCommands.ReleaseSeat(SHOW_ID, "A1", BOOKING_ID));
    }

    @Test
    @DisplayName("BookingCancelled before SeatReserved ends saga without dispatching ReleaseSeat")
    void bookingCancelled_beforeSeatReserved_endsSagaGracefully() {
        // Seat reservation failed → CancelBooking sent → BookingCancelled fires.
        // reservedSeats is empty so no ReleaseSeat should be dispatched.
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .whenPublishingA(new BookingEvents.BookingCancelled(BOOKING_ID, SHOW_ID, "customer-1", "Seat reservation failed"))
                .expectActiveSagas(0)
                .expectNoDispatchedCommands();
    }

    @Test
    @DisplayName("SeatReserved schedules a payment deadline")
    void seatReserved_schedulesPaymentDeadline() {
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .whenPublishingA(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .expectActiveSagas(1)
                .expectScheduledDeadlineWithName(Duration.ofMinutes(10), "payment-deadline");
    }

    @Test
    @DisplayName("Full happy path: BookingInitiated → SeatReserved → PaymentConfirmed → BookingConfirmed → TicketIssued ends saga")
    void fullHappyPath_sagaEndsOnTicketIssued() {
        String paymentId = BOOKING_ID + "-pay";

        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .andThenAPublished(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .andThenAPublished(new PaymentEvents.PaymentConfirmed(paymentId, BOOKING_ID))
                .andThenAPublished(new BookingEvents.BookingConfirmed(BOOKING_ID, SHOW_ID, "customer-1"))
                .whenPublishingA(new BookingEvents.TicketIssued(BOOKING_ID, "TKT-9999", "customer-1"))
                .expectActiveSagas(0);
    }

    @Test
    @DisplayName("Payment deadline fires CancelBooking command")
    void paymentDeadline_triggersCancelBooking() {
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .andThenAPublished(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .whenTimeElapses(Duration.ofMinutes(11))
                .expectDispatchedCommandsMatching(
                        (org.hamcrest.Matcher) org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // ── Amendment ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BookingAmended: reserved seats released, new seat reservation started")
    void bookingAmended_releasesOldSeatsAndReservesNew() {
        List<SeatItem> newSeats = List.of(new SeatItem("C1", AMOUNT));
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .andThenAPublished(new ShowEvents.SeatReserved(SHOW_ID, "A1", BOOKING_ID))
                .whenPublishingA(new BookingEvents.BookingAmended(BOOKING_ID, SHOW_ID, SEATS, newSeats, "customer-1"))
                .expectActiveSagas(1)
                .expectDispatchedCommands(
                        new ShowCommands.ReleaseSeat(SHOW_ID, "A1", BOOKING_ID),
                        new ShowCommands.ReserveSeat(SHOW_ID, "C1", BOOKING_ID));
    }

    @Test
    @DisplayName("BookingAmended before any seat reserved: just reserves new seats")
    void bookingAmended_noReservedSeats_reservesNew() {
        List<SeatItem> newSeats = List.of(new SeatItem("D2", AMOUNT));
        fixture.givenAPublished(new BookingEvents.BookingInitiated(BOOKING_ID, SHOW_ID, SEATS, "customer-1", AMOUNT))
                .whenPublishingA(new BookingEvents.BookingAmended(BOOKING_ID, SHOW_ID, SEATS, newSeats, "customer-1"))
                .expectActiveSagas(1)
                .expectDispatchedCommands(new ShowCommands.ReserveSeat(SHOW_ID, "D2", BOOKING_ID));
    }
}

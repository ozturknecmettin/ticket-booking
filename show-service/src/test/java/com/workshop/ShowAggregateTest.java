package com.workshop;

import com.workshop.aggregate.ShowAggregate;
import com.workshop.commands.ShowCommands;
import com.workshop.events.ShowEvents;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;

class ShowAggregateTest {

    private AggregateTestFixture<ShowAggregate> fixture;
    private static final String SHOW_ID = "show-001";
    private static final BigDecimal PRICE = new BigDecimal("49.99");
    private static final LocalDateTime EVENT_DATE = LocalDateTime.of(2026, 6, 1, 19, 30);

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(ShowAggregate.class);
    }

    @Test
    @DisplayName("CreateShow command should emit ShowCreated event")
    void createShow() {
        fixture.givenNoPriorActivity()
                .when(new ShowCommands.CreateShow(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null));
    }

    @Test
    @DisplayName("ReserveSeat should emit SeatReserved when seat is available")
    void reserveSeat() {
        fixture.given(new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null))
                .when(new ShowCommands.ReserveSeat(SHOW_ID, "A1", "booking-001"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShowEvents.SeatReserved(SHOW_ID, "A1", "booking-001"));
    }

    @Test
    @DisplayName("ReserveSeat should fail when seat is already reserved")
    void reserveDuplicateSeat() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.SeatReserved(SHOW_ID, "A1", "booking-001")
                )
                .when(new ShowCommands.ReserveSeat(SHOW_ID, "A1", "booking-002"))
                .expectException(allOf(instanceOf(IllegalStateException.class),
                        hasProperty("message", containsString("already reserved"))));
    }

    @Test
    @DisplayName("ReleaseSeat should emit SeatReleased")
    void releaseSeat() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.SeatReserved(SHOW_ID, "A1", "booking-001")
                )
                .when(new ShowCommands.ReleaseSeat(SHOW_ID, "A1", "booking-001"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShowEvents.SeatReleased(SHOW_ID, "A1", "booking-001"));
    }

    @Test
    @DisplayName("CancelShow should emit ShowCancelled")
    void cancelShow() {
        fixture.given(new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null))
                .when(new ShowCommands.CancelShow(SHOW_ID, "Venue unavailable"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShowEvents.ShowCancelled(SHOW_ID, "Venue unavailable"));
    }

    @Test
    @DisplayName("CreateShow should fail with zero seats")
    void createShowWithZeroSeats() {
        fixture.givenNoPriorActivity()
                .when(new ShowCommands.CreateShow(SHOW_ID, "Hamilton", "Broadway", 0, PRICE, EVENT_DATE, null, null, null, null, null))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ReserveSeat on cancelled show should fail")
    void reserveSeatOnCancelledShow() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.ShowCancelled(SHOW_ID, "Venue unavailable")
                )
                .when(new ShowCommands.ReserveSeat(SHOW_ID, "A1", "booking-001"))
                .expectException(allOf(instanceOf(IllegalStateException.class),
                        hasProperty("message", containsString("cancelled"))));
    }

    @Test
    @DisplayName("ReserveSeat on fully booked show should fail")
    void reserveSeatOnSoldOutShow() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 1, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.SeatReserved(SHOW_ID, "A1", "booking-001")
                )
                .when(new ShowCommands.ReserveSeat(SHOW_ID, "A2", "booking-002"))
                .expectException(allOf(instanceOf(IllegalStateException.class),
                        hasProperty("message", containsString("fully booked"))));
    }

    @Test
    @DisplayName("ReleaseSeat on non-reserved seat should fail")
    void releaseSeatNotReserved() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null)
                )
                .when(new ShowCommands.ReleaseSeat(SHOW_ID, "Z9", "booking-999"))
                .expectException(allOf(instanceOf(IllegalStateException.class),
                        hasProperty("message", containsString("not reserved"))));
    }

    // ── Hold mechanic ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HoldSeat should emit SeatHeld (deadline scheduling verified at integration level)")
    void holdSeat() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null)
                )
                .when(new ShowCommands.HoldSeat(SHOW_ID, "B2", "booking-010", 30))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShowEvents.SeatHeld(SHOW_ID, "B2", "booking-010"));
    }

    @Test
    @DisplayName("ConfirmHold should emit HoldConfirmed and seat becomes reserved")
    void confirmHold() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.SeatHeld(SHOW_ID, "B2", "booking-010")
                )
                .when(new ShowCommands.ConfirmHold(SHOW_ID, "B2", "booking-010"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShowEvents.HoldConfirmed(SHOW_ID, "B2", "booking-010"));
    }

    @Test
    @DisplayName("ExpireHold should emit HoldExpired and free the seat")
    void expireHold() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.SeatHeld(SHOW_ID, "B2", "booking-010")
                )
                .when(new ShowCommands.ExpireHold(SHOW_ID, "B2", "booking-010"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShowEvents.HoldExpired(SHOW_ID, "B2", "booking-010"));
    }

    @Test
    @DisplayName("HoldSeat on already held seat should fail")
    void holdSeatAlreadyHeld() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.SeatHeld(SHOW_ID, "B2", "booking-010")
                )
                .when(new ShowCommands.HoldSeat(SHOW_ID, "B2", "booking-011", 30))
                .expectException(allOf(instanceOf(IllegalStateException.class),
                        hasProperty("message", containsString("already on hold"))));
    }

    @Test
    @DisplayName("HoldSeat on reserved seat should fail")
    void holdSeatAlreadyReserved() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.SeatReserved(SHOW_ID, "A1", "booking-001")
                )
                .when(new ShowCommands.HoldSeat(SHOW_ID, "A1", "booking-010", 30))
                .expectException(allOf(instanceOf(IllegalStateException.class),
                        hasProperty("message", containsString("already reserved"))));
    }

    @Test
    @DisplayName("After hold expires, seat becomes available to reserve again")
    void reserveAfterHoldExpired() {
        fixture.given(
                        new ShowEvents.ShowCreated(SHOW_ID, "Hamilton", "Broadway", 100, PRICE, EVENT_DATE, null, null, null, null, null),
                        new ShowEvents.SeatHeld(SHOW_ID, "B2", "booking-010"),
                        new ShowEvents.HoldExpired(SHOW_ID, "B2", "booking-010")
                )
                .when(new ShowCommands.ReserveSeat(SHOW_ID, "B2", "booking-011"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new ShowEvents.SeatReserved(SHOW_ID, "B2", "booking-011"));
    }
}

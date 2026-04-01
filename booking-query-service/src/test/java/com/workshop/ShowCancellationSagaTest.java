package com.workshop;

import com.workshop.commands.BookingCommands;
import com.workshop.events.BookingEvents;
import com.workshop.events.ShowEvents;
import com.workshop.projection.BookingProjection;
import com.workshop.projection.BookingRepository;
import com.workshop.saga.ShowCancellationSaga;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShowCancellationSagaTest {

    private SagaTestFixture<ShowCancellationSaga> fixture;
    private BookingRepository bookingRepository;

    private static final String SHOW_ID = "show-001";
    private static final String REASON = "Venue unavailable";

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(ShowCancellationSaga.class);
        bookingRepository = mock(BookingRepository.class);
        fixture.registerResource(bookingRepository);
    }

    @Test
    @DisplayName("ShowCancelled with active bookings dispatches CancelBooking for each")
    void showCancelled_withActiveBookings_dispatchesCancelBooking() {
        BookingProjection bp = new BookingProjection();
        bp.setBookingId("bk-001");

        when(bookingRepository.findByShowIdAndStatusNotIn(eq(SHOW_ID), any()))
                .thenReturn(List.of(bp));

        fixture.givenNoPriorActivity()
                .whenPublishingA(new ShowEvents.ShowCancelled(SHOW_ID, REASON))
                .expectActiveSagas(1)
                .expectDispatchedCommands(
                        new BookingCommands.CancelBooking("bk-001", "Show cancelled: " + REASON));
    }

    @Test
    @DisplayName("ShowCancelled with no active bookings ends the saga immediately")
    void showCancelled_noActiveBookings_endsSagaImmediately() {
        when(bookingRepository.findByShowIdAndStatusNotIn(eq(SHOW_ID), any()))
                .thenReturn(List.of());

        fixture.givenNoPriorActivity()
                .whenPublishingA(new ShowEvents.ShowCancelled(SHOW_ID, REASON))
                .expectActiveSagas(0)
                .expectNoDispatchedCommands();
    }

    @Test
    @DisplayName("Last BookingCancelled ends the saga")
    void bookingCancelled_lastPending_endsSaga() {
        BookingProjection bp = new BookingProjection();
        bp.setBookingId("bk-001");

        when(bookingRepository.findByShowIdAndStatusNotIn(eq(SHOW_ID), any()))
                .thenReturn(List.of(bp));

        fixture.givenAPublished(new ShowEvents.ShowCancelled(SHOW_ID, REASON))
                .whenPublishingA(new BookingEvents.BookingCancelled("bk-001", SHOW_ID, "customer-1", "Show cancelled: " + REASON))
                .expectActiveSagas(0);
    }

    @Test
    @DisplayName("First of two BookingCancelled keeps the saga alive")
    void bookingCancelled_notLastPending_sagaRemainsActive() {
        BookingProjection bp1 = new BookingProjection();
        bp1.setBookingId("bk-001");
        BookingProjection bp2 = new BookingProjection();
        bp2.setBookingId("bk-002");

        when(bookingRepository.findByShowIdAndStatusNotIn(eq(SHOW_ID), any()))
                .thenReturn(List.of(bp1, bp2));

        fixture.givenAPublished(new ShowEvents.ShowCancelled(SHOW_ID, REASON))
                .whenPublishingA(new BookingEvents.BookingCancelled("bk-001", SHOW_ID, "customer-1", "Show cancelled: " + REASON))
                .expectActiveSagas(1);
    }

    @Test
    @DisplayName("Both BookingCancelled received — saga ends")
    void bothBookingsCancelled_endsSaga() {
        BookingProjection bp1 = new BookingProjection();
        bp1.setBookingId("bk-001");
        BookingProjection bp2 = new BookingProjection();
        bp2.setBookingId("bk-002");

        when(bookingRepository.findByShowIdAndStatusNotIn(eq(SHOW_ID), any()))
                .thenReturn(List.of(bp1, bp2));

        fixture.givenAPublished(new ShowEvents.ShowCancelled(SHOW_ID, REASON))
                .andThenAPublished(new BookingEvents.BookingCancelled("bk-001", SHOW_ID, "customer-1", "Show cancelled: " + REASON))
                .whenPublishingA(new BookingEvents.BookingCancelled("bk-002", SHOW_ID, "customer-2", "Show cancelled: " + REASON))
                .expectActiveSagas(0);
    }
}

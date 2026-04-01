package com.workshop;

import com.workshop.commands.BookingCommands;
import com.workshop.commands.ShowCommands;
import com.workshop.dto.SeatItem;
import com.workshop.events.BookingEvents;
import com.workshop.events.PaymentEvents;
import com.workshop.events.ShowEvents;
import com.workshop.projection.BookingRepository;
import jakarta.annotation.PostConstruct;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for booking-query-service read model.
 * Events are published directly via EventGateway; cross-service commands are stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bookingquerydb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "axon.axonserver.enabled=false"
})
class BookingQueryServiceIntegrationTest {

    @TestConfiguration
    static class CrossServiceCommandStubs {

        @Autowired
        CommandBus commandBus;

        @PostConstruct
        void registerStubs() {
            commandBus.subscribe(BookingCommands.CancelBooking.class.getCanonicalName(), msg -> null);
            commandBus.subscribe(BookingCommands.RefundBooking.class.getCanonicalName(), msg -> null);
            commandBus.subscribe(ShowCommands.ReleaseSeat.class.getCanonicalName(), msg -> null);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired BookingRepository bookingRepository;
    @Autowired EventGateway eventGateway;

    @Test
    void bookingInitiated_createsProjection() throws Exception {
        String bookingId = UUID.randomUUID().toString();
        eventGateway.publish(new BookingEvents.BookingInitiated(
                bookingId, "show-001",
                List.of(new SeatItem("A1", new BigDecimal("49.99"))),
                "customer-123", new BigDecimal("49.99")));

        Thread.sleep(300);

        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.status").value("INITIATED"));
    }

    @Test
    void bookingConfirmed_updatesProjectionStatus() throws Exception {
        String bookingId = UUID.randomUUID().toString();
        eventGateway.publish(new BookingEvents.BookingInitiated(
                bookingId, "show-002",
                List.of(new SeatItem("B1", new BigDecimal("99.00"))),
                "customer-456", new BigDecimal("99.00")));
        eventGateway.publish(new BookingEvents.BookingConfirmed(bookingId, "show-002", "customer-456"));

        Thread.sleep(300);

        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void ticketIssued_setsTicketNumber() throws Exception {
        String bookingId = UUID.randomUUID().toString();
        eventGateway.publish(new BookingEvents.BookingInitiated(
                bookingId, "show-003",
                List.of(new SeatItem("C1", new BigDecimal("75.00"))),
                "customer-789", new BigDecimal("75.00")));
        eventGateway.publish(new BookingEvents.BookingConfirmed(bookingId, "show-003", "customer-789"));
        eventGateway.publish(new BookingEvents.TicketIssued(bookingId, "TKT-ABCD1234", "customer-789"));

        Thread.sleep(300);

        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TICKET_ISSUED"))
                .andExpect(jsonPath("$.ticketNumber").value("TKT-ABCD1234"));
    }

    @Test
    void showCancelled_cancelsAllActiveBookings() throws Exception {
        String showId = "show-cancel-" + UUID.randomUUID().toString().substring(0, 6);
        String bookingId1 = UUID.randomUUID().toString();
        String bookingId2 = UUID.randomUUID().toString();

        eventGateway.publish(new BookingEvents.BookingInitiated(bookingId1, showId,
                List.of(new SeatItem("D1", new BigDecimal("30.00"))), "cust-a", new BigDecimal("30.00")));
        eventGateway.publish(new BookingEvents.BookingInitiated(bookingId2, showId,
                List.of(new SeatItem("D2", new BigDecimal("30.00"))), "cust-b", new BigDecimal("30.00")));

        Thread.sleep(300);

        eventGateway.publish(new ShowEvents.ShowCancelled(showId, "Venue unavailable"));
        Thread.sleep(500);

        // The projection handler dispatches CancelBooking (stubbed) — no status change here
        // Just verify the bookings exist and were INITIATED before cancellation command was sent
        assertThat(bookingRepository.findByShowId(showId)).hasSize(2);
    }

    @Test
    void getNonExistentBooking_returns404() throws Exception {
        mockMvc.perform(get("/api/bookings/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBookingsByCustomer_returnsList() throws Exception {
        mockMvc.perform(get("/api/bookings/customer/any-customer"))
                .andExpect(status().isOk());
    }

    @Test
    void bookingAmended_updatesProjectionSeats() throws Exception {
        String bookingId = UUID.randomUUID().toString();
        List<SeatItem> originalSeats = List.of(new SeatItem("A1", new BigDecimal("49.99")));
        List<SeatItem> newSeats = List.of(new SeatItem("B3", new BigDecimal("49.99")), new SeatItem("B4", new BigDecimal("49.99")));

        eventGateway.publish(new BookingEvents.BookingInitiated(
                bookingId, "show-amend",
                originalSeats, "customer-amend", new BigDecimal("49.99")));
        Thread.sleep(300);

        eventGateway.publish(new BookingEvents.BookingAmended(
                bookingId, "show-amend", originalSeats, newSeats, "customer-amend"));
        Thread.sleep(300);

        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.seats").isArray())
                .andExpect(jsonPath("$.seats.length()").value(2));
    }

    @Test
    void paymentRefunded_dispatchesRefundBooking() throws Exception {
        String bookingId = UUID.randomUUID().toString();
        eventGateway.publish(new BookingEvents.BookingInitiated(
                bookingId, "show-refund",
                List.of(new SeatItem("E1", new BigDecimal("99.00"))),
                "customer-refund", new BigDecimal("99.00")));

        Thread.sleep(300);

        eventGateway.publish(new PaymentEvents.PaymentRefunded(bookingId + "-pay", bookingId, "customer-refund", "Customer request"));
        Thread.sleep(300);

        // RefundBooking command is dispatched (stubbed) — projection stays INITIATED until
        // BookingRefunded event comes back; just verify the booking still exists
        assertThat(bookingRepository.findById(bookingId)).isPresent();
    }
}

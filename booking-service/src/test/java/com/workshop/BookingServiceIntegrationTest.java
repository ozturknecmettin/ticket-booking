package com.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.commands.BookingCommands;
import com.workshop.commands.PaymentCommands;
import com.workshop.commands.ShowCommands;
import com.workshop.dto.InitiateBookingRequest;
import com.workshop.dto.SeatItem;
import jakarta.annotation.PostConstruct;
import org.axonframework.commandhandling.CommandBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for booking-service command side.
 * Read model (projections, GET endpoints) lives in booking-query-service.
 * Cross-service commands are handled by no-op stubs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bookingdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "axon.axonserver.enabled=false"
})
class BookingServiceIntegrationTest {

    @TestConfiguration
    static class CrossServiceCommandStubs {

        @Autowired
        CommandBus commandBus;

        @PostConstruct
        void registerStubs() {
            commandBus.subscribe(ShowCommands.ReserveSeat.class.getName(), msg -> null);
            commandBus.subscribe(ShowCommands.ReleaseSeat.class.getName(), msg -> null);
            commandBus.subscribe(PaymentCommands.RequestPayment.class.getName(), msg -> null);
            commandBus.subscribe(BookingCommands.RefundBooking.class.getName(), msg -> null);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void initiateBooking_returns202() throws Exception {
        InitiateBookingRequest req = new InitiateBookingRequest(
                "show-001",
                List.of(new SeatItem("A1", new BigDecimal("49.99"))),
                "customer-123");

        MvcResult asyncPost = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        asyncPost.getAsyncResult();
        mockMvc.perform(asyncDispatch(asyncPost))
                .andExpect(status().isAccepted());
    }

    @Test
    void initiateBooking_tooManySeats_returns400() throws Exception {
        List<SeatItem> tooMany = List.of(
                new SeatItem("A1", new BigDecimal("49.99")),
                new SeatItem("A2", new BigDecimal("49.99")),
                new SeatItem("A3", new BigDecimal("49.99")),
                new SeatItem("A4", new BigDecimal("49.99")),
                new SeatItem("A5", new BigDecimal("49.99")),
                new SeatItem("A6", new BigDecimal("49.99"))
        );
        InitiateBookingRequest req = new InitiateBookingRequest("show-001", tooMany, "customer-123");

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelBooking_returns200() throws Exception {
        // Initiate first
        InitiateBookingRequest req = new InitiateBookingRequest(
                "show-cancel",
                List.of(new SeatItem("B1-" + UUID.randomUUID().toString().substring(0, 4), new BigDecimal("30.00"))),
                "customer-456");

        MvcResult asyncPost = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        asyncPost.getAsyncResult();
        MvcResult postResult = mockMvc.perform(asyncDispatch(asyncPost))
                .andExpect(status().isAccepted())
                .andReturn();

        String bookingId = postResult.getResponse().getContentAsString().replace("\"", "");
        Thread.sleep(200);

        MvcResult asyncDelete = mockMvc.perform(delete("/api/bookings/" + bookingId)
                        .param("reason", "Test cancel"))
                .andReturn();
        asyncDelete.getAsyncResult();
        mockMvc.perform(asyncDispatch(asyncDelete))
                .andExpect(status().isOk());
    }
}

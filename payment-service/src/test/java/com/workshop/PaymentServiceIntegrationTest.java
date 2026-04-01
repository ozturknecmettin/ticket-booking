package com.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.dto.ProcessPaymentRequest;
import com.workshop.projection.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:paymentdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "axon.axonserver.enabled=false"
})
class PaymentServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void clearPayments() {
        paymentRepository.deleteAll();
    }

    @Test
    void processAndConfirmPayment() throws Exception {
        String paymentId = "pay-conf-" + UUID.randomUUID().toString().substring(0, 6);
        String bookingId = "bk-conf-" + UUID.randomUUID().toString().substring(0, 6);

        ProcessPaymentRequest req = new ProcessPaymentRequest(
                paymentId, bookingId, "customer-001", new BigDecimal("49.99"));

        // POST is async (CompletableFuture)
        MvcResult asyncPost = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        asyncPost.getAsyncResult();
        mockMvc.perform(asyncDispatch(asyncPost))
                .andExpect(status().isAccepted());

        Thread.sleep(500);

        // GET is synchronous (uses repository directly)
        mockMvc.perform(get("/api/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.amount").value(49.99));

        // confirm is async (CompletableFuture)
        MvcResult asyncConfirm = mockMvc.perform(post("/api/payments/" + paymentId + "/confirm")).andReturn();
        asyncConfirm.getAsyncResult();
        mockMvc.perform(asyncDispatch(asyncConfirm))
                .andExpect(status().isOk());

        Thread.sleep(500);

        // GET is synchronous
        mockMvc.perform(get("/api/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void getPaymentByBookingId() throws Exception {
        String paymentId = "pay-bk-" + UUID.randomUUID().toString().substring(0, 6);
        String bookingId = "bk-bk-" + UUID.randomUUID().toString().substring(0, 6);

        ProcessPaymentRequest req = new ProcessPaymentRequest(
                paymentId, bookingId, "customer-002", new BigDecimal("99.00"));

        // POST is async
        MvcResult asyncPost = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        asyncPost.getAsyncResult();
        mockMvc.perform(asyncDispatch(asyncPost))
                .andExpect(status().isAccepted());

        Thread.sleep(500);

        // GET by booking is synchronous
        mockMvc.perform(get("/api/payments/booking/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId));
    }

    @Test
    void getNonExistentPayment_returns404() throws Exception {
        mockMvc.perform(get("/api/payments/nonexistent"))
                .andExpect(status().isNotFound());
    }
}

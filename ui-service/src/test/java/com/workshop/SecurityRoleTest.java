package com.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityRoleTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  RestTemplate restTemplate;

    // Passes security (not 401/403) — downstream may return 5xx since RestTemplate is mocked
    private static final ResultMatcher PASSES_SECURITY =
            result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);

    private String adminToken;
    private String customerToken;

    @BeforeAll
    void obtainTokens() throws Exception {
        adminToken    = login("admin",    "admin123");
        customerToken = login("customer", "customer123");
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    // ── Unauthenticated ────────────────────────────────────────────────────

    @Test
    void unauthenticated_proxyRequest_returns401() throws Exception {
        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_rootPage_returns200() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    // ── Admin access ───────────────────────────────────────────────────────

    @Test
    void admin_canAccessCreateShow() throws Exception {
        mockMvc.perform(post("/proxy/shows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(PASSES_SECURITY);
    }

    @Test
    void admin_canAccessConfirmPayment() throws Exception {
        mockMvc.perform(post("/proxy/payments/pay-001/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(PASSES_SECURITY);
    }

    @Test
    void admin_canAccessFailPayment() throws Exception {
        mockMvc.perform(post("/proxy/payments/pay-001/fail")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(PASSES_SECURITY);
    }

    // ── Customer blocked from admin endpoints ──────────────────────────────

    @Test
    void customer_cannotCreateShow_gets403() throws Exception {
        mockMvc.perform(post("/proxy/shows")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_cannotConfirmPayment_gets403() throws Exception {
        mockMvc.perform(post("/proxy/payments/pay-001/confirm")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_cannotFailPayment_gets403() throws Exception {
        mockMvc.perform(post("/proxy/payments/pay-001/fail")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_cannotCancelShow_gets403() throws Exception {
        mockMvc.perform(delete("/proxy/shows/show-001")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    // ── Customer allowed endpoints ─────────────────────────────────────────

    @Test
    void customer_cannotReadAnotherCustomersBookings_gets403() throws Exception {
        mockMvc.perform(get("/proxy/bookings/customer/customer2")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_canReadOwnBookings() throws Exception {
        when(restTemplate.getForEntity(contains("/api/bookings/customer/customer"), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[]"));

        mockMvc.perform(get("/proxy/bookings/customer/customer")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(PASSES_SECURITY);
    }

    @Test
    void customer_canCreateBooking() throws Exception {
        mockMvc.perform(post("/proxy/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(PASSES_SECURITY);
    }

    @Test
    void customer_canRefundOwnPayment() throws Exception {
        // Mock payment lookup → bookingId = "booking-001"
        when(restTemplate.getForObject(contains("/api/payments/pay-own"), eq(String.class)))
                .thenReturn("{\"bookingId\":\"booking-001\",\"customerId\":\"customer\"}");
        // Mock booking lookup → customerId = "customer" (matches logged-in user)
        when(restTemplate.getForObject(contains("/api/bookings/booking-001"), eq(String.class)))
                .thenReturn("{\"bookingId\":\"booking-001\",\"customerId\":\"customer\"}");
        when(restTemplate.postForEntity(contains("/api/payments/pay-own/refund"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("\"ok\""));

        mockMvc.perform(post("/proxy/payments/pay-own/refund")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(PASSES_SECURITY);
    }

    @Test
    void customer_cannotRefundAnotherCustomersPayment_gets403() throws Exception {
        // Mock payment → booking owned by a different customer
        when(restTemplate.getForObject(contains("/api/payments/pay-other"), eq(String.class)))
                .thenReturn("{\"bookingId\":\"booking-other\",\"customerId\":\"customer2\"}");
        when(restTemplate.getForObject(contains("/api/bookings/booking-other"), eq(String.class)))
                .thenReturn("{\"bookingId\":\"booking-other\",\"customerId\":\"customer2\"}");

        mockMvc.perform(post("/proxy/payments/pay-other/refund")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }
}

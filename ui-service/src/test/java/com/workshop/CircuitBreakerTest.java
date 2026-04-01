package com.workshop;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.show-service.sliding-window-size=2",
        "resilience4j.circuitbreaker.instances.show-service.failure-rate-threshold=100"
})
class CircuitBreakerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RestTemplate restTemplate;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("show-service");
        cb.reset();
        cb.transitionToClosedState();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void happyPath_returnsDownstreamResponse() throws Exception {
        when(restTemplate.getForEntity(contains("/api/shows"), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[{\"id\":\"show-1\"}]"));

        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("show-1")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void serviceDown_returns503() throws Exception {
        when(restTemplate.getForEntity(contains("/api/shows"), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("unavailable")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void circuitOpens_afterEnoughFailures_returns503WithCircuitMessage() throws Exception {
        when(restTemplate.getForEntity(contains("/api/shows"), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // First two calls fill the sliding window and open the circuit
        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isServiceUnavailable());

        // Third call should be rejected by the open circuit breaker
        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("circuit open")));

        // RestTemplate should only have been called for the first two requests
        verify(restTemplate, times(2)).getForEntity(contains("/api/shows"), eq(String.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void clientError_doesNotOpenCircuit() throws Exception {
        when(restTemplate.getForEntity(contains("/api/shows"), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        // Two 404s — these are ignored exceptions so they do NOT count as failures
        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isNotFound());

        // Circuit should still be CLOSED — third call must still reach restTemplate
        mockMvc.perform(get("/proxy/shows"))
                .andExpect(status().isNotFound());

        verify(restTemplate, times(3)).getForEntity(contains("/api/shows"), eq(String.class));
    }
}

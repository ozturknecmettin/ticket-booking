package com.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void login_withinLimit_returns200Or401() throws Exception {
        // 5 attempts should all be processed (not rate-limited)
        // Use isolated IP 10.0.0.1 so this test's bucket does not interfere with others
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .with(request -> { request.setRemoteAddr("10.0.0.1"); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "admin", "password", "admin123"))))
                    .andExpect(status().isOk()); // valid creds, should get 200
        }
    }

    @Test
    void login_exceedsLimit_returns429() throws Exception {
        // Use isolated IP 10.0.0.2 so bucket is not shared with login_withinLimit test
        String body = objectMapper.writeValueAsString(
                Map.of("username", "admin", "password", "wrong"));

        // Exhaust the 5-per-minute bucket
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                    .with(request -> { request.setRemoteAddr("10.0.0.2"); return request; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }

        // 6th request must be rate-limited
        mockMvc.perform(post("/auth/login")
                        .with(request -> { request.setRemoteAddr("10.0.0.2"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.error").value("Too many requests \u2014 please try again later"));
    }

    @Test
    void register_exceedsLimit_returns429() throws Exception {
        // Use isolated IP 10.0.0.3 for register tests
        String body = objectMapper.writeValueAsString(
                Map.of("username", "rl-user-" + System.nanoTime(), "password", "pass", "role", "CUSTOMER", "fullName", "RL User"));

        // Exhaust the 3-per-minute bucket (use unique usernames so they don't hit 409)
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            mockMvc.perform(post("/auth/register")
                            .with(request -> { request.setRemoteAddr("10.0.0.3"); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "rl-u-" + System.nanoTime() + idx,
                                           "password", "pass", "role", "CUSTOMER", "fullName", "RL " + idx))))
                    .andExpect(status().isCreated());
        }

        // 4th request must be rate-limited
        mockMvc.perform(post("/auth/register")
                        .with(request -> { request.setRemoteAddr("10.0.0.3"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }
}

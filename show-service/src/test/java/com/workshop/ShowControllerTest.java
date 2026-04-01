package com.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.controller.ShowController;
import com.workshop.dto.CreateShowRequest;
import com.workshop.projection.ShowProjection;
import com.workshop.projection.ShowRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShowController.class)
class ShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private ShowRepository showRepository;

    private ShowProjection show(String id) {
        return ShowProjection.builder()
                .showId(id).title("Hamilton").venue("Broadway")
                .totalSeats(100).availableSeats(99)
                .ticketPrice(new BigDecimal("49.99")).status("ACTIVE")
                .reservedSeats(Set.of("A1"))
                .build();
    }

    @Test
    void createShow_returns201() throws Exception {
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture("show-001"));

        CreateShowRequest req = new CreateShowRequest("Hamilton", "Broadway", 100, new BigDecimal("49.99"),
                java.time.LocalDateTime.of(2026, 6, 1, 19, 30), null, null, null, null, null);

        mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void getShow_returnsShow() throws Exception {
        when(showRepository.findById("show-001")).thenReturn(Optional.of(show("show-001")));

        mockMvc.perform(get("/api/shows/show-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value("show-001"))
                .andExpect(jsonPath("$.title").value("Hamilton"))
                .andExpect(jsonPath("$.reservedSeats").isArray());
    }

    @Test
    void getShow_returns404WhenNotFound() throws Exception {
        when(showRepository.findById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/shows/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createShow_returns400_whenTitleIsBlank() throws Exception {
        CreateShowRequest req = new CreateShowRequest("", "Broadway", 100, new BigDecimal("49.99"),
                LocalDateTime.now().plusMonths(3), null, null, null, null, null);

        mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShow_returns400_whenEventDateIsInPast() throws Exception {
        CreateShowRequest req = new CreateShowRequest("Hamilton", "Broadway", 100, new BigDecimal("49.99"),
                LocalDateTime.of(2020, 1, 1, 19, 30), null, null, null, null, null);

        mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShow_returns400_whenSeatsIsZero() throws Exception {
        CreateShowRequest req = new CreateShowRequest("Hamilton", "Broadway", 0, new BigDecimal("49.99"),
                LocalDateTime.now().plusMonths(3), null, null, null, null, null);

        mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllShows_returnsPagedResult() throws Exception {
        var shows = List.of(show("show-001"));
        when(showRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(shows, PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/shows"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].showId").value("show-001"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }
}

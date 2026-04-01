package com.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.dto.CreateShowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:showdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "axon.axonserver.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ShowServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullLifecycle_createAndGetShow() throws Exception {
        CreateShowRequest req = new CreateShowRequest("Phantom of the Opera", "West End", 200, new BigDecimal("79.99"),
                java.time.LocalDateTime.of(2026, 8, 1, 19, 30), null, null, null, null, null);

        // Create show — controller blocks on future.join(), response is synchronous
        MvcResult postResult = mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String showId = postResult.getResponse().getContentAsString().replace("\"", "");

        // Give event processor time to update read model
        Thread.sleep(500);

        // Get show — synchronous (uses repository directly)
        mockMvc.perform(get("/api/shows/" + showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Phantom of the Opera"))
                .andExpect(jsonPath("$.availableSeats").value(200))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getActiveShows_returnsPagedResult() throws Exception {
        mockMvc.perform(get("/api/shows/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }
}

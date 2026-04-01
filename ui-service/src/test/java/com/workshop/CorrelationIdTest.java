package com.workshop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RestTemplate restTemplate;

    private static final String HEADER = "X-Correlation-ID";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Test
    @DisplayName("Request without X-Correlation-ID gets one generated and echoed in response")
    void noIncomingHeader_generatesAndEchoes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    @DisplayName("Request with X-Correlation-ID echoes the same value in response")
    void incomingHeader_echoedBack() throws Exception {
        String correlationId = "test-correlation-id-123";

        mockMvc.perform(get("/actuator/health")
                        .header(HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, correlationId));
    }

    @Test
    @DisplayName("Blank X-Correlation-ID is treated as absent and a new one is generated")
    void blankHeader_generatesNew() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(HEADER, "   "))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, matchesPattern(UUID_PATTERN)));
    }
}

package com.workshop.controller;

import com.workshop.config.ChaosConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final JdbcTemplate jdbcTemplate;
    private final ChaosConfig chaosConfig;

    private static final String PROCESSING_GROUP = "payment-projections";

    @GetMapping("/chaos")
    public Map<String, Object> getChaosStatus() {
        return Map.of(
                "chaosEnabled", chaosConfig.isChaosEnabled(),
                "failureRate",  chaosConfig.getFailureRate()
        );
    }

    @PostMapping("/chaos")
    public Map<String, Object> setChaos(@RequestParam boolean enabled) {
        chaosConfig.setChaosEnabled(enabled);
        return Map.of(
                "chaosEnabled", chaosConfig.isChaosEnabled(),
                "failureRate",  chaosConfig.getFailureRate()
        );
    }

    @GetMapping("/dlq-depth")
    public Map<String, Object> dlqDepth() {
        try {
            Long sequences = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT sequence_identifier) FROM dead_letter_entry WHERE processing_group = ?",
                    Long.class, PROCESSING_GROUP);
            return Map.of("processingGroup", PROCESSING_GROUP, "sequences", sequences != null ? sequences : 0L);
        } catch (Exception e) {
            return Map.of("processingGroup", PROCESSING_GROUP, "sequences", 0L);
        }
    }
}

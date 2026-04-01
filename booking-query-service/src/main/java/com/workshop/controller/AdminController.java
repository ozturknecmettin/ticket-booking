package com.workshop.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final JdbcTemplate jdbcTemplate;

    private static final String PROCESSING_GROUP = "booking-projections";

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

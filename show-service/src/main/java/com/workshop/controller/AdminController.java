package com.workshop.controller;

import com.workshop.commands.ShowCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final JdbcTemplate jdbcTemplate;
    private final EventStore eventStore;
    private final CommandGateway commandGateway;

    private static final String PROCESSING_GROUP = "show-projections";

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

    @GetMapping("/events/{aggregateId}")
    public List<Map<String, Object>> getAggregateEvents(@PathVariable String aggregateId) {
        List<Map<String, Object>> events = new ArrayList<>();
        try {
            eventStore.readEvents(aggregateId).forEachRemaining(msg -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("sequenceNumber", msg.getSequenceNumber());
                e.put("timestamp", msg.getTimestamp().toString());
                e.put("eventType", msg.getPayloadType().getSimpleName());
                e.put("payload", msg.getPayload());
                events.add(e);
            });
        } catch (Exception ex) {
            log.debug("Could not read events for aggregate {}: {}", aggregateId, ex.getMessage());
        }
        return events;
    }

    @PostMapping("/shows/{showId}/release-seat")
    public Map<String, Object> releaseSeat(@PathVariable String showId,
                                            @RequestParam String seatNumber,
                                            @RequestParam String bookingId) {
        log.warn("Admin force-releasing seat {} from show {} (booking {})", seatNumber, showId, bookingId);
        try {
            commandGateway.sendAndWait(new ShowCommands.ReleaseSeat(showId, seatNumber, bookingId));
            return Map.of("released", true, "showId", showId, "seat", seatNumber);
        } catch (Exception e) {
            return Map.of("released", false, "error", e.getMessage());
        }
    }
}

package com.workshop.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventsourcing.eventstore.EventStore;
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

    private final EventStore eventStore;

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
}

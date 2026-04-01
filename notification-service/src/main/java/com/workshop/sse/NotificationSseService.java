package com.workshop.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maintains per-customer SSE connections. Each customer key maps to its own
 * list of emitters; the special key {@code "all"} is used for admin/broadcast
 * connections that receive every notification regardless of customer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSseService {

    public static final String ALL_KEY = "all";

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emittersByCustomer =
            new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * Registers a new SSE connection. Pass {@code "all"} (or blank/null) for
     * admin connections that should receive every notification.
     */
    public SseEmitter addEmitter(String customerId) {
        String key = (customerId == null || customerId.isBlank()) ? ALL_KEY : customerId;
        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout; browser auto-reconnects

        emittersByCustomer.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emittersByCustomer.get(key);
            if (list != null) list.remove(emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        int total = emittersByCustomer.values().stream().mapToInt(List::size).sum();
        log.debug("SSE client connected (customer={}) — {} active emitter(s)", key, total);
        return emitter;
    }

    /**
     * Pushes a notification to the customer's own emitters and to all "all" (admin) emitters.
     */
    public void push(NotificationResponse notification) {
        String customerId = notification.customerId();
        if (customerId != null && !customerId.isBlank()) {
            pushToList(emittersByCustomer.get(customerId), notification);
        }
        pushToList(emittersByCustomer.get(ALL_KEY), notification);
    }

    private void pushToList(CopyOnWriteArrayList<SseEmitter> list, NotificationResponse notification) {
        if (list == null || list.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(notification);
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(notification.notificationType())
                    .data(json, MediaType.APPLICATION_JSON);

            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(event);
                } catch (IOException e) {
                    dead.add(emitter);
                }
            }
            list.removeAll(dead);
            if (!dead.isEmpty()) {
                log.debug("Removed {} dead SSE emitter(s)", dead.size());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize SSE notification", e);
        }
    }
}

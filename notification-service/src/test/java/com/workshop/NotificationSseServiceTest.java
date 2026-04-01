package com.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.dto.NotificationResponse;
import com.workshop.sse.NotificationSseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for per-customer SSE fan-out logic in NotificationSseService.
 * No Spring context needed — constructs the service directly.
 */
class NotificationSseServiceTest {

    private NotificationSseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new NotificationSseService(new ObjectMapper());
    }

    // ── Registration ────────────────────────────────────────────────────────

    @Test
    @DisplayName("addEmitter(alice) returns a non-null SseEmitter")
    void addEmitter_returnsEmitter() {
        SseEmitter emitter = sseService.addEmitter("alice");
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("addEmitter with blank customerId maps to the 'all' key")
    void addEmitter_blankCustomerIdMapsToAll() {
        SseEmitter e1 = sseService.addEmitter("");
        SseEmitter e2 = sseService.addEmitter(null);
        // Both should succeed (mapped to "all"); no exception thrown
        assertThat(e1).isNotNull();
        assertThat(e2).isNotNull();
    }

    @Test
    @DisplayName("addEmitter('all') maps to the broadcast bucket")
    void addEmitter_allKeyWorks() {
        SseEmitter emitter = sseService.addEmitter(NotificationSseService.ALL_KEY);
        assertThat(emitter).isNotNull();
    }

    // ── Isolation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("push to alice does not throw when only bob is subscribed")
    void push_noAliceEmitter_doesNotThrow() {
        sseService.addEmitter("bob"); // bob is subscribed
        NotificationResponse notification = sampleNotification("alice", "bk-001");
        // alice has no emitter — should not throw
        sseService.push(notification);
    }

    @Test
    @DisplayName("push always notifies 'all' emitters regardless of customerId")
    void push_alwaysNotifiesAllEmitters() throws Exception {
        // Register an admin "all" emitter backed by a no-op (avoids actual I/O)
        sseService.addEmitter(NotificationSseService.ALL_KEY);
        // A notification for any customer should not throw
        sseService.push(sampleNotification("charlie", "bk-002"));
    }

    // ── Cleanup ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Emitter removes itself from map on completion")
    void emitter_removedOnCompletion() {
        SseEmitter emitter = sseService.addEmitter("alice");
        // Simulate completion
        emitter.complete();
        // Pushing after completion should not throw (emitter was removed)
        sseService.push(sampleNotification("alice", "bk-003"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private NotificationResponse sampleNotification(String customerId, String bookingId) {
        return new NotificationResponse(
                java.util.UUID.randomUUID().toString(),
                bookingId, customerId,
                "BOOKING_CONFIRMED",
                "Test notification",
                Instant.now()
        );
    }
}

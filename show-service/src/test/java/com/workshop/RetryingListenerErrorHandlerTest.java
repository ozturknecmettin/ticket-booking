package com.workshop;

import com.workshop.config.RetryingListenerErrorHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingListenerErrorHandlerTest {

    private final RetryingListenerErrorHandler handler = new RetryingListenerErrorHandler();

    /** Builds a minimal EventMessage for the given payload. */
    private EventMessage<String> msg(String payload) {
        return GenericEventMessage.asEventMessage(payload);
    }

    @Test
    @DisplayName("Handler succeeds on first retry — no exception thrown")
    void succeeds_onFirstRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        EventMessageHandler invoker = event -> {
            if (calls.incrementAndGet() < 2) throw new RuntimeException("transient");
            return null;
        };

        // initial exception + 1 retry ⟹ should succeed
        handler.onError(new RuntimeException("transient"), msg("test"), invoker);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Handler succeeds on third (last) retry — no exception thrown")
    void succeeds_onThirdRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        // Throws on first two retry attempts, succeeds on the third
        EventMessageHandler invoker = event -> {
            if (calls.incrementAndGet() < 3) throw new RuntimeException("transient");
            return null;
        };

        handler.onError(new RuntimeException("transient"), msg("test"), invoker);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Handler exhausts all retries and rethrows last exception")
    void exhaustsRetries_rethrows() {
        RuntimeException boom = new RuntimeException("permanent");
        EventMessageHandler alwaysFails = event -> { throw boom; };

        assertThatThrownBy(() -> handler.onError(boom, msg("test"), alwaysFails))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("permanent");
    }

    @Test
    @DisplayName("Exactly MAX_RETRIES (3) attempts are made before giving up")
    void exactlyThreeRetries_beforeGivingUp() {
        AtomicInteger calls = new AtomicInteger(0);
        EventMessageHandler alwaysFails = event -> {
            calls.incrementAndGet();
            throw new RuntimeException("fail");
        };

        assertThatThrownBy(() -> handler.onError(new RuntimeException("fail"), msg("test"), alwaysFails));
        assertThat(calls.get()).isEqualTo(3); // 3 retry attempts
    }
}

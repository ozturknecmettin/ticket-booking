package com.workshop.config;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;

/**
 * Event-handler error handler that retries with exponential back-off before
 * letting the event fall through to the dead-letter queue.
 *
 * <pre>
 *   attempt 1 → wait 500 ms
 *   attempt 2 → wait 1 000 ms
 *   attempt 3 → wait 2 000 ms
 *   → propagate → DLQ
 * </pre>
 */
@Slf4j
public class RetryingListenerErrorHandler implements ListenerInvocationErrorHandler {

    private static final int  MAX_RETRIES   = 3;
    private static final long BASE_DELAY_MS = 500;

    @Override
    public void onError(Exception exception,
                        EventMessage<?> event,
                        EventMessageHandler invoker) throws Exception {
        String eventType = event.getPayloadType().getSimpleName();
        Exception last = exception;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            long delayMs = BASE_DELAY_MS * (1L << attempt); // 500, 1000, 2000
            log.warn("Retry {}/{} for event {} after {}ms: {}",
                    attempt + 1, MAX_RETRIES, eventType, delayMs, last.getMessage());
            try {
                Thread.sleep(delayMs);
                invoker.handle(event);
                return; // success — stop retrying
            } catch (Exception e) {
                last = e;
            }
        }
        log.error("All {} retries exhausted for event {}; routing to DLQ", MAX_RETRIES, eventType);
        throw last;
    }
}

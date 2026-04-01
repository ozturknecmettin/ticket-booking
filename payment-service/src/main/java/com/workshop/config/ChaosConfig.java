package com.workshop.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configurable chaos toggle for the payment service.
 * When chaos is enabled, ConfirmPayment randomly fails with the configured probability.
 */
@Component
@Slf4j
public class ChaosConfig {

    @Value("${payment.chaos.failure-rate:0.5}")
    private double failureRate;

    private final AtomicBoolean chaosEnabled = new AtomicBoolean(false);

    public boolean isChaosEnabled() {
        return chaosEnabled.get();
    }

    public void setChaosEnabled(boolean enabled) {
        chaosEnabled.set(enabled);
        log.warn("Chaos mode {}: failure-rate={}", enabled ? "ENABLED" : "DISABLED", failureRate);
    }

    /** Returns true if the current request should be chaos-failed. */
    public boolean shouldFail() {
        return chaosEnabled.get() && ThreadLocalRandom.current().nextDouble() < failureRate;
    }

    public double getFailureRate() {
        return failureRate;
    }
}

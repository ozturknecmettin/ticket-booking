package com.workshop;

import com.workshop.config.ChaosConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosConfigTest {

    private final ChaosConfig chaos = new ChaosConfig();

    @Test
    @DisplayName("Chaos is disabled by default")
    void chaosDisabledByDefault() {
        assertThat(chaos.isChaosEnabled()).isFalse();
    }

    @Test
    @DisplayName("shouldFail returns false when chaos is disabled")
    void shouldFail_whenDisabled_returnsFalse() {
        chaos.setChaosEnabled(false);
        // Even with 100% failure rate, shouldFail must be false when chaos is off
        for (int i = 0; i < 20; i++) {
            assertThat(chaos.shouldFail()).isFalse();
        }
    }

    @Test
    @DisplayName("setChaosEnabled toggles isChaosEnabled")
    void setChaosEnabled_toggles() {
        chaos.setChaosEnabled(true);
        assertThat(chaos.isChaosEnabled()).isTrue();
        chaos.setChaosEnabled(false);
        assertThat(chaos.isChaosEnabled()).isFalse();
    }

    @Test
    @DisplayName("shouldFail always returns true when chaos is enabled with rate=1.0")
    void shouldFail_whenEnabled_alwaysFailsAtRate1() {
        ReflectionTestUtils.setField(chaos, "failureRate", 1.0);
        chaos.setChaosEnabled(true);
        assertThat(chaos.shouldFail()).isTrue();
    }

    @Test
    @DisplayName("getFailureRate returns the value set via reflection")
    void getFailureRate_returnsConfiguredValue() {
        ReflectionTestUtils.setField(chaos, "failureRate", 0.75);
        assertThat(chaos.getFailureRate()).isEqualTo(0.75);
    }
}

package com.workshop.config;

import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures snapshot support for the BookingAggregate.
 *
 * A snapshot is taken every {@code booking.snapshot-threshold} events, so the
 * aggregate is reconstructed from (snapshot + new events) rather than replaying
 * the full event stream from the beginning. This keeps load times O(1) regardless
 * of how many events the aggregate has accumulated.
 *
 * The bean name "bookingSnapshotTrigger" is referenced via
 * {@code @Aggregate(snapshotTriggerDefinition = "bookingSnapshotTrigger")}.
 */
@Configuration
public class SnapshotConfig {

    @Value("${booking.snapshot-threshold:50}")
    private int snapshotThreshold;

    @Bean
    public SnapshotTriggerDefinition bookingSnapshotTrigger(Snapshotter snapshotter) {
        return new EventCountSnapshotTriggerDefinition(snapshotter, snapshotThreshold);
    }
}

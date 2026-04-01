package com.workshop.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Subset of booking commands dispatched by the projection handler
 * in response to cross-service events (e.g., ShowCancelled, PaymentRefunded).
 * Will move to ShowCancellationSaga in Sprint 2.
 */
public sealed interface BookingCommands permits
        BookingCommands.CancelBooking,
        BookingCommands.RefundBooking {

    record CancelBooking(
            @TargetAggregateIdentifier String bookingId,
            String reason
    ) implements BookingCommands {}

    record RefundBooking(
            @TargetAggregateIdentifier String bookingId,
            String reason
    ) implements BookingCommands {}
}

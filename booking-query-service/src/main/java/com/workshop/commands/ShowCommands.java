package com.workshop.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Show commands dispatched by the projection handler when releasing seats
 * after a booking refund. Will move to a proper saga in Sprint 2.
 */
public sealed interface ShowCommands permits
        ShowCommands.ReleaseSeat {

    record ReleaseSeat(
            @TargetAggregateIdentifier String showId,
            String seatNumber,
            String bookingId
    ) implements ShowCommands {}
}

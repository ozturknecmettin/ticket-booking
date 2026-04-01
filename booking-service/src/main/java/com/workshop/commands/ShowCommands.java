package com.workshop.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Commands sent by the booking saga to show-service.
 */
public sealed interface ShowCommands permits
        ShowCommands.ReserveSeat,
        ShowCommands.ReleaseSeat {

    record ReserveSeat(
            @TargetAggregateIdentifier String showId,
            String seatNumber,
            String bookingId
    ) implements ShowCommands {}

    record ReleaseSeat(
            @TargetAggregateIdentifier String showId,
            String seatNumber,
            String bookingId
    ) implements ShowCommands {}
}

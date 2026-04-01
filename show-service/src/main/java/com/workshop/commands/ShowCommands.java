package com.workshop.commands;

import com.workshop.dto.PriceZone;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public sealed interface ShowCommands permits
        ShowCommands.CreateShow,
        ShowCommands.ReserveSeat,
        ShowCommands.ReleaseSeat,
        ShowCommands.CancelShow,
        ShowCommands.HoldSeat,
        ShowCommands.ConfirmHold,
        ShowCommands.ExpireHold {

    record CreateShow(
            @TargetAggregateIdentifier String showId,
            String title,
            String venue,
            int totalSeats,
            BigDecimal ticketPrice,
            LocalDateTime eventDate,
            String category,
            String artistName,
            String description,
            Integer durationMinutes,
            List<PriceZone> priceZones
    ) implements ShowCommands {}

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

    record CancelShow(
            @TargetAggregateIdentifier String showId,
            String reason
    ) implements ShowCommands {}

    record HoldSeat(
            @TargetAggregateIdentifier String showId,
            String seatNumber,
            String bookingId,
            int durationSeconds
    ) implements ShowCommands {}

    record ConfirmHold(
            @TargetAggregateIdentifier String showId,
            String seatNumber,
            String bookingId
    ) implements ShowCommands {}

    /** Fired by the aggregate's own deadline handler when a hold expires. */
    record ExpireHold(
            @TargetAggregateIdentifier String showId,
            String seatNumber,
            String bookingId
    ) implements ShowCommands {}
}

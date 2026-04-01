package com.workshop.events;

import com.workshop.dto.PriceZone;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public sealed interface ShowEvents permits
        ShowEvents.ShowCreated,
        ShowEvents.SeatReserved,
        ShowEvents.SeatReleased,
        ShowEvents.ShowCancelled,
        ShowEvents.SeatHeld,
        ShowEvents.HoldConfirmed,
        ShowEvents.HoldExpired {

    record ShowCreated(
            String showId,
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
    ) implements ShowEvents {}

    record SeatReserved(
            String showId,
            String seatNumber,
            String bookingId
    ) implements ShowEvents {}

    record SeatReleased(
            String showId,
            String seatNumber,
            String bookingId
    ) implements ShowEvents {}

    record ShowCancelled(
            String showId,
            String reason
    ) implements ShowEvents {}

    record SeatHeld(
            String showId,
            String seatNumber,
            String bookingId
    ) implements ShowEvents {}

    record HoldConfirmed(
            String showId,
            String seatNumber,
            String bookingId
    ) implements ShowEvents {}

    record HoldExpired(
            String showId,
            String seatNumber,
            String bookingId
    ) implements ShowEvents {}
}

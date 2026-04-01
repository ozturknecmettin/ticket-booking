package com.workshop.events;

/**
 * Show events published by show-service.
 * Duplicated here so booking-query-service classpath is self-contained.
 */
public sealed interface ShowEvents permits
        ShowEvents.SeatReserved,
        ShowEvents.SeatReleased,
        ShowEvents.ShowCancelled {

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
}

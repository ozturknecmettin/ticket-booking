package com.workshop.events;

/**
 * Show events published by show-service and consumed by the booking saga.
 * Duplicated here so the booking-service classpath is self-contained.
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

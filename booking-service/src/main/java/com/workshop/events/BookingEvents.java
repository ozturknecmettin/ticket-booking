package com.workshop.events;

import com.workshop.dto.SeatItem;

import java.math.BigDecimal;
import java.util.List;

public sealed interface BookingEvents permits
        BookingEvents.BookingInitiated,
        BookingEvents.BookingConfirmed,
        BookingEvents.BookingCancelled,
        BookingEvents.TicketIssued,
        BookingEvents.BookingRefunded,
        BookingEvents.BookingAmended {

    record BookingInitiated(
            String bookingId,
            String showId,
            List<SeatItem> seats,
            String customerId,
            BigDecimal totalAmount
    ) implements BookingEvents {}

    record BookingConfirmed(
            String bookingId,
            String showId,
            String customerId
    ) implements BookingEvents {}

    record BookingCancelled(
            String bookingId,
            String showId,
            String customerId,
            String reason
    ) implements BookingEvents {}

    record TicketIssued(
            String bookingId,
            String ticketNumber,
            String customerId
    ) implements BookingEvents {}

    record BookingRefunded(
            String bookingId,
            String showId,
            List<SeatItem> seats,
            String reason
    ) implements BookingEvents {}

    record BookingAmended(
            String bookingId,
            String showId,
            List<SeatItem> oldSeats,
            List<SeatItem> newSeats,
            String customerId
    ) implements BookingEvents {}
}

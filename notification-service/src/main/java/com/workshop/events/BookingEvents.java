package com.workshop.events;

/**
 * Booking events consumed by notification-service.
 * Mirrors the subset of events published by booking-service.
 */
public sealed interface BookingEvents permits
        BookingEvents.BookingConfirmed,
        BookingEvents.BookingCancelled,
        BookingEvents.TicketIssued,
        BookingEvents.BookingAmended {

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

    /** Only the fields the notification service needs; extra fields (oldSeats/newSeats) are ignored. */
    record BookingAmended(
            String bookingId,
            String showId,
            String customerId
    ) implements BookingEvents {}
}

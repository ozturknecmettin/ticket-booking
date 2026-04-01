package com.workshop.query;

public sealed interface BookingQueries permits
        BookingQueries.GetBooking,
        BookingQueries.GetBookingsByCustomer,
        BookingQueries.GetBookingsByShow {

    record GetBooking(String bookingId) implements BookingQueries {}

    record GetBookingsByCustomer(String customerId) implements BookingQueries {}

    record GetBookingsByShow(String showId) implements BookingQueries {}
}

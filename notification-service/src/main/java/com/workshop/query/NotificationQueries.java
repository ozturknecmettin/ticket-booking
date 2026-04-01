package com.workshop.query;

public sealed interface NotificationQueries permits
        NotificationQueries.GetNotificationsByCustomer,
        NotificationQueries.GetNotificationsByBooking {

    record GetNotificationsByCustomer(String customerId) implements NotificationQueries {}

    record GetNotificationsByBooking(String bookingId) implements NotificationQueries {}
}

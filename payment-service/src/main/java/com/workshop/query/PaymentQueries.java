package com.workshop.query;

public sealed interface PaymentQueries permits
        PaymentQueries.GetPayment,
        PaymentQueries.GetPaymentByBooking,
        PaymentQueries.GetPaymentsByCustomer {

    record GetPayment(String paymentId) implements PaymentQueries {}

    record GetPaymentByBooking(String bookingId) implements PaymentQueries {}

    record GetPaymentsByCustomer(String customerId) implements PaymentQueries {}
}

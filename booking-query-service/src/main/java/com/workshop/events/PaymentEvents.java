package com.workshop.events;

import java.math.BigDecimal;

/**
 * Payment events published by payment-service.
 * Duplicated here so booking-query-service classpath is self-contained.
 */
public sealed interface PaymentEvents permits
        PaymentEvents.PaymentRequested,
        PaymentEvents.PaymentConfirmed,
        PaymentEvents.PaymentFailed,
        PaymentEvents.PaymentRefunded {

    record PaymentRequested(
            String paymentId,
            String bookingId,
            String customerId,
            BigDecimal amount
    ) implements PaymentEvents {}

    record PaymentConfirmed(
            String paymentId,
            String bookingId
    ) implements PaymentEvents {}

    record PaymentFailed(
            String paymentId,
            String bookingId,
            String reason
    ) implements PaymentEvents {}

    record PaymentRefunded(
            String paymentId,
            String bookingId,
            String customerId,
            String reason
    ) implements PaymentEvents {}
}

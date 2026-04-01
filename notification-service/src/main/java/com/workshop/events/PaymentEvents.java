package com.workshop.events;

import java.math.BigDecimal;

/**
 * Payment events consumed by notification-service.
 */
public sealed interface PaymentEvents permits
        PaymentEvents.PaymentRefunded {

    record PaymentRefunded(
            String paymentId,
            String bookingId,
            String customerId,
            String reason
    ) implements PaymentEvents {}
}

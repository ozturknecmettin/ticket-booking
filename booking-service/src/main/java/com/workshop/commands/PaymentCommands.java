package com.workshop.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;
import java.math.BigDecimal;

/**
 * Commands sent by the booking saga to payment-service.
 */
public sealed interface PaymentCommands permits
        PaymentCommands.RequestPayment,
        PaymentCommands.FailPayment {

    record RequestPayment(
            @TargetAggregateIdentifier String paymentId,
            String bookingId,
            String customerId,
            BigDecimal amount
    ) implements PaymentCommands {}

    record FailPayment(
            @TargetAggregateIdentifier String paymentId,
            String reason
    ) implements PaymentCommands {}
}

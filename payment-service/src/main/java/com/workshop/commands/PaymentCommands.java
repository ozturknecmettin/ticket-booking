package com.workshop.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;
import java.math.BigDecimal;

public sealed interface PaymentCommands permits
        PaymentCommands.RequestPayment,
        PaymentCommands.ConfirmPayment,
        PaymentCommands.FailPayment,
        PaymentCommands.RefundPayment {

    record RequestPayment(
            @TargetAggregateIdentifier String paymentId,
            String bookingId,
            String customerId,
            BigDecimal amount
    ) implements PaymentCommands {}

    record ConfirmPayment(
            @TargetAggregateIdentifier String paymentId
    ) implements PaymentCommands {}

    record FailPayment(
            @TargetAggregateIdentifier String paymentId,
            String reason
    ) implements PaymentCommands {}

    record RefundPayment(
            @TargetAggregateIdentifier String paymentId,
            String reason
    ) implements PaymentCommands {}
}

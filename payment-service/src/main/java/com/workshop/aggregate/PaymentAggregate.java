package com.workshop.aggregate;

import com.workshop.commands.PaymentCommands;
import com.workshop.config.ChaosConfig;
import com.workshop.events.PaymentEvents;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

@Aggregate
@NoArgsConstructor
@Slf4j
public class PaymentAggregate {

    @AggregateIdentifier
    private String paymentId;
    private String bookingId;
    private String customerId;
    private BigDecimal amount;
    private PaymentStatus status;

    private enum PaymentStatus { REQUESTED, CONFIRMED, FAILED, REFUNDED }

    @Autowired
    private transient ChaosConfig chaosConfig;

    @CommandHandler
    public PaymentAggregate(PaymentCommands.RequestPayment cmd) {
        if (cmd.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        AggregateLifecycle.apply(new PaymentEvents.PaymentRequested(
                cmd.paymentId(), cmd.bookingId(), cmd.customerId(), cmd.amount()
        ));
    }

    @CommandHandler
    public void handle(PaymentCommands.ConfirmPayment cmd) {
        if (status != PaymentStatus.REQUESTED) {
            throw new IllegalStateException("Payment cannot be confirmed in state: " + status);
        }
        if (chaosConfig != null && chaosConfig.shouldFail()) {
            log.warn("CHAOS: randomly failing payment {} (booking {})", paymentId, bookingId);
            AggregateLifecycle.apply(new PaymentEvents.PaymentFailed(paymentId, bookingId,
                    "Chaos: simulated payment failure"));
            return;
        }
        AggregateLifecycle.apply(new PaymentEvents.PaymentConfirmed(paymentId, bookingId));
    }

    @CommandHandler
    public void handle(PaymentCommands.FailPayment cmd) {
        if (status != PaymentStatus.REQUESTED) {
            throw new IllegalStateException("Payment cannot be failed in state: " + status);
        }
        AggregateLifecycle.apply(new PaymentEvents.PaymentFailed(paymentId, bookingId, cmd.reason()));
    }

    @CommandHandler
    public void handle(PaymentCommands.RefundPayment cmd) {
        if (status != PaymentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed payments can be refunded");
        }
        AggregateLifecycle.apply(new PaymentEvents.PaymentRefunded(paymentId, bookingId, customerId, cmd.reason()));
    }

    @EventSourcingHandler
    public void on(PaymentEvents.PaymentRequested event) {
        this.paymentId = event.paymentId();
        this.bookingId = event.bookingId();
        this.customerId = event.customerId();
        this.amount = event.amount();
        this.status = PaymentStatus.REQUESTED;
        log.info("Payment requested: {}", paymentId);
    }

    @EventSourcingHandler
    public void on(PaymentEvents.PaymentConfirmed event) {
        this.status = PaymentStatus.CONFIRMED;
        log.info("Payment confirmed: {}", paymentId);
    }

    @EventSourcingHandler
    public void on(PaymentEvents.PaymentFailed event) {
        this.status = PaymentStatus.FAILED;
        log.warn("Payment failed: {} reason={}", paymentId, event.reason());
    }

    @EventSourcingHandler
    public void on(PaymentEvents.PaymentRefunded event) {
        this.status = PaymentStatus.REFUNDED;
        log.info("Payment refunded: {}", paymentId);
    }
}

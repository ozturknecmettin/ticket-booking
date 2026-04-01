package com.workshop.query;

import com.workshop.events.PaymentEvents;
import com.workshop.projection.PaymentProjection;
import com.workshop.projection.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@ProcessingGroup("payment-projections")
public class PaymentQueryHandler {

    private final PaymentRepository paymentRepository;
    private final Counter paymentsConfirmed;
    private final Counter paymentsFailed;

    public PaymentQueryHandler(PaymentRepository paymentRepository, MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.paymentsConfirmed = Counter.builder("payments.confirmed")
                .description("Total payments confirmed").register(meterRegistry);
        this.paymentsFailed = Counter.builder("payments.failed")
                .description("Total payments failed").register(meterRegistry);
    }

    @EventHandler
    public void on(PaymentEvents.PaymentRequested event) {
        paymentRepository.save(PaymentProjection.builder()
                .paymentId(event.paymentId())
                .bookingId(event.bookingId())
                .customerId(event.customerId())
                .amount(event.amount())
                .status("REQUESTED")
                .build());
        log.info("Read model created for payment {}", event.paymentId());
    }

    @EventHandler
    public void on(PaymentEvents.PaymentConfirmed event) {
        paymentRepository.findById(event.paymentId()).ifPresent(p -> {
            p.setStatus("CONFIRMED");
            paymentRepository.save(p);
        });
        paymentsConfirmed.increment();
    }

    @EventHandler
    public void on(PaymentEvents.PaymentFailed event) {
        paymentRepository.findById(event.paymentId()).ifPresent(p -> {
            p.setStatus("FAILED");
            paymentRepository.save(p);
        });
        paymentsFailed.increment();
    }

    @EventHandler
    public void on(PaymentEvents.PaymentRefunded event) {
        paymentRepository.findById(event.paymentId()).ifPresent(p -> {
            p.setStatus("REFUNDED");
            paymentRepository.save(p);
        });
    }

    @QueryHandler
    public Optional<PaymentProjection> handle(PaymentQueries.GetPayment query) {
        return paymentRepository.findById(query.paymentId());
    }

    @QueryHandler
    public Optional<PaymentProjection> handle(PaymentQueries.GetPaymentByBooking query) {
        return paymentRepository.findByBookingId(query.bookingId());
    }

    @QueryHandler
    public List<PaymentProjection> handle(PaymentQueries.GetPaymentsByCustomer query) {
        return paymentRepository.findByCustomerId(query.customerId());
    }
}

package com.workshop;

import com.workshop.aggregate.PaymentAggregate;
import com.workshop.commands.PaymentCommands;
import com.workshop.events.PaymentEvents;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class PaymentAggregateTest {

    private AggregateTestFixture<PaymentAggregate> fixture;
    private static final String PAYMENT_ID = "payment-001";
    private static final String BOOKING_ID = "booking-001";
    private static final BigDecimal AMOUNT = new BigDecimal("49.99");

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(PaymentAggregate.class);
    }

    @Test
    @DisplayName("RequestPayment should emit PaymentRequested")
    void requestPayment() {
        fixture.givenNoPriorActivity()
                .when(new PaymentCommands.RequestPayment(PAYMENT_ID, BOOKING_ID, "customer-1", AMOUNT))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new PaymentEvents.PaymentRequested(PAYMENT_ID, BOOKING_ID, "customer-1", AMOUNT));
    }

    @Test
    @DisplayName("ConfirmPayment should emit PaymentConfirmed when REQUESTED")
    void confirmPayment() {
        fixture.given(new PaymentEvents.PaymentRequested(PAYMENT_ID, BOOKING_ID, "customer-1", AMOUNT))
                .when(new PaymentCommands.ConfirmPayment(PAYMENT_ID))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new PaymentEvents.PaymentConfirmed(PAYMENT_ID, BOOKING_ID));
    }

    @Test
    @DisplayName("FailPayment should emit PaymentFailed when REQUESTED")
    void failPayment() {
        fixture.given(new PaymentEvents.PaymentRequested(PAYMENT_ID, BOOKING_ID, "customer-1", AMOUNT))
                .when(new PaymentCommands.FailPayment(PAYMENT_ID, "Insufficient funds"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new PaymentEvents.PaymentFailed(PAYMENT_ID, BOOKING_ID, "Insufficient funds"));
    }

    @Test
    @DisplayName("RefundPayment should emit PaymentRefunded when CONFIRMED")
    void refundPayment() {
        fixture.given(
                        new PaymentEvents.PaymentRequested(PAYMENT_ID, BOOKING_ID, "customer-1", AMOUNT),
                        new PaymentEvents.PaymentConfirmed(PAYMENT_ID, BOOKING_ID)
                )
                .when(new PaymentCommands.RefundPayment(PAYMENT_ID, "Show cancelled"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new PaymentEvents.PaymentRefunded(PAYMENT_ID, BOOKING_ID, "customer-1", "Show cancelled"));
    }

    @Test
    @DisplayName("RequestPayment with zero amount should fail")
    void requestPaymentWithZeroAmount() {
        fixture.givenNoPriorActivity()
                .when(new PaymentCommands.RequestPayment(PAYMENT_ID, BOOKING_ID, "customer-1", BigDecimal.ZERO))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ConfirmPayment on already confirmed payment should fail")
    void confirmAlreadyConfirmed() {
        fixture.given(
                        new PaymentEvents.PaymentRequested(PAYMENT_ID, BOOKING_ID, "customer-1", AMOUNT),
                        new PaymentEvents.PaymentConfirmed(PAYMENT_ID, BOOKING_ID)
                )
                .when(new PaymentCommands.ConfirmPayment(PAYMENT_ID))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("RefundPayment on failed payment should fail")
    void refundFailedPayment() {
        fixture.given(
                        new PaymentEvents.PaymentRequested(PAYMENT_ID, BOOKING_ID, "customer-1", AMOUNT),
                        new PaymentEvents.PaymentFailed(PAYMENT_ID, BOOKING_ID, "Insufficient funds")
                )
                .when(new PaymentCommands.RefundPayment(PAYMENT_ID, "Customer request"))
                .expectException(IllegalStateException.class);
    }
}

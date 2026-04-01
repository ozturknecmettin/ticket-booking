package com.workshop.commands;

import com.workshop.dto.SeatItem;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;
import java.util.List;

public sealed interface BookingCommands permits
        BookingCommands.InitiateBooking,
        BookingCommands.ConfirmBooking,
        BookingCommands.CancelBooking,
        BookingCommands.IssueTicket,
        BookingCommands.RefundBooking,
        BookingCommands.AmendBooking {

    record InitiateBooking(
            @TargetAggregateIdentifier String bookingId,
            String showId,
            List<SeatItem> seats,
            String customerId,
            BigDecimal totalAmount
    ) implements BookingCommands {}

    record ConfirmBooking(
            @TargetAggregateIdentifier String bookingId
    ) implements BookingCommands {}

    record CancelBooking(
            @TargetAggregateIdentifier String bookingId,
            String reason
    ) implements BookingCommands {}

    record IssueTicket(
            @TargetAggregateIdentifier String bookingId,
            String ticketNumber
    ) implements BookingCommands {}

    record RefundBooking(
            @TargetAggregateIdentifier String bookingId,
            String reason
    ) implements BookingCommands {}

    /** Swap seats while the booking is still in INITIATED state (payment not yet confirmed). */
    record AmendBooking(
            @TargetAggregateIdentifier String bookingId,
            List<SeatItem> newSeats
    ) implements BookingCommands {}
}

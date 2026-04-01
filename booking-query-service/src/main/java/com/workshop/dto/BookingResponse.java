package com.workshop.dto;

import java.math.BigDecimal;
import java.util.List;

public record BookingResponse(
        String bookingId,
        String showId,
        List<SeatItem> seats,
        String customerId,
        BigDecimal totalAmount,
        String status,
        String ticketNumber
) {}

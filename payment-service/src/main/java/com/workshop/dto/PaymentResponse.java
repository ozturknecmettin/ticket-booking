package com.workshop.dto;

import java.math.BigDecimal;

public record PaymentResponse(
        String paymentId,
        String bookingId,
        String customerId,
        BigDecimal amount,
        String status
) {}

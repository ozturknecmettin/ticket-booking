package com.workshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProcessPaymentRequest(
        String paymentId,
        @NotBlank String bookingId,
        @NotBlank String customerId,
        @NotNull @Positive BigDecimal amount
) {}

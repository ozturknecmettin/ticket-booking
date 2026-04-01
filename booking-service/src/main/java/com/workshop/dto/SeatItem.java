package com.workshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SeatItem(
        @NotBlank String seatNumber,
        @NotNull @Positive BigDecimal unitPrice
) {}

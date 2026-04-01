package com.workshop.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateShowRequest(
        @NotBlank String title,
        @NotBlank String venue,
        @Positive int totalSeats,
        @Positive BigDecimal ticketPrice,
        @NotNull @Future LocalDateTime eventDate,
        String category,
        String artistName,
        String description,
        Integer durationMinutes,
        List<PriceZone> priceZones
) {}

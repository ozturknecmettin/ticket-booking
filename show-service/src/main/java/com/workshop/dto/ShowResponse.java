package com.workshop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ShowResponse(
        String showId,
        String title,
        String venue,
        int totalSeats,
        int availableSeats,
        BigDecimal ticketPrice,
        String status,
        Set<String> reservedSeats,
        LocalDateTime eventDate,
        String category,
        String artistName,
        String description,
        Integer durationMinutes,
        List<PriceZone> priceZones
) {}

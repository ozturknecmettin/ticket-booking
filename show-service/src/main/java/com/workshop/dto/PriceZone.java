package com.workshop.dto;

import java.math.BigDecimal;

public record PriceZone(
        String label,
        String fromRow,
        String toRow,
        BigDecimal price
) {}

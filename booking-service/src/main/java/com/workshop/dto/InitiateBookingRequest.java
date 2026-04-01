package com.workshop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InitiateBookingRequest(
        @NotBlank String showId,
        @NotEmpty @Size(min = 1, max = 5) List<@Valid SeatItem> seats,
        @NotBlank String customerId
) {}

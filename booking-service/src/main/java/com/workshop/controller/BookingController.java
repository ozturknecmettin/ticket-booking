package com.workshop.controller;

import com.workshop.commands.BookingCommands;
import com.workshop.dto.InitiateBookingRequest;
import com.workshop.dto.SeatItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Booking command API (CQRS write side)")
public class BookingController {

    private final CommandGateway commandGateway;

    @Value("${booking.max-seats-per-booking:5}")
    private int maxSeatsPerBooking;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Initiate a booking (triggers saga)")
    public CompletableFuture<String> initiateBooking(@Valid @RequestBody InitiateBookingRequest request) {
        if (request.seats().size() > maxSeatsPerBooking) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot book more than " + maxSeatsPerBooking + " seats per booking");
        }
        BigDecimal totalAmount = request.seats().stream()
                .map(s -> s.unitPrice() != null ? s.unitPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String bookingId = UUID.randomUUID().toString();
        return commandGateway.send(new BookingCommands.InitiateBooking(
                bookingId, request.showId(), request.seats(),
                request.customerId(), totalAmount
        ));
    }

    @PostMapping("/{bookingId}/amend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Amend a booking's seats (only while INITIATED)")
    public CompletableFuture<Void> amendBooking(
            @PathVariable String bookingId,
            @RequestBody List<SeatItem> newSeats) {
        return commandGateway.send(new BookingCommands.AmendBooking(bookingId, newSeats));
    }

    @DeleteMapping("/{bookingId}")
    @Operation(summary = "Cancel a booking")
    public CompletableFuture<Void> cancelBooking(
            @PathVariable String bookingId,
            @RequestParam(defaultValue = "Cancelled by customer") String reason) {
        return commandGateway.send(new BookingCommands.CancelBooking(bookingId, reason));
    }

    @GetMapping("/health")
    @Operation(summary = "Command-side liveness check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\"}");
    }
}

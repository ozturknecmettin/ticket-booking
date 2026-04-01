package com.workshop.controller;

import com.workshop.commands.ShowCommands;
import com.workshop.dto.CreateShowRequest;
import com.workshop.dto.PageResult;
import com.workshop.dto.PriceZone;
import com.workshop.dto.ShowResponse;
import com.workshop.projection.ShowProjection;
import com.workshop.projection.ShowRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
@Tag(name = "Shows", description = "Show management API")
public class ShowController {

    private final CommandGateway commandGateway;
    private final ShowRepository showRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new show")
    public ResponseEntity<?> createShow(@Valid @RequestBody CreateShowRequest request) {
        List<PriceZone> zones = request.priceZones() != null && !request.priceZones().isEmpty()
                ? request.priceZones() : null;
        BigDecimal ticketPrice = request.ticketPrice();
        if (ticketPrice == null && zones == null) {
            return ResponseEntity.badRequest().body("{\"error\":\"Either ticketPrice or priceZones must be provided\"}");
        }
        if (ticketPrice == null) {
            ticketPrice = zones.stream().map(PriceZone::price).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        }
        String showId = UUID.randomUUID().toString();
        CompletableFuture<String> future = commandGateway.send(new ShowCommands.CreateShow(
                showId, request.title(), request.venue(),
                request.totalSeats(), ticketPrice, request.eventDate(),
                request.category(), request.artistName(), request.description(),
                request.durationMinutes(), zones
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(future.join());
    }

    @GetMapping("/{showId}")
    @Operation(summary = "Get a show by ID")
    public ResponseEntity<ShowResponse> getShow(@PathVariable String showId) {
        return showRepository.findById(showId)
                .map(p -> ResponseEntity.ok(toResponse(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "List all shows (paginated)")
    public PageResult<ShowResponse> getAllShows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(showRepository.findAll(PageRequest.of(page, size)).map(this::toResponse));
    }

    @GetMapping("/active")
    @Operation(summary = "List active shows (paginated)")
    public PageResult<ShowResponse> getActiveShows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(showRepository.findByStatus("ACTIVE", PageRequest.of(page, size)).map(this::toResponse));
    }

    @DeleteMapping("/{showId}")
    @Operation(summary = "Cancel a show")
    public CompletableFuture<Void> cancelShow(
            @PathVariable String showId,
            @RequestParam(defaultValue = "Cancelled by admin") String reason) {
        return commandGateway.send(new ShowCommands.CancelShow(showId, reason));
    }

    private ShowResponse toResponse(ShowProjection p) {
        return new ShowResponse(
                p.getShowId(), p.getTitle(), p.getVenue(),
                p.getTotalSeats(), p.getAvailableSeats(),
                p.getTicketPrice(), p.getStatus(),
                p.getReservedSeats(), p.getEventDate(),
                p.getCategory(), p.getArtistName(),
                p.getDescription(), p.getDurationMinutes(),
                p.getPriceZones()
        );
    }
}

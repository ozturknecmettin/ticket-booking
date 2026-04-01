package com.workshop.controller;

import com.workshop.dto.BookingResponse;
import com.workshop.dto.PageResult;
import com.workshop.projection.BookingProjection;
import com.workshop.projection.BookingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Queries", description = "Read-only booking query API (CQRS read side)")
public class BookingQueryController {

    private final BookingRepository bookingRepository;

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable String bookingId) {
        return bookingRepository.findById(bookingId)
                .map(p -> ResponseEntity.ok(toResponse(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all bookings (admin, paginated)")
    public PageResult<BookingResponse> getAllBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(bookingRepository.findAll(PageRequest.of(page, size)).map(this::toResponse));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get all bookings for a customer (paginated)")
    public PageResult<BookingResponse> getBookingsByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(bookingRepository.findByCustomerId(customerId, PageRequest.of(page, size)).map(this::toResponse));
    }

    @GetMapping("/show/{showId}")
    @Operation(summary = "Get all bookings for a show (paginated)")
    public PageResult<BookingResponse> getBookingsByShow(
            @PathVariable String showId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(bookingRepository.findByShowId(showId, PageRequest.of(page, size)).map(this::toResponse));
    }

    private BookingResponse toResponse(BookingProjection p) {
        return new BookingResponse(
                p.getBookingId(), p.getShowId(), p.getSeats(),
                p.getCustomerId(), p.getTotalAmount(), p.getStatus(), p.getTicketNumber()
        );
    }
}

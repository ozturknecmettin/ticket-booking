package com.workshop.controller;

import com.workshop.dto.NotificationResponse;
import com.workshop.projection.NotificationProjection;
import com.workshop.projection.NotificationRepository;
import com.workshop.sse.NotificationSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification history API")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationSseService sseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream — push notifications to connected browsers")
    public SseEmitter stream(@RequestParam(defaultValue = "all") String customerId) {
        return sseService.addEmitter(customerId);
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get all notifications for a customer")
    public List<NotificationResponse> getByCustomer(@PathVariable String customerId) {
        return notificationRepository.findByCustomerIdOrderBySentAtDesc(customerId)
                .stream().map(this::toResponse).toList();
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get all notifications for a booking")
    public List<NotificationResponse> getByBooking(@PathVariable String bookingId) {
        return notificationRepository.findByBookingId(bookingId)
                .stream().map(this::toResponse).toList();
    }

    @GetMapping("/recent")
    @Operation(summary = "Get the 50 most recent notifications")
    public List<NotificationResponse> getRecent() {
        return notificationRepository.findTop50ByOrderBySentAtDesc()
                .stream().map(this::toResponse).toList();
    }

    private NotificationResponse toResponse(NotificationProjection p) {
        return new NotificationResponse(
                p.getNotificationId(), p.getBookingId(), p.getCustomerId(),
                p.getNotificationType(), p.getMessage(), p.getSentAt()
        );
    }
}

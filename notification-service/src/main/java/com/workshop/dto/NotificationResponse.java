package com.workshop.dto;

import java.time.Instant;

public record NotificationResponse(
        String notificationId,
        String bookingId,
        String customerId,
        String notificationType,
        String message,
        Instant sentAt
) {}

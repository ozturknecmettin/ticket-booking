package com.workshop.projection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationProjection, String> {
    List<NotificationProjection> findByCustomerIdOrderBySentAtDesc(String customerId);
    List<NotificationProjection> findByBookingId(String bookingId);
    List<NotificationProjection> findTop50ByOrderBySentAtDesc();
}

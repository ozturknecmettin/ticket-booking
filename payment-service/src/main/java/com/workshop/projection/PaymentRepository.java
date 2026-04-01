package com.workshop.projection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentProjection, String> {
    Optional<PaymentProjection> findByBookingId(String bookingId);
    List<PaymentProjection> findByCustomerId(String customerId);
    List<PaymentProjection> findByStatus(String status);
}

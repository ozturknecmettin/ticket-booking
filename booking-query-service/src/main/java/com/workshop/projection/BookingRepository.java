package com.workshop.projection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<BookingProjection, String> {
    List<BookingProjection> findByCustomerId(String customerId);
    Page<BookingProjection> findByCustomerId(String customerId, Pageable pageable);
    List<BookingProjection> findByShowId(String showId);
    Page<BookingProjection> findByShowId(String showId, Pageable pageable);
    List<BookingProjection> findByStatus(String status);
    List<BookingProjection> findByShowIdAndStatusNotIn(String showId, java.util.Collection<String> statuses);
}

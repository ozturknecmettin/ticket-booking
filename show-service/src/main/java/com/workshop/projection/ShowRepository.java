package com.workshop.projection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowRepository extends JpaRepository<ShowProjection, String> {
    List<ShowProjection> findByStatus(String status);
    Page<ShowProjection> findByStatus(String status, Pageable pageable);
}

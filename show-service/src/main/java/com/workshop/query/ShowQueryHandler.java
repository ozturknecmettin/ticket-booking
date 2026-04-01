package com.workshop.query;

import com.workshop.events.ShowEvents;
import com.workshop.projection.ShowProjection;
import com.workshop.projection.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@ProcessingGroup("show-projections")
public class ShowQueryHandler {

    private final ShowRepository showRepository;

    @EventHandler
    public void on(ShowEvents.ShowCreated event) {
        ShowProjection projection = ShowProjection.builder()
                .showId(event.showId())
                .title(event.title())
                .venue(event.venue())
                .totalSeats(event.totalSeats())
                .availableSeats(event.totalSeats())
                .ticketPrice(event.ticketPrice())
                .eventDate(event.eventDate())
                .category(event.category())
                .artistName(event.artistName())
                .priceZones(event.priceZones())
                .description(event.description())
                .durationMinutes(event.durationMinutes())
                .status("ACTIVE")
                .build();
        showRepository.save(projection);
        log.info("Read model created for show {}", event.showId());
    }

    @EventHandler
    public void on(ShowEvents.SeatReserved event) {
        showRepository.findById(event.showId()).ifPresent(p -> {
            p.setAvailableSeats(p.getAvailableSeats() - 1);
            p.getReservedSeats().add(event.seatNumber());
            showRepository.save(p);
        });
    }

    @EventHandler
    public void on(ShowEvents.SeatReleased event) {
        showRepository.findById(event.showId()).ifPresent(p -> {
            p.setAvailableSeats(p.getAvailableSeats() + 1);
            p.getReservedSeats().remove(event.seatNumber());
            showRepository.save(p);
        });
    }

    @EventHandler
    public void on(ShowEvents.ShowCancelled event) {
        showRepository.findById(event.showId()).ifPresent(p -> {
            p.setStatus("CANCELLED");
            showRepository.save(p);
        });
    }

    @QueryHandler
    public Optional<ShowProjection> handle(ShowQueries.GetShow query) {
        return showRepository.findById(query.showId());
    }

    @QueryHandler
    public List<ShowProjection> handle(ShowQueries.GetAllShows query) {
        return showRepository.findAll();
    }

    @QueryHandler
    public List<ShowProjection> handle(ShowQueries.GetActiveShows query) {
        return showRepository.findByStatus("ACTIVE");
    }
}

package com.workshop.projection;

import com.workshop.dto.PriceZone;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "shows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowProjection {

    @Id
    @Column(name = "show_id")
    private String showId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String venue;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(name = "ticket_price", precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    @Column(nullable = false)
    private String status;

    @Column(name = "event_date")
    private LocalDateTime eventDate;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "artist_name")
    private String artistName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "price_zones", columnDefinition = "TEXT")
    @Convert(converter = PriceZoneConverter.class)
    private List<PriceZone> priceZones;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "show_reserved_seats", joinColumns = @JoinColumn(name = "show_id"))
    @Column(name = "seat_number")
    @Builder.Default
    private Set<String> reservedSeats = new HashSet<>();

    @Version
    private Long version;
}

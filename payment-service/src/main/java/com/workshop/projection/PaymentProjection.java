package com.workshop.projection;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProjection {

    @Id
    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status; // REQUESTED, CONFIRMED, FAILED, REFUNDED

    @Version
    private Long version;
}

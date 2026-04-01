package com.workshop.controller;

import com.workshop.commands.PaymentCommands;
import com.workshop.dto.PaymentResponse;
import com.workshop.dto.ProcessPaymentRequest;
import com.workshop.projection.PaymentProjection;
import com.workshop.projection.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing API")
public class PaymentController {

    private final CommandGateway commandGateway;
    private final PaymentRepository paymentRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Process a payment request")
    public CompletableFuture<String> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        String paymentId = request.paymentId() != null ? request.paymentId() : UUID.randomUUID().toString();
        return commandGateway.<Object>send(new PaymentCommands.RequestPayment(
                paymentId, request.bookingId(), request.customerId(), request.amount()
        )).thenApply(ignored -> paymentId);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
        return paymentRepository.findById(paymentId)
                .map(p -> ResponseEntity.ok(toResponse(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get payment by booking ID")
    public ResponseEntity<PaymentResponse> getPaymentByBooking(@PathVariable String bookingId) {
        return paymentRepository.findByBookingId(bookingId)
                .map(p -> ResponseEntity.ok(toResponse(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{paymentId}/confirm")
    @Operation(summary = "Confirm a payment (admin/webhook)")
    public CompletableFuture<Void> confirmPayment(@PathVariable String paymentId) {
        return commandGateway.send(new PaymentCommands.ConfirmPayment(paymentId));
    }

    @PostMapping("/{paymentId}/fail")
    @Operation(summary = "Fail a payment (admin/webhook)")
    public CompletableFuture<Void> failPayment(
            @PathVariable String paymentId,
            @RequestParam(defaultValue = "Insufficient funds") String reason) {
        return commandGateway.send(new PaymentCommands.FailPayment(paymentId, reason));
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a confirmed payment")
    public CompletableFuture<Void> refundPayment(
            @PathVariable String paymentId,
            @RequestParam(defaultValue = "Customer request") String reason) {
        return commandGateway.send(new PaymentCommands.RefundPayment(paymentId, reason));
    }

    private PaymentResponse toResponse(PaymentProjection p) {
        return new PaymentResponse(p.getPaymentId(), p.getBookingId(),
                p.getCustomerId(), p.getAmount(), p.getStatus());
    }
}

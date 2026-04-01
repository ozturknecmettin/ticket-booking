package com.workshop.controller;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workshop.model.User;
import com.workshop.model.UserRepository;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final UserRepository userRepository;

    @Value("${services.show-url}")
    private String showUrl;

    @Value("${services.booking-url}")
    private String bookingUrl;

    @Value("${services.booking-query-url}")
    private String bookingQueryUrl;

    @Value("${services.payment-url}")
    private String paymentUrl;

    @Value("${services.notification-url}")
    private String notificationUrl;

    // ── Page ──────────────────────────────────────────────────────────────────

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/auth/me")
    @ResponseBody
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String role     = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        String fullName = userRepository.findByUsername(auth.getName())
                .map(User::getFullName).orElse(auth.getName());
        return ResponseEntity.ok(Map.of("username", auth.getName(), "fullName", fullName, "role", role));
    }

    // ── Health ─────────────────────────────────────────────────────────────────

    @GetMapping("/proxy/health")
    @ResponseBody
    public Map<String, String> health() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("show-service",          serviceStatus("show-service", showUrl));
        result.put("booking-service",       serviceStatus("booking-service", bookingUrl));
        result.put("booking-query-service", serviceStatus("booking-query-service", bookingQueryUrl));
        result.put("payment-service",       serviceStatus("payment-service", paymentUrl));
        result.put("notification-service",  serviceStatus("notification-service", notificationUrl));
        return result;
    }

    // ── DLQ depth ─────────────────────────────────────────────────────────────

    @GetMapping("/proxy/dlq")
    @ResponseBody
    public Map<String, Object> dlqDepth() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : Map.of(
                "show-service",          showUrl,
                "booking-query-service", bookingQueryUrl,
                "payment-service",       paymentUrl,
                "notification-service",  notificationUrl
        ).entrySet()) {
            try {
                String body = restTemplate.getForObject(e.getValue() + "/api/admin/dlq-depth", String.class);
                result.put(e.getKey(), objectMapper.readTree(body).get("sequences").asLong());
            } catch (Exception ex) {
                result.put(e.getKey(), -1L);
            }
        }
        return result;
    }

    // ── Shows proxy ────────────────────────────────────────────────────────────

    @GetMapping("/proxy/shows")
    @ResponseBody
    public ResponseEntity<String> getShows() {
        return proxy("show-service", () -> restTemplate.getForEntity(showUrl + "/api/shows", String.class));
    }

    @PostMapping("/proxy/shows")
    @ResponseBody
    public ResponseEntity<String> createShow(@RequestBody String body) {
        return proxy("show-service", () -> restTemplate.postForEntity(showUrl + "/api/shows", jsonEntity(body), String.class));
    }

    @DeleteMapping("/proxy/shows/{showId}")
    @ResponseBody
    public ResponseEntity<String> cancelShow(@PathVariable String showId,
                                             @RequestParam(defaultValue = "Cancelled by admin") String reason) {
        return proxy("show-service", () -> {
            restTemplate.delete(showUrl + "/api/shows/" + showId + "?reason=" + reason);
            return ResponseEntity.ok("\"cancelled\"");
        });
    }

    // ── Bookings proxy ─────────────────────────────────────────────────────────

    @PostMapping("/proxy/bookings")
    @ResponseBody
    public ResponseEntity<String> createBooking(@RequestBody String body, Authentication auth) {
        return proxy("booking-service", () -> {
            // For CUSTOMER role, force customerId to the logged-in username
            String finalBody = body;
            if (isCustomer(auth)) {
                try {
                    ObjectNode node = (ObjectNode) objectMapper.readTree(body);
                    node.put("customerId", auth.getName());
                    finalBody = objectMapper.writeValueAsString(node);
                } catch (Exception e) {
                    log.warn("Could not override customerId in booking request", e);
                }
            }
            return restTemplate.postForEntity(bookingUrl + "/api/bookings", jsonEntity(finalBody), String.class);
        });
    }

    @GetMapping("/proxy/bookings")
    @ResponseBody
    public ResponseEntity<String> getAllBookings() {
        return proxy("booking-query-service", () -> restTemplate.getForEntity(bookingQueryUrl + "/api/bookings", String.class));
    }

    @GetMapping("/proxy/bookings/{bookingId}")
    @ResponseBody
    public ResponseEntity<String> getBooking(@PathVariable String bookingId) {
        return proxy("booking-query-service", () -> restTemplate.getForEntity(bookingQueryUrl + "/api/bookings/" + bookingId, String.class));
    }

    @GetMapping("/proxy/bookings/show/{showId}")
    @ResponseBody
    public ResponseEntity<String> getBookingsByShow(@PathVariable String showId) {
        return proxy("booking-query-service", () -> restTemplate.getForEntity(bookingQueryUrl + "/api/bookings/show/" + showId, String.class));
    }

    @GetMapping("/proxy/bookings/customer/{customerId}")
    @ResponseBody
    public ResponseEntity<String> getBookingsByCustomer(@PathVariable String customerId, Authentication auth) {
        // CUSTOMER can only fetch their own bookings
        if (isCustomer(auth) && !auth.getName().equals(customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"Access denied\"}");
        }
        return proxy("booking-query-service", () -> restTemplate.getForEntity(bookingQueryUrl + "/api/bookings/customer/" + customerId, String.class));
    }

    @DeleteMapping("/proxy/bookings/{bookingId}")
    @ResponseBody
    public ResponseEntity<String> cancelBooking(@PathVariable String bookingId,
                                                @RequestParam(defaultValue = "Cancelled by customer") String reason) {
        return proxy("booking-service", () -> {
            restTemplate.delete(bookingUrl + "/api/bookings/" + bookingId + "?reason=" + reason);
            return ResponseEntity.ok("\"cancelled\"");
        });
    }

    // ── Payments proxy ─────────────────────────────────────────────────────────

    @GetMapping("/proxy/payments/booking/{bookingId}")
    @ResponseBody
    public ResponseEntity<String> getPaymentByBooking(@PathVariable String bookingId) {
        return proxy("payment-service", () -> restTemplate.getForEntity(paymentUrl + "/api/payments/booking/" + bookingId, String.class));
    }

    @PostMapping("/proxy/payments/{paymentId}/confirm")
    @ResponseBody
    public ResponseEntity<String> confirmPayment(@PathVariable String paymentId) {
        return proxy("payment-service", () -> restTemplate.postForEntity(
                paymentUrl + "/api/payments/" + paymentId + "/confirm", jsonEntity("{}"), String.class));
    }

    @PostMapping("/proxy/payments/{paymentId}/fail")
    @ResponseBody
    public ResponseEntity<String> failPayment(@PathVariable String paymentId,
                                              @RequestParam(defaultValue = "Insufficient funds") String reason) {
        return proxy("payment-service", () -> restTemplate.postForEntity(
                paymentUrl + "/api/payments/" + paymentId + "/fail?reason=" + reason, jsonEntity("{}"), String.class));
    }

    @PostMapping("/proxy/payments/{paymentId}/refund")
    @ResponseBody
    public ResponseEntity<String> refundPayment(@PathVariable String paymentId,
                                                @RequestParam(defaultValue = "Refunded by customer") String reason,
                                                Authentication auth) {
        // CUSTOMER must own the booking linked to this payment
        if (isCustomer(auth)) {
            try {
                String paymentJson = restTemplate.getForObject(
                        paymentUrl + "/api/payments/" + paymentId, String.class);
                String bookingId = objectMapper.readTree(paymentJson).get("bookingId").asText();
                String bookingJson = restTemplate.getForObject(
                        bookingQueryUrl + "/api/bookings/" + bookingId, String.class);
                String customerId = objectMapper.readTree(bookingJson).get("customerId").asText();
                if (!auth.getName().equals(customerId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("{\"error\":\"Access denied\"}");
                }
            } catch (Exception e) {
                log.warn("Ownership check failed for refund of payment {}", paymentId, e);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("{\"error\":\"Access denied\"}");
            }
        }
        return proxy("payment-service", () -> restTemplate.postForEntity(
                paymentUrl + "/api/payments/" + paymentId + "/refund?reason=" + reason, jsonEntity("{}"), String.class));
    }

    // ── Booking amendment proxy ────────────────────────────────────────────────

    @PostMapping("/proxy/bookings/{bookingId}/amend")
    @ResponseBody
    public ResponseEntity<String> amendBooking(@PathVariable String bookingId,
                                               @RequestBody String body,
                                               Authentication auth) {
        return proxy("booking-service", () -> restTemplate.postForEntity(
                bookingUrl + "/api/bookings/" + bookingId + "/amend", jsonEntity(body), String.class));
    }

    // ── Chaos mode proxy ───────────────────────────────────────────────────────

    @GetMapping("/proxy/chaos")
    @ResponseBody
    public ResponseEntity<String> getChaosStatus() {
        return proxy("payment-service", () -> restTemplate.getForEntity(
                paymentUrl + "/api/admin/chaos", String.class));
    }

    @PostMapping("/proxy/chaos")
    @ResponseBody
    public ResponseEntity<String> setChaos(@RequestParam boolean enabled) {
        return proxy("payment-service", () -> restTemplate.postForEntity(
                paymentUrl + "/api/admin/chaos?enabled=" + enabled, jsonEntity("{}"), String.class));
    }

    // ── Notifications proxy ────────────────────────────────────────────────────

    /**
     * SSE proxy — streams text/event-stream from notification-service to the browser.
     * Uses raw HttpURLConnection with readTimeout=0 so the connection stays open indefinitely.
     * This endpoint is public (no JWT) because the EventSource JS API cannot send headers.
     */
    @GetMapping(value = "/proxy/notifications/stream", produces = "text/event-stream")
    public void streamNotifications(@RequestParam(defaultValue = "all") String customerId,
                                    HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        URL url = new URL(notificationUrl + "/api/notifications/stream?customerId=" + customerId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(0); // no read timeout — this is a long-lived stream
        conn.connect();

        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                response.getOutputStream().write(buf, 0, n);
                response.getOutputStream().flush();
            }
        } finally {
            conn.disconnect();
        }
    }

    @GetMapping("/proxy/notifications/booking/{bookingId}")
    @ResponseBody
    public ResponseEntity<String> getNotificationsByBooking(@PathVariable String bookingId) {
        return proxy("notification-service", () -> restTemplate.getForEntity(
                notificationUrl + "/api/notifications/booking/" + bookingId, String.class));
    }

    @GetMapping("/proxy/notifications/recent")
    @ResponseBody
    public ResponseEntity<String> getRecentNotifications() {
        return proxy("notification-service", () -> restTemplate.getForEntity(
                notificationUrl + "/api/notifications/recent", String.class));
    }

    // ── Event store inspector ──────────────────────────────────────────────────

    @GetMapping("/proxy/events/booking/{aggregateId}")
    @ResponseBody
    public ResponseEntity<String> getBookingEvents(@PathVariable String aggregateId) {
        return proxy("booking-service", () -> restTemplate.getForEntity(
                bookingUrl + "/api/admin/events/" + aggregateId, String.class));
    }

    @GetMapping("/proxy/events/show/{aggregateId}")
    @ResponseBody
    public ResponseEntity<String> getShowEvents(@PathVariable String aggregateId) {
        return proxy("show-service", () -> restTemplate.getForEntity(
                showUrl + "/api/admin/events/" + aggregateId, String.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isCustomer(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
    }

    private String serviceStatus(String service, String url) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
                circuitBreakerRegistry.circuitBreaker(service);
        return switch (cb.getState()) {
            case OPEN      -> "CB_OPEN";
            case HALF_OPEN -> "CB_HALF";
            default        -> checkHealth(url);
        };
    }

    private String checkHealth(String url) {
        try {
            ResponseEntity<String> r = restTemplate.getForEntity(url + "/actuator/health", String.class);
            return r.getStatusCode().is2xxSuccessful() ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> proxy(String service, Supplier<ResponseEntity<String>> call) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
                circuitBreakerRegistry.circuitBreaker(service);
        try {
            return io.github.resilience4j.circuitbreaker.CircuitBreaker
                    .decorateSupplier(cb, call::get).get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for {}", service);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"" + service + " is temporarily unavailable — circuit open\"}");
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"Service unavailable\"}");
        } catch (Exception e) {
            log.error("Proxy error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}

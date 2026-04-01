package com.workshop;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pact consumer test: booking-service (consumer) → payment-service (provider).
 *
 * Verifies the contract for the POST /api/payments endpoint that
 * booking-service would call if using REST (in this CQRS architecture the
 * actual interaction is via Axon commands, but the Pact test documents and
 * verifies the HTTP contract for REST-based clients).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service", port = "8090", pactVersion = PactSpecVersion.V3)
class BookingPaymentPactConsumerTest {

    @Pact(consumer = "booking-service", provider = "payment-service")
    public RequestResponsePact createPaymentPact(PactDslWithProvider builder) {
        return builder
                .given("payment service is available")
                .uponReceiving("a request to process payment")
                    .method(HttpMethod.POST.name())
                    .path("/api/payments")
                    .headers(Map.of("Content-Type", "application/json"))
                    .body("""
                        {
                          "paymentId": "payment-001",
                          "bookingId": "booking-001",
                          "customerId": "customer-001",
                          "amount": 49.99
                        }
                        """)
                .willRespondWith()
                    .status(HttpStatus.ACCEPTED.value())
                .toPact();
    }

    @Pact(consumer = "booking-service", provider = "payment-service")
    public RequestResponsePact getPaymentPact(PactDslWithProvider builder) {
        return builder
                .given("payment payment-001 exists")
                .uponReceiving("a request to get payment status")
                    .method(HttpMethod.GET.name())
                    .path("/api/payments/payment-001")
                .willRespondWith()
                    .status(HttpStatus.OK.value())
                    .headers(Map.of("Content-Type", "application/json"))
                    .body("""
                        {
                          "paymentId": "payment-001",
                          "bookingId": "booking-001",
                          "amount": 49.99,
                          "status": "CONFIRMED"
                        }
                        """)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createPaymentPact")
    void testCreatePayment(MockServer mockServer) {
        RestTemplate restTemplate = new RestTemplate();
        var response = restTemplate.postForEntity(
                mockServer.getUrl() + "/api/payments",
                Map.of(
                        "paymentId", "payment-001",
                        "bookingId", "booking-001",
                        "customerId", "customer-001",
                        "amount", 49.99
                ),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @PactTestFor(pactMethod = "getPaymentPact")
    void testGetPayment(MockServer mockServer) {
        RestTemplate restTemplate = new RestTemplate();
        var response = restTemplate.getForEntity(
                mockServer.getUrl() + "/api/payments/payment-001",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("CONFIRMED");
    }
}

# Ticket Booking Platform

An event-driven microservices platform for booking show tickets, built with Spring Boot, Axon Framework (CQRS + Event Sourcing), and Saga orchestration.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     UI Service :8080                    │
│            (JWT auth, rate limiting, Thymeleaf)         │
└────────────────────────┬────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
  │Show Service │ │  Booking    │ │   Payment   │
  │   :8081     │ │  Service    │ │   Service   │
  │(write+read) │ │   :8082     │ │   :8083     │
  └─────────────┘ └──────┬──────┘ └─────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
  │  Booking    │ │Notification │ │    Axon     │
  │Query Service│ │  Service    │ │   Server    │
  │   :8085     │ │   :8084     │ │  (events)   │
  └─────────────┘ └─────────────┘ └─────────────┘
```

### Booking Flow (Saga)

```
POST /api/bookings
      │
      ▼
BookingAggregate ──── BookingInitiated ────► BookingPaymentSaga
                                                    │
                                          ReserveSeat ──► ShowAggregate
                                                    │
                                        RequestPayment ──► PaymentAggregate
                                                    │
                                    POST /api/payments/{id}/confirm
                                                    │
                                          PaymentConfirmed ──► BookingAggregate
                                                    │
                                              BookingConfirmed ──► TicketIssued
```

## Services

| Service | Port | Role |
|---------|------|------|
| ui-service | 8080 | Demo UI, JWT auth, API gateway |
| show-service | 8081 | Show management, seat inventory (CQRS write+read) |
| booking-service | 8082 | Booking aggregate + Saga orchestration |
| payment-service | 8083 | Payment processing |
| notification-service | 8084 | Email notifications, SSE push |
| booking-query-service | 8085 | Booking read model (CQRS read side) |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| CQRS / Event Sourcing | Axon Framework 4.10.0 |
| Database (prod) | PostgreSQL 16 |
| Database (local) | H2 (in-memory) |
| Migrations | Flyway |
| Security | Spring Security + JWT |
| Resilience | Resilience4j (circuit breakers) |
| Rate Limiting | Bucket4j |
| Monitoring | Prometheus + Grafana + Zipkin |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Email (dev) | MailHog |
| Containerisation | Docker + Docker Compose |
| CI/CD | GitHub Actions → AWS ECS |

## Running Locally (Dev Mode)

### Prerequisites

- Java 21
- Maven 3.9+
- Axon Server (standalone, free edition)

### Start Axon Server

Download from [axoniq.io](https://developer.axoniq.io/axon-server/overview) and run:

```bash
java -jar axonserver.jar
```

Axon Server UI: http://localhost:8024

### Build All Services

```bash
mvn clean package -DskipTests
```

### Run Each Service

Open a terminal per service (or use your IDE):

```bash
# Show Service
java -jar show-service/target/show-service-*.jar --spring.profiles.active=local

# Booking Service
java -jar booking-service/target/booking-service-*.jar --spring.profiles.active=local

# Payment Service
java -jar payment-service/target/payment-service-*.jar --spring.profiles.active=local

# Notification Service
java -jar notification-service/target/notification-service-*.jar --spring.profiles.active=local

# Booking Query Service
java -jar booking-query-service/target/booking-query-service-*.jar --spring.profiles.active=local

# UI Service
java -jar ui-service/target/ui-service-*.jar
```

### Seed Demo Data

```bash
./scripts/seed-data.sh
```

Creates 3 shows and 4 bookings demonstrating different saga outcomes.

## Running with Docker Compose

```bash
docker compose up --build
```

Starts all services plus PostgreSQL, Axon Server, Zipkin, Prometheus, Grafana, and MailHog.

| Tool | URL |
|------|-----|
| Application UI | http://localhost:8080 |
| Axon Server | http://localhost:8024 |
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |
| Zipkin | http://localhost:9411 |
| MailHog | http://localhost:8025 |

## API Reference

Each service exposes Swagger UI:

| Service | Swagger UI |
|---------|-----------|
| Show Service | http://localhost:8081/swagger-ui.html |
| Booking Service | http://localhost:8082/swagger-ui.html |
| Payment Service | http://localhost:8083/swagger-ui.html |
| Notification Service | http://localhost:8084/swagger-ui.html |
| Booking Query Service | http://localhost:8085/swagger-ui.html |

### Key Endpoints

```
# Create a show
POST http://localhost:8081/api/shows

# Book seats
POST http://localhost:8082/api/bookings

# Confirm payment (triggers booking confirmation + ticket issuance)
POST http://localhost:8083/api/payments/{paymentId}/confirm

# Query bookings
GET  http://localhost:8085/api/bookings/{bookingId}
GET  http://localhost:8085/api/bookings/customer/{customerId}

# SSE notification stream
GET  http://localhost:8084/api/notifications/stream
```

### Example: Book a Ticket

```bash
# 1. Create a show
curl -X POST http://localhost:8081/api/shows \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Hamilton",
    "venue": "Broadway Theater",
    "totalSeats": 50,
    "priceZones": [{"zoneName": "Standard", "seatPrefix": "A", "unitPrice": 75.00}]
  }'

# 2. Initiate a booking
curl -X POST http://localhost:8082/api/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "showId": "show-001",
    "customerId": "customer-alice",
    "seats": [{"seatNumber": "A1", "unitPrice": 75.00}]
  }'

# 3. Confirm payment (use paymentId from booking-query-service)
curl -X POST http://localhost:8083/api/payments/{paymentId}/confirm
```

## Testing

```bash
# Run all tests (150 tests)
mvn verify

# Single service
mvn verify -pl show-service
```

Test coverage enforced at **80%** via JaCoCo. Includes unit tests, integration tests, and Pact contract tests.

## CI/CD

| Branch / Trigger | Pipeline |
|-----------------|----------|
| Pull Request → `develop` or `main` | Build + test + coverage check |
| Push to `develop` | Full test suite + deploy to dev (AWS ECS) |
| Push to `test` | Full test suite + deploy to test (AWS ECS) |
| Tag `v*.*.*` | Build + deploy to prod (AWS ECS, requires approval) |

## Project Structure

```
ticket-booking/
├── show-service/           # Show aggregate, seat inventory
├── booking-service/        # Booking aggregate, BookingPaymentSaga
├── booking-query-service/  # CQRS read model, ShowCancellationSaga
├── payment-service/        # Payment aggregate, chaos mode
├── notification-service/   # Email + SSE notifications
├── ui-service/             # Thymeleaf UI, JWT, rate limiting
├── docker-compose.yml
├── prometheus/
├── grafana/
├── ecs/                    # AWS ECS task definitions
└── scripts/
    ├── init-multiple-dbs.sh
    └── seed-data.sh
```

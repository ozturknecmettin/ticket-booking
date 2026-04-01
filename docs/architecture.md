# Ticket Booking System — Architecture

## Overview

CQRS/Event Sourcing mono-repo using Axon Framework 4.10 with five Spring Boot 3.2
microservices. Commands and events are routed via **Axon Server** (gRPC :8124 / HTTP :8024),
which also acts as the persistent event store and deadline manager.

```
┌──────────────┐     Commands/Events (Axon in-process bus)
│ show-service │◄────────────────────────────────────────────────────┐
│   :8081      │                                                     │
└──────┬───────┘                                                     │
       │ SeatReserved / SeatReleased events                         │
       ▼                                                             │
┌──────────────┐   BookingPaymentSaga   ┌───────────────┐           │
│booking-svc   │──────────────────────►│ payment-svc   │           │
│   :8082      │◄──────────────────────│   :8083       │           │
└──────┬───────┘  PaymentConfirmed /   └───────────────┘           │
       │          PaymentFailed                                      │
       │ Booking events (fan-out)                                   │
       ├──────────────────────────────────────────────────────────► │
       │                                                             │
       ▼                                                             │
┌──────────────────────┐                                             │
│ booking-query-svc    │  Read model for bookings (CQRS query side) │
│   :8085              │─────────────────────────────────────────────┘
└──────────────────────┘

┌──────────────────┐
│notification-svc  │  Listens to booking/payment events, sends notifications + SSE
│   :8084          │
└──────────────────┘
```

## Booking Lifecycle (Saga)

```
POST /api/bookings
    │
    ▼ InitiateBooking ──────► BookingInitiated
                                    │
                        [Saga] ReserveSeat ──► show-service
                                    │
                              SeatReserved
                                    │
                        [Saga] RequestPayment ──► payment-service
                                    │
                    ┌───────────────┴───────────────┐
              PaymentConfirmed               PaymentFailed
                    │                               │
            [Saga] ConfirmBooking           [Saga] CancelBooking
                    │                               │
           BookingConfirmed                  BookingCancelled
                    │                               │
            [Saga] IssueTicket           [Saga] ReleaseSeat ──► show-service
                    │
              TicketIssued ──► SagaLifecycle.end()
```

## Technology Choices

| Concern          | Choice                                     | Reason                                           |
|------------------|--------------------------------------------|--------------------------------------------------|
| Framework        | Axon Framework 4.10.0                      | First-class CQRS/ES, Saga support                 |
| Event transport  | Axon Server (gRPC :8124 / HTTP :8024)      | Persistent event store + distributed routing      |
| Read models      | JPA + H2 (dev) / PostgreSQL (prod)         | Simple query-side persistence                     |
| Schema migration | Flyway (read model + DLQ tables)           | Event store is Axon Server-managed                |
| Dead Letter Queue| JpaSequencedDeadLetterQueue per service    | Failed events parked; processor keeps running     |
| Retry policy     | `RetryingListenerErrorHandler` (3 retries, 500/1000/2000 ms backoff) | Transient failures retried before DLQ |
| Chaos mode       | `ChaosConfig` toggle in payment-service    | Admin-controlled random payment failure for testing |
| Input validation | Bean Validation (`jakarta.validation`)     | 400 returned before command dispatch              |
| Deadline manager | AxonServerDeadlineManager                  | Payment deadlines survive service restarts        |
| API docs         | SpringDoc OpenAPI 2.5 + Swagger UI         | Per-service at `/swagger-ui.html`                 |
| Testing          | AggregateTestFixture, SagaTestFixture, Pact| Full pyramid coverage                             |
| Contract testing | Pact 4.6                                   | booking-service consumer / payment-service provider|
| Coverage         | JaCoCo 80% minimum (INSTRUCTION)           | Enforced in Maven verify phase                    |

## AWS Infrastructure Recommendations

### VPC Layout

```
VPC 10.0.0.0/16
├── Public subnets  (eu-west-1a, eu-west-1b, eu-west-1c)  — ALB
└── Private subnets (eu-west-1a, eu-west-1b, eu-west-1c)  — ECS tasks + RDS
```

### Components

| Component             | Details                                                     |
|-----------------------|-------------------------------------------------------------|
| **ECS Fargate**       | Each service runs as an ECS service; no EC2 to manage       |
| **Application Load Balancer** | Single ALB with path-based routing per service      |
|                       | `/api/shows/*` → show-service target group                  |
|                       | `/api/bookings/*` → booking-service target group            |
|                       | `/api/payments/*` → payment-service target group            |
|                       | `/api/notifications/*` → notification-service target group  |
| **Amazon RDS Aurora PostgreSQL** | One Aurora cluster, four databases (showdb, bookingdb, paymentdb, notificationdb) |
| **AWS Secrets Manager** | DB credentials injected at container start                |
| **CloudWatch Logs**   | Log group `/ecs/ticket-booking/<service>` per service       |
| **ECR or DockerHub**  | Container image registry                                    |

### Fargate Sizing (starting point)

| Service              | CPU   | Memory |
|----------------------|-------|--------|
| show-service         | 512   | 1024 MB|
| booking-service      | 512   | 1024 MB|
| payment-service      | 512   | 1024 MB|
| notification-service | 256   | 512 MB |

Scale with ECS Application Auto Scaling based on CPU utilisation (target: 60%).

### ECS Service Auto Scaling

```hcl
# Example Terraform snippet
resource "aws_appautoscaling_policy" "show_service_scaling" {
  policy_type            = "TargetTrackingScaling"
  target_value           = 60.0
  predefined_metric_type = "ECSServiceAverageCPUUtilization"
  scale_in_cooldown      = 300
  scale_out_cooldown     = 60
}
```

### IAM Roles Required

| Role                      | Purpose                                          |
|---------------------------|--------------------------------------------------|
| `ecsTaskExecutionRole`    | Pull images, push CloudWatch logs, read Secrets  |
| `ticket-booking-task-role`| Application-level AWS access (e.g. SES for email)|

### Security Groups

- **ALB SG**: inbound 443 from 0.0.0.0/0; outbound to ECS SG on container ports
- **ECS SG**: inbound from ALB SG on 8081–8084; outbound to RDS SG on 5432
- **RDS SG**: inbound from ECS SG on 5432 only

### CloudWatch Alarms (recommended)

- 5xx error rate > 1% for 5 minutes → SNS alert
- CPU > 80% for 3 minutes → SNS alert + auto-scale trigger
- Memory > 80% → SNS alert

## Local Development

```bash
# Start Axon Server first (required)
docker run -d -p 8024:8024 -p 8124:8124 axoniq/axonserver

# Start all services with H2 (no Docker required for services)
cd show-service    && mvn spring-boot:run -Dspring-boot.run.profiles=dev &
cd booking-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev &
cd payment-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev &
cd notification-service && mvn spring-boot:run -Dspring-boot.run.profiles=dev &

# Or via Docker Compose (PostgreSQL mode)
docker compose up --build

# Swagger UIs
open http://localhost:8081/swagger-ui.html  # show-service
open http://localhost:8082/swagger-ui.html  # booking-service
open http://localhost:8083/swagger-ui.html  # payment-service
open http://localhost:8084/swagger-ui.html  # notification-service
```

## Running Tests

```bash
# Unit + Integration tests for all services
mvn verify -Dspring.profiles.active=dev

# Pact consumer (generates pact files in booking-service/target/pacts/)
mvn -pl booking-service test -Dtest=BookingPaymentPactConsumerTest

# Pact provider verification (reads pact files from booking-service)
mvn -pl payment-service test -Dtest=PaymentPactProviderTest
```

## Environment Variables Summary

| Variable       | Services               | Description                    |
|----------------|------------------------|--------------------------------|
| `DB_HOST`      | all                    | PostgreSQL host                |
| `DB_PORT`      | all                    | PostgreSQL port (default 5432) |
| `DB_NAME`      | per-service            | Database name                  |
| `DB_USERNAME`  | per-service            | Database user                  |
| `DB_PASSWORD`  | per-service            | Database password (secret)     |
| `SPRING_PROFILES_ACTIVE` | all        | `dev` or `prod`                |
| `payment.chaos.failure-rate` | payment-service | Failure probability when chaos is enabled (default `0.5`) |

## GitHub Secrets Required

| Secret                      | Used by workflow |
|-----------------------------|-----------------|
| `DOCKERHUB_USERNAME`        | dev, test, prod |
| `DOCKERHUB_TOKEN`           | dev, test, prod |
| `AWS_ACCESS_KEY_ID`         | dev, test       |
| `AWS_SECRET_ACCESS_KEY`     | dev, test       |
| `AWS_ACCESS_KEY_ID_PROD`    | prod            |
| `AWS_SECRET_ACCESS_KEY_PROD`| prod            |

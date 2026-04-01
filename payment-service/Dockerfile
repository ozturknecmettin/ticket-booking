# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build
COPY pom.xml ./
COPY show-service/pom.xml         show-service/
COPY booking-service/pom.xml      booking-service/
COPY payment-service/pom.xml      payment-service/
COPY notification-service/pom.xml notification-service/
COPY ui-service/pom.xml           ui-service/

RUN mvn -pl payment-service -am dependency:go-offline -B -q

COPY payment-service/src payment-service/src
RUN mvn -pl payment-service -am package -DskipTests -B -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /build/payment-service/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]

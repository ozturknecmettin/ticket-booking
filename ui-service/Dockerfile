# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy all module poms so Maven can parse the parent <modules> list
COPY pom.xml ./
COPY show-service/pom.xml         show-service/
COPY booking-service/pom.xml      booking-service/
COPY payment-service/pom.xml      payment-service/
COPY notification-service/pom.xml notification-service/
COPY ui-service/pom.xml           ui-service/

# Download dependencies (cached if poms unchanged)
RUN mvn -pl ui-service dependency:go-offline -B -q

# Copy source and build
COPY ui-service/src ui-service/src
RUN mvn -pl ui-service package -DskipTests -B -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /build/ui-service/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]

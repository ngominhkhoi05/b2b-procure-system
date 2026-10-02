# =============================================================================
# Multi-stage Dockerfile for b2b-procure-system (Spring Boot 4.1.1 / Java 21)
# Build context = repository root (where build.gradle and settings.gradle live)
# =============================================================================

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Gradle wrapper + config first so dependencies are cached in a separate layer.
COPY gradlew settings.gradle build.gradle gradlew.bat ./
COPY gradle ./gradle

# Ensure wrapper is executable and verify Java toolchain (no-op download thanks to Docker BuildKit cache).
RUN chmod +x ./gradlew \
 && ./gradlew --version

# Copy sources and build the bootJar (skip tests — they run in CI).
COPY src ./src
RUN ./gradlew clean bootJar -x test --no-daemon

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# wget is already in alpine; no need to install it for the HEALTHCHECK.
RUN addgroup -S spring \
 && adduser -S spring -G spring

USER spring:spring

# Copy the Spring Boot fat jar produced by the build stage.
# Spring Boot 4.x writes the artifact to build/libs/<artifactId>-<version>.jar.
COPY --from=build /app/build/libs/*.jar app.jar

# Sensible container defaults; can be overridden in docker-compose / env.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Ho_Chi_Minh

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

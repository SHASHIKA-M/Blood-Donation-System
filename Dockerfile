# =============================================================
#  Dockerfile  —  Render-ready for BloodConnect (Java 17 / Spring Boot)
#
#  WHY DOCKER: Render has native runtimes for Node, Python, Ruby, Go,
#  Rust and Elixir only. Java/Spring Boot must be deployed as a Docker
#  service. This file builds the jar and runs it on a slim JRE image.
# =============================================================

# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the POM first so Docker can cache the dependency download layer
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Then the source
COPY src ./src

# Tests are skipped inside the image: Render build minutes are limited,
# and the H2 test slice is meant for local / CI runs.
RUN mvn -q -B -DskipTests package

# ---------- Stage 2: run it ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render injects PORT (default 10000). application.properties already reads
# ${PORT:8899}, so no command-line flag is required.
ENV PORT=10000

# Keep the JVM happy inside the 512 MB free instance
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Dfile.encoding=UTF-8"

EXPOSE 10000

# Health probe: used by Render health checks and `docker ps`
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT}/health" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

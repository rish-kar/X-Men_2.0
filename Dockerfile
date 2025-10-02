# Multi-stage build for Spring Boot (Java 21.0.6 LTS)
# Stage 1: Build with Maven + Temurin JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy POM first to leverage layer caching
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

# Copy sources
COPY src ./src

# Build application (skip tests to speed image build; run tests in CI separately)
RUN mvn -q -DskipTests package

# Stage 2: Runtime (Temurin JRE 21.0.6 LTS)
FROM eclipse-temurin:21-jre AS runtime
LABEL org.opencontainers.image.title="X-Men" \
      org.opencontainers.image.description="X-Men: Mutation-Based Analysis of Security Ceremonies" \
      org.opencontainers.image.licenses="UNLICENSED"

# Install netcat for health checks (tiny footprint)
RUN apt-get update \
    && apt-get install -y --no-install-recommends netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Non-root user
RUN useradd --create-home --shell /bin/sh appuser
USER appuser
WORKDIR /app

# Allow overriding jar name if version changes
ARG JAR_FILE=target/X-Men_3.0-0.0.1-SNAPSHOT.jar
COPY --from=build /workspace/${JAR_FILE} app.jar

# Environment defaults (match application.yaml fallbacks)
ENV SERVER_PORT=8081 \
    APP_NAME="X-Men" \
    APP_CORS_ALLOWED_ORIGINS="http://localhost:8081,http://localhost:8082,http://localhost:8083,http://localhost:5173" \
    JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=default

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD nc -z 127.0.0.1 $SERVER_PORT || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar app.jar"]

# Build: docker build -t x-men:latest .
# Run:   docker run -p 8081:8081 x-men:latest
# Custom jar: docker build --build-arg JAR_FILE=target/your.jar -t x-men:latest .

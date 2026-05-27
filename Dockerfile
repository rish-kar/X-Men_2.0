# syntax=docker/dockerfile:1.7
#
# X-Men — multi-stage container image
# ----------------------------------------------------------------------
#  Stage 1: build with Maven + Eclipse Temurin JDK 21
#  Stage 2: slim runtime with Eclipse Temurin JRE 21, non-root user
#
# Build:    docker build -t x-men:latest .
# Run:      docker run --rm -p 8081:8081 x-men:latest
# Compose:  docker compose up --build
# ----------------------------------------------------------------------

# ---------- Stage 1: build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies first so subsequent layers can be cached.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -e dependency:go-offline

# Copy sources (this is the layer that changes most often).
COPY src ./src

# Package — tests run in CI, not in image builds.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -DskipTests package


# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-noble AS runtime

LABEL org.opencontainers.image.title="X-Men" \
      org.opencontainers.image.description="X-Men: Mutation-Based Analysis of Security Ceremonies" \
      org.opencontainers.image.licenses="UNLICENSED"

# Tiny tool needed for the HEALTHCHECK plus the native Linux libraries JavaFX needs
# when the JVM starts with -Djava.awt.headless=false inside Docker. JavaFX media
# uses the Linux GStreamer/FFmpeg stack for MP4/H.264/AAC playback, so those
# plugins are part of the runtime image rather than an optional host dependency.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        netcat-openbsd \
        xvfb \
        xauth \
        ffmpeg \
        gstreamer1.0-libav \
        gstreamer1.0-plugins-base \
        gstreamer1.0-plugins-good \
        gstreamer1.0-plugins-bad \
        gstreamer1.0-plugins-ugly \
        libasound2t64 \
        libavcodec-extra \
        libavformat60 \
        libavutil58 \
        libglib2.0-0 \
        libgl1 \
        libgstreamer-plugins-base1.0-0 \
        libgstreamer1.0-0 \
        libgtk-3-0 \
        libpulse0 \
        libswscale7 \
        libx11-6 \
        libxext6 \
        libxi6 \
        libxrandr2 \
        libxrender1 \
        libxtst6 \
        libxxf86vm1 \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN useradd --create-home --shell /bin/sh appuser
USER appuser
WORKDIR /app

# Override the jar name with --build-arg if your version changes.
ARG JAR_FILE=target/X-Men_2.0-0.0.1-SNAPSHOT.jar
COPY --from=build /workspace/${JAR_FILE} app.jar

# Defaults that match application.yaml fallbacks. All overridable at `docker run` time.
ENV SERVER_PORT=8081 \
    APP_NAME="X-Men" \
    APP_CORS_ALLOWED_ORIGINS="http://localhost:8081,http://localhost:8082,http://localhost:8083,http://localhost:5173" \
    DERIVATION_SERVICE_URL="http://localhost:9091" \
    JAVA_OPTS="-Djava.awt.headless=false" \
    XMEN_UI_ENABLED=false \
    SPRING_PROFILES_ACTIVE=default

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD nc -z 127.0.0.1 $SERVER_PORT || exit 1

ENTRYPOINT ["sh","-c","Xvfb :99 -screen 0 1280x1024x24 -nolisten tcp >/tmp/xvfb.log 2>&1 & export DISPLAY=:99; exec java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar app.jar"]

# --- Stage 1: Build Stage ---
# We use the full JDK to compile the app.
# BellSoft provides verified Java 25 images for Alpine.
FROM bellsoft/liberica-openjdk-alpine:25 AS builder

WORKDIR /build

# 1. Cache Gradle Wrapper and Dependencies
# By copying only these files first, we avoid re-downloading
# dependencies every time your source code changes.
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

# Grant execution rights and pre-download dependencies
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon > /dev/null 2>&1 || true

# 2. Build the Application
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# 3. Layered Extraction (Spring Boot 3.4+ Standard)
# This splits the fat JAR into 4 layers: dependencies, loader, snapshots, and application code.
RUN java -Djarmode=layertools -jar build/libs/*.jar extract

# --- Stage 2: Runtime Stage ---
# Use the minimal JRE for production to reduce the attack surface.
FROM bellsoft/liberica-openjre-alpine:25

# 4. Security: Run as a Non-Root User
# This prevents an attacker from gaining root access to the host if the container is breached.
RUN addgroup -S adiwave && adduser -S springuser -G adiwave
USER springuser

WORKDIR /app

# 5. Copy extracted layers from the builder stage
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

# 6. Performance & Security Tuning
# -XX:MaxRAMPercentage: Ensures the JVM respects Docker memory limits.
# -Djava.security.egd: Speeds up cryptographic operations (like JWT signing/verification).
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "org.springframework.boot.loader.launch.JarLauncher"]
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Cache Gradle dependencies
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew \
 && ./gradlew dependencies --no-daemon

# Build application
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Extract layered JAR (Spring Boot 3.4+)
RUN java -Djarmode=layertools -jar build/libs/*.jar extract


# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-jammy

# Security: non-root user
RUN groupadd -r adiwave && useradd -r -g adiwave springuser
USER springuser

WORKDIR /app

# Copy layers
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

EXPOSE 9090

# JVM tuning
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
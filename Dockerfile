# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache Maven dependencies separately (layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Build the application (skip tests — DB not available at build time)
COPY src ./src
RUN mvn package -DskipTests -B -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Uploads directory (ephemeral on Render — replace with S3 for persistence)
RUN mkdir -p /app/uploads && chown spring:spring /app/uploads

COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# Container-aware JVM: uses cgroup memory limits, not host RAM
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Djava.net.preferIPv4Stack=true", \
  "-jar", "app.jar"]

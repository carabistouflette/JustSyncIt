# Multi-stage build for JustSyncIt application
FROM openjdk:21-jdk-slim as builder

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies
RUN ./gradlew downloadDependencies --no-daemon

# Copy source code
COPY src src

# Copy additional files needed for distribution
COPY LICENSE README.md CHANGELOG.md docs examples scripts ./

# Build the application
RUN ./gradlew releaseBuild --no-daemon

# Runtime stage
FROM openjdk:21-jre-slim

# Install required packages for runtime
RUN apt-get update && apt-get install -y \
    curl \
    unzip \
    tar \
    && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r justsyncit && useradd -r -g justsyncit justsyncit

# Set working directory
WORKDIR /opt/justsyncit

# Copy the distribution from builder stage
COPY --from=builder /app/build/distributions/justsyncit-*.tar.gz /tmp/

# Extract the distribution
RUN tar -xzf /tmp/justsyncit-*.tar.gz --strip-components=1 -C /opt/justsyncit && \
    rm /tmp/justsyncit-*.tar.gz

# Make startup scripts executable
RUN chmod +x bin/start.sh

# Create data and logs directories
RUN mkdir -p /opt/justsyncit/data /opt/justsyncit/logs && \
    chown -R justsyncit:justsyncit /opt/justsyncit

# Switch to non-root user
USER justsyncit

# Expose port for server mode
EXPOSE 8080

# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD bin/start.sh --version || exit 1

# Set JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Set default command
ENTRYPOINT ["bin/start.sh"]
CMD ["--help"]
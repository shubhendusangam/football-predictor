# ============================================
# Model Training Service - Dockerfile
# Use this when JAR is pre-built locally
# For full build from source, use Dockerfile.multi-stage
# ============================================

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Create directories for data and logs
RUN mkdir -p /app/data /app/logs/model-training && chown -R appuser:appgroup /app

# Copy the built JAR
COPY target/model-training-service-1.0.0.jar app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8081

# Environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1024m" \
    SPRING_PROFILES_ACTIVE=docker

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/api/training/model-info || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]


# ============================================
# Football Match Predictor - Multi-Module Dockerfile
# Multi-stage build for optimized image size
# ============================================

# Stage 1: Build All Modules
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and parent pom.xml first (for better caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy module pom.xml files
COPY football-prediction-app/pom.xml football-prediction-app/
COPY model-training-service/pom.xml model-training-service/

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -B

# Copy all source code
COPY football-prediction-app/src football-prediction-app/src
COPY model-training-service/src model-training-service/src

# Build all modules (skip tests for faster build)
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime for Main Application
FROM eclipse-temurin:21-jre-alpine

ARG MODULE_NAME=football-prediction-app

WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Create data directory for H2 database and model
RUN mkdir -p /app/data /app/logs && chown -R appuser:appgroup /app

# Copy the built JAR from builder stage
COPY --from=builder /app/${MODULE_NAME}/target/*.jar app.jar

# Copy static resources and data files if they exist
COPY --from=builder --chown=appuser:appgroup /app/${MODULE_NAME}/src/main/resources/data /app/data/csv 2>/dev/null || true
COPY --from=builder --chown=appuser:appgroup /app/${MODULE_NAME}/data /app/data 2>/dev/null || true

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Environment variables
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=docker

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/model/status || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]


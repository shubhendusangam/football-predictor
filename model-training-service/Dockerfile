FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR
COPY target/model-training-service-1.0.0.jar app.jar

# Create directories for data and logs
RUN mkdir -p /app/data /app/logs/model-training

# Expose port
EXPOSE 8081

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]


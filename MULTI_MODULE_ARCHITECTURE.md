# 📦 Multi-Module Architecture Guide

## Overview
The Football Prediction application has been restructured as a multi-module Maven project for better separation of concerns, maintainability, and scalability.

## 🏗️ Module Structure

```
football-prediction-parent/
├── pom.xml                          # Parent POM with dependency management
├── README.md                        # Main project documentation
├── docker-compose.yml               # Multi-service container setup
├── Dockerfile                       # Multi-stage build for all modules
├── scripts/                         # Build and deployment scripts
├── data/                           # Shared data files
├── logs/                           # Shared log files  
│
├── football-prediction-app/         # Main Application Module
│   ├── pom.xml                     # App-specific dependencies
│   ├── README.md                   # App module documentation
│   ├── src/                        # Application source code
│   │   ├── main/java/              # Java source
│   │   └── test/java/              # Test source
│   ├── data/                       # App-specific data
│   └── logs/                       # App-specific logs
│
└── model-training-service/          # Training Service Module
    ├── pom.xml                     # Service-specific dependencies
    ├── README.md                   # Service module documentation
    ├── Dockerfile                  # Service-specific Docker build
    └── src/                        # Service source code
        ├── main/java/              # Java source
        └── test/java/              # Test source
```

## 🎯 Module Responsibilities

### Parent Module (`football-prediction-parent`)
- **Purpose**: Project coordination and dependency management
- **Responsibilities**:
  - Defines common dependencies and versions
  - Manages build plugins configuration
  - Coordinates multi-module build process
  - Provides shared profiles (dev, docker, test)

### Main Application (`football-prediction-app`)
- **Purpose**: Core web application and prediction service
- **Port**: 8080
- **Responsibilities**:
  - Web UI (responsive HTML/CSS/JS)
  - REST API endpoints
  - Data ingestion and processing
  - Feature engineering
  - External API integrations
  - Database management
  - User interface for predictions

### Model Training Service (`model-training-service`)
- **Purpose**: Dedicated ML model training and evaluation
- **Port**: 8081  
- **Responsibilities**:
  - Model training algorithms
  - Cross-validation and evaluation
  - Hyperparameter tuning
  - Model persistence and versioning
  - Scheduled training jobs
  - Advanced ML experiments

## 🔧 Build Process

### Single Command Build
```bash
# Build all modules from parent directory
mvn clean install
```

### Individual Module Build
```bash
# Build specific module
cd football-prediction-app
mvn clean package

# Or build training service
cd model-training-service  
mvn clean package
```

### Development Workflow
```bash
# 1. Build all modules
mvn clean install

# 2. Run main application
cd football-prediction-app
mvn spring-boot:run

# 3. In another terminal, run training service
cd model-training-service
mvn spring-boot:run
```

## 📋 Dependency Management

### Parent POM Benefits
- **Version Control**: Centralized dependency version management
- **Consistency**: Same versions across all modules
- **Maintenance**: Easy updates via parent POM
- **Conflict Resolution**: Automatic dependency resolution

### Module Dependencies
```xml
<!-- In football-prediction-app/pom.xml -->
<dependency>
    <groupId>com.app</groupId>
    <artifactId>model-training-service</artifactId>
    <!-- Version managed by parent -->
</dependency>
```

## 🐳 Docker Integration

### Multi-Stage Build
```dockerfile
# Builds all modules in single Docker build
FROM eclipse-temurin:21-jdk-alpine AS builder
# ... build all modules
FROM eclipse-temurin:21-jre-alpine
# ... runtime for specific module
```

### Docker Compose
```yaml
# Orchestrates both services
services:
  football-predictor:
    build: ./football-prediction-app
    ports: ["8080:8080"]
    
  model-training-service:
    build: ./model-training-service  
    ports: ["8081:8081"]
```

## 🚀 Deployment Options

### 1. Development (Separate JVMs)
```bash
./scripts/start-services.sh
# Choose option 2: Separate processes
```

### 2. Docker Compose (Recommended)
```bash
./scripts/start-services.sh
# Choose option 1: Docker Compose
```

### 3. Kubernetes (Production)
```bash
# Build images
docker build -t football-app football-prediction-app/
docker build -t training-service model-training-service/

# Deploy with Helm or kubectl
kubectl apply -f k8s/
```

## 🧪 Testing Strategy

### Unit Tests
```bash
# Test all modules
mvn test

# Test specific module
cd football-prediction-app
mvn test
```

### Integration Tests
```bash
# Run integration tests
mvn verify

# Module-specific integration tests
cd model-training-service
mvn verify -Pintegration-test
```

### End-to-End Tests
```bash
# Start services and run E2E tests
./scripts/start-services.sh
./scripts/test-apis.sh
```

## 📊 Module Communication

### Internal Communication
- **Model Training → Main App**: Shared database, file system
- **Main App → Training Service**: REST API calls (localhost:8081)
- **Shared Resources**: Data files, model artifacts, logs

### External Communication
- **Main App**: Handles all external API calls
- **Training Service**: Focuses on ML operations only

## 🔄 CI/CD Pipeline

### Maven Reactor Build
```bash
# Single command builds all modules in correct order
mvn clean deploy
```

### Pipeline Stages
1. **Build Parent**: Validate parent POM
2. **Build Modules**: Parallel module compilation  
3. **Test Modules**: Run all test suites
4. **Package**: Create JAR artifacts
5. **Deploy**: Push to registry/repository

## 📈 Benefits of Modular Architecture

### Development Benefits
- **Separation of Concerns**: Clear module boundaries
- **Parallel Development**: Teams can work on different modules
- **Selective Building**: Build only changed modules
- **Better Testing**: Module-specific test strategies

### Operational Benefits
- **Independent Scaling**: Scale services independently
- **Resource Optimization**: Different resource requirements
- **Deployment Flexibility**: Deploy modules separately
- **Monitoring**: Module-specific metrics and logs

### Maintenance Benefits
- **Code Organization**: Logical code separation
- **Dependency Management**: Clear dependency hierarchy
- **Version Management**: Independent module versioning
- **Refactoring**: Easier to refactor individual modules

## 🛠️ IDE Setup

### IntelliJ IDEA
1. Import parent POM as Maven project
2. IDE automatically recognizes modules
3. Run configurations for each module
4. Integrated debugging across modules

### Visual Studio Code
1. Install Java Extension Pack
2. Open workspace at parent level
3. Configure launch configurations
4. Use integrated terminal for Maven commands

## 📝 Best Practices

### Module Design
- Keep modules focused and cohesive
- Minimize inter-module dependencies  
- Use interfaces for module communication
- Maintain clear API contracts

### Development Workflow
- Always build from parent for consistency
- Run tests before committing changes
- Use feature branches for module changes
- Document API changes between modules

### Deployment Strategy
- Test modules individually and together
- Use Docker Compose for local development
- Implement health checks for each module
- Monitor module interactions in production

This modular architecture provides a solid foundation for the Football Prediction system's continued growth and evolution.

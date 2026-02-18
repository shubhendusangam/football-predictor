# Football Prediction Application

## Overview
This is the main Spring Boot application module that provides the web interface and core functionality for the Football Match Predictor system.

## Module Structure
```
football-prediction-app/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/app/footballprediction/
│   │   │       ├── controller/          # REST controllers
│   │   │       ├── service/             # Business logic services
│   │   │       ├── repository/          # Data access layer
│   │   │       ├── model/               # Entity classes
│   │   │       ├── dto/                 # Data transfer objects
│   │   │       ├── config/              # Configuration classes
│   │   │       ├── ingestion/           # Data ingestion services
│   │   │       ├── featureengineering/  # Feature extraction
│   │   │       └── external/            # External API integration
│   │   └── resources/
│   │       ├── static/                  # Web assets (CSS, JS, HTML)
│   │       ├── application.properties   # Configuration
│   │       └── log4j2.xml              # Logging configuration
│   └── test/
├── data/                                # Database and model files
├── logs/                                # Application logs
└── pom.xml                             # Maven configuration
```

## Features
- **Web Interface**: Responsive UI for match predictions
- **REST APIs**: RESTful endpoints for predictions and data management
- **Data Ingestion**: CSV data loading and processing
- **External APIs**: Integration with football-data.org and news services
- **Feature Engineering**: Advanced statistical feature extraction
- **Model Integration**: Uses ML models from the training service

## Key Components

### Controllers
- `PredictionController`: Main prediction endpoints
- `DataController`: Data management operations
- `ExternalApiController`: External API integration
- `NewsController`: News aggregation service

### Services
- `PredictionService`: Core prediction logic
- `FeatureEngineeringService`: Statistical feature calculation
- `DataIngestionService`: CSV data processing
- `ExternalApiService`: Third-party API integration

### Configuration
- Spring Boot auto-configuration
- H2 database setup
- Log4j2 async logging
- CORS configuration for frontend

## Running the Application

### Development Mode
```bash
# From the parent directory
mvn clean install

# Run the main application
cd football-prediction-app
mvn spring-boot:run
```

### Production Mode
```bash
# Build the application
mvn clean package

# Run the JAR
java -jar target/football-prediction-app-1.0.0.jar
```

## Configuration

### Application Properties
- `application.properties`: Default configuration
- `application-docker.properties`: Docker-specific settings
- `application-test.properties`: Test configuration

### Database
- H2 in-memory database for development
- Database console available at `/h2-console`
- Data files stored in `data/` directory

### Logging
- Log4j2 async logging for performance
- Separate log files for different components
- Configurable log levels per environment

## API Endpoints

### Prediction APIs
- `POST /api/predict`: Make match predictions
- `GET /api/teams`: Get available teams
- `GET /api/model/status`: Check model status

### Data Management
- `POST /api/data/reload`: Reload data from CSV
- `POST /api/data/update`: Update and retrain model

### External APIs
- `GET /api/external/upcoming`: Get upcoming matches
- `GET /api/external/standings`: Get league standings
- `GET /api/news/premier-league`: Get Premier League news

## Dependencies
- Spring Boot 4.0.2
- Spring Data JPA
- Spring WebFlux (for reactive HTTP clients)
- H2 Database
- Weka ML library
- OpenCSV for data processing
- Log4j2 for logging

## Testing
```bash
# Run unit tests
mvn test

# Run integration tests
mvn verify

# Generate test reports
mvn surefire-report:report
```

## Deployment
The application can be deployed as:
- Standalone JAR file
- Docker container (see parent README for Docker setup)
- War file (with additional configuration)

For detailed deployment instructions, see the parent project documentation.

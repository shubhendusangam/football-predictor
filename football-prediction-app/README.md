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
- **Web Interface**: Responsive dark-themed PWA-ready UI for match predictions
- **REST APIs**: RESTful endpoints for predictions, training, and data management
- **Data Ingestion**: CSV data loading with duplicate detection (22 seasons of PL data)
- **External APIs**: Integration with football-data.org (matches, standings, calendar) and news services
- **Feature Engineering**: 25 statistical features in 3 phases:
  - Phase 1: Form points, goals scored/conceded, H2H win rates, total goals
  - Phase 2: Shots on target, corners averages
  - Phase 3: Goal difference, overall form, win/unbeaten streaks, rest days
- **Model Integration**: Stacked Ensemble (RandomForest + AdaBoostM1 + Logistic Regression)
- **Match Calendar**: Date-based predictions for upcoming fixtures
- **Live Standings**: Current Premier League table display
- **News Feed**: Premier League news aggregation

## Key Components

### Controllers
- `PredictionController`: Main prediction endpoints with probability distributions
- `ModelTrainingController`: Model training operations (basic, CV, ensemble, grid search)
- `DataController`: Data ingestion and management operations
- `ExternalApiController`: External API integration (matches, standings, calendar)
- `NewsController`: News aggregation service for Premier League news

### Services
- `PredictionService`: Core prediction logic using trained ML models
- `FeatureEngineeringService`: 25-feature statistical calculation (3 phases)
- `ModelTrainingService`: Model training orchestration with ensemble support
- `StackedEnsembleService`: Stacked model combining RF + AdaBoost + Logistic Regression
- `EnsembleModelService`: Voting ensembles, cross-validation, grid search
- `DataIngestionService`: CSV data processing with duplicate detection
- `FootballDataApiService`: Integration with football-data.org API

### ML Components
- `StackedEnsembleService`: Primary ensemble model
- `EnsembleModelService`: Cross-validation, grid search, voting/stacking ensembles
- `ModelTrainingService`: Data preparation and model evaluation

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

### Model Training APIs
- `POST /api/model/train`: Train model (uses Stacked Ensemble by default)
- `POST /api/model/train/stacked`: Train Stacked Ensemble (RF + GB + LR meta-model)
- `POST /api/model/train/advanced`: Advanced training with grid search
- `POST /api/model/train/cv`: Cross-validation training
- `POST /api/model/train/boosting`: Gradient Boosting training
- `POST /api/model/train/ensemble`: Voting ensemble training
- `POST /api/model/grid-search`: Hyperparameter optimization
- `GET /api/model/compare`: Compare all available models

### Stacked Ensemble Architecture
The default model uses a **Stacked Ensemble** approach:
```
┌─────────────────────────────────────────────────────────────┐
│                    STACKED ENSEMBLE                         │
├─────────────────────────────────────────────────────────────┤
│  Base Model 1: RandomForest (100 trees)                     │
│  Base Model 2: Gradient Boosting (AdaBoostM1, 100 rounds)   │
│  Meta Model:   Logistic Regression (combines predictions)   │
└─────────────────────────────────────────────────────────────┘
```
- Base models are trained on 64% of data
- Validation set (16%) is used to generate meta-features
- Meta-model learns optimal weighting of base model predictions
- Final evaluation on held-out test set (20%)

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

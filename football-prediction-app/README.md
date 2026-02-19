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
│   │   │       ├── dto/                 # Data transfer objects
│   │   │       ├── config/              # Configuration classes
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

### Web Interface
- **Dark Theme**: Modern responsive dark-themed UI
- **Prediction Modes**: Manual and Upcoming tabs for different prediction workflows
- **Quick Predictions**: Click on upcoming matches to instantly fill team dropdowns
- **Team Statistics**: Comprehensive 5-tab dashboard (Overview, Goals, Form, Matches, Rivals)
- **PL Calendar**: Date-based predictions with Today/Tomorrow/Weekend buttons
- **News Feed**: Latest Premier League news aggregation
- **Admin Panel**: Model training controls (requires authentication)
- **Keyboard Support**: Press Escape key to close modal dialogs for better accessibility

### REST APIs
- Prediction endpoints with probability distributions
- Team statistics endpoints
- Model training operations (basic, CV, ensemble, grid search)
- External API integration (football-data.org)
- News aggregation service

### Data & ML
- **Data Ingestion**: CSV data loading with duplicate detection (22 seasons of PL data)
- **Feature Engineering**: 25 statistical features in 3 phases:
  - Phase 1: Form points, goals scored/conceded, H2H win rates, total goals
  - Phase 2: Shots on target, corners averages
  - Phase 3: Goal difference, overall form, win/unbeaten streaks, rest days
- **Model Integration**: Stacked Ensemble (RandomForest + AdaBoostM1 + Logistic Regression)

## Key Components

### Controllers
- `PredictionController`: Match prediction endpoints with probability distributions
- `ModelTrainingController`: Model training operations
- `DataController`: Data ingestion and management
- `TeamStatsController`: Team statistics endpoints
- `ExternalApiController`: External API integration (matches, standings, calendar)
- `NewsController`: News aggregation service

### Services
- `PredictionService`: Core prediction logic using trained ML models
- `FeatureEngineeringService`: 25-feature statistical calculation
- `TeamStatsService`: Comprehensive team statistics with caching
- `ModelTrainingService`: Model training orchestration
- `StackedEnsembleService`: Stacked model combining RF + AdaBoost + LR
- `DataIngestionService`: CSV data processing
- `FootballDataApiService`: Integration with football-data.org API

### DTOs
- `PredictionRequest/Response`: Prediction input/output
- `TeamStatsResponse`: Comprehensive team statistics with nested DTOs:
  - `OverallStats`: Total matches, W/D/L, points per game
  - `HomeAwayStats`: Home vs Away performance split
  - `GoalStats`: Scoring patterns, clean sheets, half-time analysis
  - `FormStats`: Recent form, streaks, shot conversion
  - `RecentMatch`: Last 10 match results with H/A badges
  - `H2HRecord`: Head-to-head rivalry statistics

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
- H2 file-based database
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

### Head-to-Head (H2H) APIs
- `GET /api/h2h?homeTeam=X&awayTeam=Y`: Get comprehensive H2H insights between two teams
  - Historical Record: "Arsenal leads 15-8-7 vs Chelsea" format
  - Recent Meetings: Last 5 H2H matches with scores
  - Goal Stats: Average goals, BTTS %, team averages
  - Common Results: Most frequent scoreline and outcome
  - Venue Advantage: Win % when each team plays at home

### Trending Insights APIs
- `GET /api/insights/trending`: Get live/trending insights across all teams
  - 🔥 Hot Teams: Teams on 3+ match winning streaks
  - ❄️ Cold Teams: Teams without a win in 5+ matches
  - ⚽ Top Scorers: Teams scoring most goals recently
  - 🧱 Defensive Walls: Teams with most clean sheets
  - 🎯 Upset Alerts: Matches where away team has >50% win probability
  - 🎉 Goal Fest: Matches with highest expected total goals

### Team Statistics APIs
- `GET /api/teams/{teamName}/stats`: Get comprehensive team statistics
- `GET /api/teams/compare?team1=X&team2=Y`: Compare two teams

### Model Training APIs
- `POST /api/model/train`: Train model (Stacked Ensemble)
- `POST /api/model/train/advanced`: Advanced training with grid search
- `POST /api/model/train/cv`: Cross-validation training
- `POST /api/model/train/boosting`: Gradient Boosting training
- `POST /api/model/train/ensemble`: Voting ensemble training
- `POST /api/model/grid-search`: Hyperparameter optimization
- `GET /api/model/compare`: Compare all available models

### External APIs
- `GET /api/external/predict`: Get upcoming match predictions
- `GET /api/external/standings`: Get league standings
- `GET /api/external/matches-by-date`: Get matches for a specific date
- `GET /api/news/premier-league`: Get Premier League news

### Data Management
- `POST /api/data/reload`: Reload data from CSV
- `POST /api/data/update`: Update and retrain model

## UI Components

### Make a Prediction Section
- **Manual Tab**: Select home/away teams from dropdowns
- **Upcoming Tab**: Auto-fetch and display upcoming Premier League predictions
- **Quick Predictions**: Clickable cards showing upcoming matches with form data

### Team Statistics Section
- **Overview Tab**: Total stats, W/D/L, home vs away split, current season
- **Goals Tab**: Scoring/defending metrics, half-time analysis
- **Form Tab**: Recent form (last 5/10), streaks, shot stats
- **Matches Tab**: Last 10 matches with H/A badges, scores, results
- **Rivals Tab**: Top 5 H2H records with win percentages

### Additional Sections
- **PL Calendar**: Date picker with quick buttons for Today/Tomorrow/Weekend
- **Upcoming Predictions**: Full list with standings
- **News Feed**: Latest football news
- **Admin Panel**: Model training and data management (authenticated)

## UI/UX Enhancements

### Keyboard Accessibility
The application includes full keyboard support for improved accessibility:

- **Escape Key**: Close modal dialogs (Admin Panel, etc.) by pressing the Escape key
- **Click Outside**: Click anywhere outside the modal to close it
- **Close Button**: Traditional X button for mouse users

**Implementation Details:**
- Event listeners are properly attached when modals open and removed when they close
- No memory leaks - clean event handling lifecycle
- Follows standard UI/UX patterns for modal interactions
- Compatible with screen readers and assistive technologies

### Modal Interactions
Users can close modals using any of these methods:
1. Press the `Escape` key on the keyboard
2. Click the X (close) button in the modal header
3. Click anywhere on the overlay (outside the modal)

This provides flexibility for different user preferences and accessibility needs.

## Dependencies
- Spring Boot 4.0.2
- Spring Data JPA
- Spring WebFlux (reactive HTTP clients)
- H2 Database
- Weka ML library
- OpenCSV for data processing
- Log4j2 for logging
- Lombok for boilerplate reduction

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

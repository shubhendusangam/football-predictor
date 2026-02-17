# ⚽ Football Match Outcome Predictor

A Spring Boot application that ingests historical Premier League CSV data and predicts match outcomes (Home Win / Draw / Away Win) using Machine Learning (Random Forest via Weka).

---

## 🧠 How It Works

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA FLOW ARCHITECTURE                          │
└─────────────────────────────────────────────────────────────────────────┘

    CSV Files (Premier League 2004-2026 | 22 Seasons | ~8000 matches)
    └── src/main/resources/data/
        ├── PL_04_05.csv ... PL_18_19.csv   (Historical data)
        ├── PL_19_20.csv ... PL_24_25.csv   (Recent seasons)
        └── PL_25_26.csv                     (Current season)
                │
                ▼
    ┌──────────────────────────────┐
    │    CsvIngestionService       │  ← Parses CSV, validates rows,
    │    (OpenCSV)                 │    skips duplicates
    └──────────────────────────────┘
                │
                ▼
    ┌──────────────────────────────┐
    │    H2 Database               │  ← Match entities persisted
    │    (MatchRepository)         │    with JPA/Hibernate
    └──────────────────────────────┘
                │
                ▼
    ┌──────────────────────────────┐
    │  FeatureEngineeringService   │  ← Computes form, H2H stats,
    │                              │    goal averages per team
    └──────────────────────────────┘
                │
                ▼
    ┌──────────────────────────────┐
    │   ModelTrainingService       │  ← Trains Random Forest (Weka)
    │   (Weka Random Forest)       │    80/20 temporal split
    └──────────────────────────────┘
                │
                ▼
    ┌──────────────────────────────┐
    │   PredictionController       │  ← REST API endpoints
    │   POST /api/predict          │
    └──────────────────────────────┘
                │
                ▼
    ┌──────────────────────────────┐
    │         Response             │
    │  {                           │
    │    "prediction": "HOME_WIN", │
    │    "probHomeWin": 0.55,      │
    │    "probDraw": 0.25,         │
    │    "probAwayWin": 0.20,      │
    │    "confidence": "MEDIUM"    │
    │  }                           │
    └──────────────────────────────┘
```

---

## 🛠️ Tech Stack

| Layer        | Technology                              |
|:-------------|:----------------------------------------|
| Language     | Java 21                                 |
| Framework    | Spring Boot 4.0.2                       |
| ML Library   | Weka 3.8.6 (Random Forest)              |
| Data Source  | CSV files (Premier League historical data) |
| Database     | H2 (embedded, file-based)               |
| CSV Parsing  | OpenCSV 5.12.0                          |
| Logging      | Log4j2 with async Disruptor             |
| Testing      | JUnit 5, Mockito, AssertJ, MockMvc      |
| Build Tool   | Maven                                   |

---

## 📋 Prerequisites

- Java 21+
- Maven 3.8+
- Premier League CSV data files (included in `src/main/resources/data/`)

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/<your-username>/football-prediction.git
cd football-prediction
```

### 2. Build and run

```bash
mvn clean install
mvn spring-boot:run
```

### Startup Sequence

On startup, the application automatically:

1. **Ingests CSV data** → Parses all configured CSV files, skips duplicates
2. **Loads or trains model** → If saved model exists, loads it; otherwise trains fresh
3. **Ready to predict** → REST API available at `http://localhost:8080`

```
═══════════════════════════════════════════════════
  ⚽  Football Match Outcome Predictor             
      Java + Spring Boot + Weka Random Forest      
═══════════════════════════════════════════════════
► Step 1: Ingesting CSV data...
► Step 2: Checking model...
  ✓ Model loaded by Spring at startup. Ready to predict!
```

---

## 🐳 Docker

### Quick Start with Docker

```bash
# Build the image
docker build -t football-predictor .

# Run the container
docker run -d -p 8080:8080 --name football-predictor football-predictor
```

### Using Docker Compose

```bash
# Start the application
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

### Docker Configuration

| File                          | Description                                      |
|:------------------------------|:-------------------------------------------------|
| `Dockerfile`                  | Multi-stage build (JDK for build, JRE for runtime) |
| `docker-compose.yml`          | Container orchestration with volume persistence  |
| `.dockerignore`               | Excludes unnecessary files from build context    |
| `application-docker.properties` | Docker-specific configuration                  |

### Environment Variables

| Variable                 | Description                | Default              |
|:-------------------------|:---------------------------|:---------------------|
| `JAVA_OPTS`              | JVM options                | `-Xms256m -Xmx512m`  |
| `FOOTBALL_API_KEY`       | football-data.org API key  | (your key)           |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile      | `docker`             |

### Persistent Data

The Docker volume `football-predictor-data` persists:
- H2 database (`footballdb`)
- Trained model (`match_predictor.model`)

```bash
# View volume
docker volume inspect football-predictor-data

# Backup data
docker cp football-predictor:/app/data ./backup
```

---

## 🖥️ Web Interface

The application includes a modern, responsive web UI accessible at `http://localhost:8080`.

### Features:
- **Team Selection**: Dropdown menus with all available teams loaded from the database
- **Match Prediction**: One-click predictions with detailed probability breakdown
- **Results Visualization**: Color-coded probability bars and confidence indicators
- **Analysis Features**: View the underlying statistics used for predictions (form points, goals average, H2H records)
- **Model Management**: Train/retrain the model and reload CSV data directly from the UI
- **Real-time Status**: See if the ML model is loaded and ready for predictions

### Screenshots

#### 1. Team Selection
Dropdown menus with all available teams loaded from the database:

```
┌────────────────────────────────────────────────────────────────┐
│  ⚽ Football Match Predictor                    [Model Ready]  │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  🎯 Make a Prediction                                          │
│                                                                │
│  Home Team                        Away Team                    │
│  ┌──────────────────────┐        ┌──────────────────────┐      │
│  │ Select a team...   ▼ │   VS   │ Select a team...   ▼ │      │
│  ├──────────────────────┤        └──────────────────────┘      │
│  │ Arsenal             │                                       │
│  │ Aston Villa         │                                       │
│  │ Bournemouth         │                                       │
│  │ Brentford           │                                       │
│  │ Brighton            │                                       │
│  │ Chelsea             │                                       │
│  │ Crystal Palace      │                                       │
│  │ Everton             │                                       │
│  │ Fulham              │                                       │
│  │ ...                 │                                       │
│  └──────────────────────┘                                      │
│                                                                │
│              [ Predict Match ]                                 │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

#### 2. Match Prediction
One-click predictions with detailed probability breakdown:
<img width="1009" height="864" alt="image" src="https://github.com/user-attachments/assets/2ce6145e-1827-4dc4-9885-0e697d57e45f" />

#### 3. Results Visualization
Color-coded probability bars and confidence indicators:
<img width="1009" height="228" alt="image" src="https://github.com/user-attachments/assets/fdc1a5cb-16f1-4a00-b290-b38bf80fe568" />

#### 4. Analysis Features
View underlying statistics used for predictions:
<img width="1009" height="321" alt="image" src="https://github.com/user-attachments/assets/183810ce-8c0a-401a-85a6-5dea373ec54f" />

#### 5. Model Management
Train/retrain the model and manage data from the UI:
<img width="942" height="319" alt="image" src="https://github.com/user-attachments/assets/06338c8a-6e09-4272-81ee-2a8baf83de11" />

#### 6. Football News
<img width="984" height="713" alt="image" src="https://github.com/user-attachments/assets/13732236-2ccd-412b-aa00-338fc5c46663" />

#### 7. Real-time Status
See if the ML model is loaded and ready:
<img width="972" height="633" alt="image" src="https://github.com/user-attachments/assets/f74d6a57-ee2d-47a0-9597-f09a1c91cd19" />

#### 8. Calender
Premier League Calendar : Prediction based on the selected date
<img width="1009" height="489" alt="image" src="https://github.com/user-attachments/assets/52b71589-b087-4019-8ba1-56b50c0164af" />

#### 9. Predict upcoming Matches
<img width="607" height="1286" alt="image" src="https://github.com/user-attachments/assets/f14b0c2f-f320-4543-bd24-64c1338d72bb" />

#### 10. Current Standing
<img width="749" height="785" alt="image" src="https://github.com/user-attachments/assets/65a35d88-1ca3-4aa6-b1ea-43a97f6f70f4" />

---

## 📡 API Endpoints

### Get all teams

```http
GET /api/teams
```

**Response:**
```json
["Arsenal", "Aston Villa", "Brighton", "Chelsea", "Crystal Palace", ...]
```

### Predict a match outcome

```http
POST /api/predict
Content-Type: application/json

{
  "homeTeam": "Arsenal",
  "awayTeam": "Chelsea"
}
```

> ⚠️ Team names must match exactly what's in the CSV data (e.g., "Man United", "Tottenham")

**Response:**

```json
{
  "homeTeam": "Arsenal",
  "awayTeam": "Chelsea",
  "prediction": "HOME_WIN",
  "predictionCode": "H",
  "probHomeWin": 0.55,
  "probDraw": 0.25,
  "probAwayWin": 0.20,
  "confidence": "MEDIUM",
  "features": {
    "homeFormPoints": 2.4,
    "awayFormPoints": 1.8,
    "homeGoalsScoredAvg": 2.1,
    "awayGoalsScoredAvg": 1.5,
    "h2hHomeWinRate": 0.4,
    "h2hDrawRate": 0.3,
    "h2hAwayWinRate": 0.3
  }
}
```

### Retrain the model

```http
POST /api/model/train
```

Retrains the Random Forest classifier from all data in the database. Takes 30-60 seconds.

### Check model status

```http
GET /api/model/status
```

Returns whether the model is loaded and ready for predictions.

### Reload CSV data

```http
POST /api/data/reload
```

Re-ingests CSV files (useful after adding new season data). Skips already-loaded matches.

### H2 Database Console

```
http://localhost:8080/h2-console
```

JDBC URL: `jdbc:h2:file:./data/footballdb`

---

## 🌐 External API Integration (football-data.org)

The application integrates with [football-data.org](https://www.football-data.org/) to fetch real-time current season data and predict upcoming matches.

### Configuration

Add your API key to `application.properties`:
```properties
football.api.key=YOUR_API_KEY_HERE
football.api.base-url=https://api.football-data.org/v4
```

Get a free API key at: https://www.football-data.org/client/register

**Free tier limits:** 10 requests per minute

### Predict Upcoming Matches

```http
GET /api/external/predict?competition=PL&limit=10
```

Fetches upcoming matches from the external API and predicts outcomes using:
- Historical data from our database (form, H2H, goals)
- Current season form from football-data.org

**Response:**
```json
{
  "competition": "PL",
  "competitionName": "Premier League",
  "currentMatchday": 25,
  "predictions": [
    {
      "matchId": 123456,
      "matchDate": "2025-02-22T15:00:00Z",
      "homeTeam": "Arsenal",
      "awayTeam": "Chelsea",
      "prediction": "HOME_WIN",
      "probHomeWin": 0.55,
      "probDraw": 0.25,
      "probAwayWin": 0.20,
      "confidence": "MEDIUM",
      "homeTeamForm": {
        "recentForm": "W,W,D,L,W",
        "position": 2,
        "points": 50,
        "pointsPerGame": 2.08
      },
      "awayTeamForm": {
        "recentForm": "W,D,L,W,L",
        "position": 5,
        "points": 42
      }
    }
  ]
}
```

### Get Current Standings

```http
GET /api/external/standings?competition=PL
```

### Get Upcoming Matches (raw)

```http
GET /api/external/upcoming?competition=PL
```

### Get Finished Matches (raw)

```http
GET /api/external/finished?competition=PL
```

### Supported Competitions

| Code | Competition                    | Status        |
|:-----|:-------------------------------|:--------------|
| PL   | 🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier League             | ✅ Supported  |

> **Note:** Currently only Premier League is supported as we only have historical data for this competition. Other leagues (La Liga, Bundesliga, Serie A, Ligue 1) may be added in the future.

---


## 🧪 Features Used for Prediction

The model uses **25 features** computed from historical data:

### Core Features (15)

| #  | Feature                | Description                             | Calculation           |
|:---|:-----------------------|:----------------------------------------|:----------------------|
| 0  | `homeFormPoints`       | Points per game in last 5 home matches  | W=3, D=1, L=0         |
| 1  | `awayFormPoints`       | Points per game in last 5 away matches  | W=3, D=1, L=0         |
| 2  | `homeGoalsScoredAvg`   | Avg goals scored at home                | Last 20 home matches  |
| 3  | `homeGoalsConcededAvg` | Avg goals conceded at home              | Last 20 home matches  |
| 4  | `awayGoalsScoredAvg`   | Avg goals scored away                   | Last 20 away matches  |
| 5  | `awayGoalsConcededAvg` | Avg goals conceded away                 | Last 20 away matches  |
| 6  | `homeTotalGoalsAvg`    | Avg total goals in home team's matches  | Last 5 matches        |
| 7  | `awayTotalGoalsAvg`    | Avg total goals in away team's matches  | Last 5 matches        |
| 8  | `h2hHomeWinRate`       | Head-to-head home win rate              | All historical H2H    |
| 9  | `h2hDrawRate`          | Head-to-head draw rate                  | All historical H2H    |
| 10 | `h2hAwayWinRate`       | Head-to-head away win rate              | All historical H2H    |
| 11 | `homeShotsOnTargetAvg` | Avg shots on target at home             | Last 10 home matches  |
| 12 | `awayShotsOnTargetAvg` | Avg shots on target away                | Last 10 away matches  |
| 13 | `homeCornersAvg`       | Avg corners at home                     | Last 10 home matches  |
| 14 | `awayCornersAvg`       | Avg corners away                        | Last 10 away matches  |

### Enhanced Features (10)

| #  | Feature                  | Description                               | Impact |
|:---|:-------------------------|:------------------------------------------|:-------|
| 15 | `homeGoalDifference`     | Goals scored - conceded (last 5 matches)  | High   |
| 16 | `awayGoalDifference`     | Goals scored - conceded (last 5 matches)  | High   |
| 17 | `homeOverallFormPoints`  | Form across ALL matches (not just home)   | Medium |
| 18 | `awayOverallFormPoints`  | Form across ALL matches (not just away)   | Medium |
| 19 | `homeWinStreak`          | Consecutive wins (momentum)               | Medium |
| 20 | `awayWinStreak`          | Consecutive wins (momentum)               | Medium |
| 21 | `homeUnbeatenStreak`     | Matches without loss                      | Medium |
| 22 | `awayUnbeatenStreak`     | Matches without loss                      | Medium |
| 23 | `homeDaysSinceLastMatch` | Rest/fatigue factor                       | Medium |
| 24 | `awayDaysSinceLastMatch` | Rest/fatigue factor                       | Medium |

> ⚠️ **No data leakage:** Only pre-match knowable features are used. In-game stats from the match being predicted are never included.

---

## 🏗️ Project Structure

```
src/
├── main/
│   ├── java/com/app/footballprediction/
│   │   ├── FootballPredictionApplication.java  # Entry point + ApplicationRunner
│   │   ├── config/
│   │   │   ├── CacheConfig.java                # Caching configuration
│   │   │   ├── FootballApiConfig.java          # External API config
│   │   │   ├── RateLimitFilter.java            # API rate limiting
│   │   │   ├── RequestLoggingFilter.java       # HTTP request logging
│   │   │   └── WekaModelConfig.java            # Loads saved model as @Bean
│   │   ├── controller/
│   │   │   ├── ExternalApiController.java      # football-data.org endpoints
│   │   │   └── PredictionController.java       # Main REST API endpoints
│   │   ├── dto/
│   │   │   ├── PredictRequest.java             # { homeTeam, awayTeam }
│   │   │   ├── PredictResponse.java            # Prediction + probabilities
│   │   │   ├── UpcomingPredictionResponse.java # Upcoming match predictions
│   │   │   └── external/                       # External API DTOs
│   │   │       ├── FootballApiResponse.java
│   │   │       └── StandingsResponse.java
│   │   ├── featureengineering/
│   │   │   └── FeatureEngineeringService.java  # Computes 25 ML features
│   │   ├── model/
│   │   │   ├── Match.java                      # JPA entity (@Entity, @Table)
│   │   │   └── MatchFeatures.java              # Feature vector POJO
│   │   ├── modeltraining/
│   │   │   └── ModelTrainingService.java       # Weka RF training + prediction
│   │   ├── repository/
│   │   │   └── MatchRepository.java            # JPA queries (form, H2H, etc.)
│   │   ├── scheduler/
│   │   │   └── DataUpdateScheduler.java        # Auto-update data weekly
│   │   └── service/
│   │       ├── CsvIngestionService.java        # CSV parsing + batch insert
│   │       ├── FootballDataApiService.java     # football-data.org API client
│   │       └── FootballDataService.java        # Data orchestration
│   └── resources/
│       ├── application.properties              # Configuration
│       ├── application-docker.properties       # Docker-specific config
│       ├── log4j2.xml                          # Logging config
│       ├── static/                             # Web UI files
│       │   ├── index.html
│       │   ├── css/styles.css
│       │   └── js/app.js
│       └── data/                               # Premier League CSVs (22 seasons)
│           ├── PL_04_05.csv ... PL_18_19.csv   # Historical data
│           ├── PL_19_20.csv ... PL_24_25.csv   # Recent seasons
│           └── PL_25_26.csv                    # Current season
└── test/
    └── java/com/app/footballprediction/
        ├── FootballPredictionApplicationTests.java
        └── FootballPredictionE2ETest.java
```

---

## 📊 Model Training Pipeline

```
┌────────────────────────────────────────────────────────────────────┐
│                    MODEL TRAINING PIPELINE                         │
└────────────────────────────────────────────────────────────────────┘

1. Load all matches from DB (ordered by date ASC)
        │
        ▼
2. Build feature vectors for each match
   └── Skip matches with no history (start of season)
        │
        ▼
3. Temporal split: 80% train / 20% test (most recent)
   └── Avoids data leakage from future matches
        │
        ▼
4. Convert to Weka Instances (25 numeric features + 1 nominal label)
        │
        ▼
5. Train Random Forest
   └── 100 trees, 4 features per split, seed=42
        │
        ▼
6. Evaluate on test set
   └── Confusion matrix, precision, recall, F1
        │
        ▼
7. Save model + schema to disk
   └── ./data/match_predictor.model
```

---

## 📈 Model Performance

Football outcomes are inherently difficult to predict. Expected accuracy benchmarks:

| Benchmark                               | Accuracy |
|:----------------------------------------|:---------|
| Naive baseline (always predict Home Win) | ~45%    |
| This model (Random Forest)              | ~53–56%  |
| State of the art (with xG data)         | ~58%     |

Evaluation uses **temporal split** — trained on older matches, tested on most recent 20%. Standard K-fold is avoided because match data is time-series.

---

## ⚙️ Configuration

```properties
# src/main/resources/application.properties

# ─── Server ───────────────────────────────────────
server.port=8080

# ─── H2 Database ──────────────────────────────────
spring.datasource.url=jdbc:h2:file:./data/footballdb
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
spring.jpa.hibernate.ddl-auto=update

# ─── CSV Data Sources ─────────────────────────────
csv.data.paths=data/PL_04_05.csv,data/PL_05_06.csv,data/PL_06_07.csv,data/PL_07_08.csv,\
data/PL_08_09.csv,data/PL_09_10.csv,data/PL_10_11.csv,data/PL_11_12.csv,\
data/PL_12_13.csv,data/PL_13_14.csv,data/PL_14_15.csv,data/PL_15_16.csv,\
data/PL_16_17.csv,data/PL_17_18.csv,data/PL_18_19.csv,data/PL_19_20.csv,\
data/PL_20_21.csv,data/PL_21_22.csv,data/PL_22_23.csv,data/PL_23_24.csv,\
data/PL_24_25.csv,data/PL_25_26.csv

# ─── Weka Model ───────────────────────────────────
model.output.path=./data/match_predictor.model

# ─── Feature Engineering ──────────────────────────
feature.form.window=5
```

---

## 📁 CSV Format Expected

The ingestion service expects CSVs with these headers (case-sensitive):

**Required columns:**
- `Date`, `HomeTeam`, `AwayTeam`, `FTHG`, `FTAG`, `FTR`

**Optional columns (if present):**
- `HTHG`, `HTAG`, `HTR` (half-time)
- `HS`, `AS`, `HST`, `AST` (shots)
- `HC`, `AC` (corners)
- `HY`, `AY`, `HR`, `AR` (cards)

**Date formats supported:**
- `dd/MM/yy` (e.g., 01/08/18)
- `dd/MM/yyyy` (e.g., 01/08/2018)

Rows with empty `FTR` or unparsable dates are automatically skipped.

---

## 🗺️ Roadmap

- [x] CSV data ingestion (OpenCSV)
- [x] Feature engineering pipeline (25 features including form, H2H, goals, streaks)
- [x] Random Forest classifier (Weka)
- [x] REST API with Spring Boot
- [x] Model persistence (load on startup)
- [x] H2 database console
- [x] Comprehensive test suite (unit, integration, API, E2E)
- [x] Docker support with docker-compose
- [x] Live data from football-data.org API
- [x] Scheduled data updates (auto-retrain)
- [ ] Add xG features from Understat/FBref
- [ ] Upgrade to Gradient Boosting (SMILE library)
- [ ] Swagger/OpenAPI documentation

---

## 🧪 Testing

The project includes a comprehensive test suite with **50+ test cases** covering unit tests, integration tests, API tests, and end-to-end tests.

### Run all tests

```bash
mvn test
```

### Run specific test categories

```bash
# Unit tests only
mvn test -Dtest="*Test" -DfailIfNoTests=false

# Integration tests only
mvn test -Dtest="*IntegrationTest"

# API tests only
mvn test -Dtest="*ApiTest"

# End-to-End tests only
mvn test -Dtest="*E2ETest"

# Single test class
mvn test -Dtest=MatchTest
```

### Test Suite Overview

| Category        | Test Class                        | Tests | Description                    |
|:----------------|:----------------------------------|:-----:|:-------------------------------|
| **Unit**        | `MatchTest`                       | 11    | Entity methods (points, goals) |
| **Unit**        | `MatchFeaturesTest`               | 4     | Feature vector POJO            |
| **Unit**        | `PredictRequestTest`              | 4     | Request DTO                    |
| **Unit**        | `PredictResponseTest`             | 5     | Response DTO                   |
| **Unit**        | `FeatureEngineeringServiceTest`   | 5     | Feature computation (mocked)   |
| **Unit**        | `ModelTrainingServiceTest`        | 7     | ML training logic (mocked)     |
| **Integration** | `MatchRepositoryIntegrationTest`  | 12    | JPA queries with H2            |
| **API**         | `PredictionControllerApiTest`     | 11    | REST endpoints (MockMvc)       |
| **E2E**         | `FootballPredictionE2ETest`       | 8     | Full application flow          |
| **Context**     | `FootballPredictionApplicationTests` | 6  | Bean wiring verification       |

### Test Configuration

Tests use a separate profile (`application-test.properties`):

```properties
# In-memory H2 database for tests
spring.datasource.url=jdbc:h2:mem:testdb

# Create-drop for clean state
spring.jpa.hibernate.ddl-auto=create-drop

# Single CSV for faster tests
csv.data.paths=data/PL_23_24.csv

# Reduced logging
logging.level.com.app.footballprediction=WARN
```

### Test Coverage

| Component                 | Unit | Integration | API | E2E |
|:--------------------------|:----:|:-----------:|:---:|:---:|
| Match entity              | ✅   | -           | -   | -   |
| MatchFeatures             | ✅   | -           | -   | -   |
| DTOs                      | ✅   | -           | -   | -   |
| FeatureEngineeringService | ✅   | -           | -   | -   |
| ModelTrainingService      | ✅   | -           | -   | -   |
| MatchRepository           | -    | ✅          | -   | -   |
| PredictionController      | -    | -           | ✅  | ✅  |
| Full Application          | -    | -           | -   | ✅  |

---

## 📚 Documentation & Resources

- **[DESIGN.md](DESIGN.md)** — Comprehensive design document with class descriptions and method references
- **[IMPROVEMENTS.md](IMPROVEMENTS.md)** — Enhancement plan and implemented improvements
- **Premier League CSVs** — Historical match data (2004-2026, 22 seasons)
- [football-data.org](https://www.football-data.org) — Live match data API integration
- [Weka documentation](https://waikato.github.io/weka-wiki/) — ML library reference

---

## 📄 License

MIT License — feel free to use, modify, and distribute.

---

> Built with Java 21 + Spring Boot 4 + Weka | Premier League data 2004-2026

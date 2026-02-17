# ⚽ Football Match Outcome Predictor

A Spring Boot application that ingests historical Premier League CSV data and predicts match outcomes (Home Win / Draw / Away Win) using Machine Learning (Random Forest via Weka).

---

## 🧠 How It Works

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA FLOW ARCHITECTURE                          │
└─────────────────────────────────────────────────────────────────────────┘

    CSV Files (Premier League 2004-2025 | 21 Seasons | ~8000 matches)
    └── src/main/resources/data/
        ├── PL_04_05.csv ... PL_18_19.csv   (Historical data)
        ├── PL_19_20.csv ... PL_23_24.csv   (Recent seasons)
        └── PL_24_25.csv                     (Current season)
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

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.2 |
| ML Library | Weka 3.8.6 (Random Forest) |
| Data Source | CSV files (Premier League historical data) |
| Database | H2 (embedded, file-based) |
| CSV Parsing | OpenCSV 5.12.0 |
| Logging | Log4j2 with async Disruptor |
| Testing | JUnit 5, Mockito, AssertJ, MockMvc |
| Build Tool | Maven |

---

## 📋 Prerequisites

- Java 21+
- Maven 3.8+
- Premier League CSV data files (included in `src/main/resources/data/`)

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/football-prediction.git
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

| File | Description |
|------|-------------|
| `Dockerfile` | Multi-stage build (JDK for build, JRE for runtime) |
| `docker-compose.yml` | Container orchestration with volume persistence |
| `.dockerignore` | Excludes unnecessary files from build context |
| `application-docker.properties` | Docker-specific configuration |

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JAVA_OPTS` | JVM options | `-Xms256m -Xmx512m` |
| `FOOTBALL_API_KEY` | football-data.org API key | (your key) |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `docker` |

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
  ✓  Application ready                            
───────────────────────────────────────────────────
  Web UI:                                          
  http://localhost:8080     → Web Interface       
───────────────────────────────────────────────────
  Endpoints:                                       
  POST /api/predict       → predict a match       
  POST /api/model/train   → retrain the model     
  GET  /api/model/status  → check model status    
  GET  /api/teams         → list all teams        
  POST /api/data/reload   → re-ingest CSV files   
───────────────────────────────────────────────────
  Tools:                                           
  http://localhost:8080/h2-console  → view DB     
═══════════════════════════════════════════════════
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

### Screenshots:

```
┌────────────────────────────────────────────────────────────────┐
│  ⚽ Football Match Predictor                    [Model Ready]  │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  🎯 Make a Prediction                                          │
│  ┌──────────────┐        ┌──────────────┐                     │
│  │ Arsenal    ▼ │   VS   │ Chelsea    ▼ │                     │
│  └──────────────┘        └──────────────┘                     │
│              [ Predict Match ]                                 │
│                                                                │
├────────────────────────────────────────────────────────────────┤
│  📊 Prediction Results                                         │
│                                                                │
│         Arsenal  vs  Chelsea                                   │
│                                                                │
│         Predicted Outcome                                      │
│         ═══ HOME_WIN ═══                                       │
│              HIGH                                              │
│                                                                │
│  Home Win  ████████████████░░░░░░░░░  55%                     │
│  Draw      ████████░░░░░░░░░░░░░░░░░  25%                     │
│  Away Win  ██████░░░░░░░░░░░░░░░░░░░  20%                     │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

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

| Code | Competition | Status |
|------|-------------|--------|
| PL | 🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier League | ✅ Supported |

> **Note:** Currently only Premier League is supported as we only have historical data for this competition. Other leagues (La Liga, Bundesliga, Serie A, Ligue 1) may be added in the future.

---

## 📰 News API Integration (newsapi.org)

The application can fetch football news from [NewsAPI.org](https://newsapi.org/).

### Configuration

Get a free API key at: https://newsapi.org/register

Add to `application.properties`:
```properties
news.api.key=YOUR_NEWS_API_KEY
```

**Free tier:** 100 requests/day

### Get Premier League News

```http
GET /api/news/premier-league
```

### Get General Football News

```http
GET /api/news/football
```

### Get Team-Specific News

```http
GET /api/news/team?name=Arsenal
```

### Response Example

```json
{
  "status": "ok",
  "totalResults": 10,
  "articles": [
    {
      "source": { "name": "BBC Sport" },
      "title": "Premier League: Arsenal vs Chelsea preview",
      "description": "Match preview and team news...",
      "url": "https://...",
      "urlToImage": "https://...",
      "publishedAt": "2026-02-18T10:00:00Z"
    }
  ]
}

---

## 🧪 Features Used for Prediction

| # | Feature | Description | Calculation |
|---|---------|-------------|-------------|
| 0 | `homeFormPoints` | Points per game in last 5 home matches | W=3, D=1, L=0 |
| 1 | `awayFormPoints` | Points per game in last 5 away matches | W=3, D=1, L=0 |
| 2 | `homeGoalsScoredAvg` | Avg goals scored at home | Last 20 home matches |
| 3 | `homeGoalsConcededAvg` | Avg goals conceded at home | Last 20 home matches |
| 4 | `awayGoalsScoredAvg` | Avg goals scored away | Last 20 away matches |
| 5 | `awayGoalsConcededAvg` | Avg goals conceded away | Last 20 away matches |
| 6 | `homeTotalGoalsAvg` | Avg total goals in home team's matches | Last 5 matches |
| 7 | `awayTotalGoalsAvg` | Avg total goals in away team's matches | Last 5 matches |
| 8 | `h2hHomeWinRate` | Head-to-head home win rate | All historical H2H |
| 9 | `h2hDrawRate` | Head-to-head draw rate | All historical H2H |
| 10 | `h2hAwayWinRate` | Head-to-head away win rate | All historical H2H |
| 11 | `homeShotsOnTargetAvg` | Avg shots on target at home | Last 10 home matches |
| 12 | `awayShotsOnTargetAvg` | Avg shots on target away | Last 10 away matches |
| 13 | `homeCornersAvg` | Avg corners at home | Last 10 home matches |
| 14 | `awayCornersAvg` | Avg corners away | Last 10 away matches |

> ⚠️ **No data leakage:** Only pre-match knowable features are used. In-game stats from the match being predicted are never included.

---

## 🏗️ Project Structure

```
src/
├── main/
│   ├── java/com/app/footballprediction/
│   │   ├── FootballPredictionApplication.java  # Entry point + ApplicationRunner
│   │   ├── config/
│   │   │   ├── WekaModelConfig.java            # Loads saved model as @Bean
│   │   │   └── RequestLoggingFilter.java       # HTTP request logging
│   │   ├── controller/
│   │   │   └── PredictionController.java       # REST API endpoints
│   │   ├── dto/
│   │   │   ├── PredictRequest.java             # { homeTeam, awayTeam }
│   │   │   └── PredictResponse.java            # Prediction + probabilities
│   │   ├── featureengineering/
│   │   │   └── FeatureEngineeringService.java  # Computes ML features
│   │   ├── model/
│   │   │   ├── Match.java                      # JPA entity (@Entity, @Table)
│   │   │   └── MatchFeatures.java              # Feature vector POJO
│   │   ├── modeltraining/
│   │   │   └── ModelTrainingService.java       # Weka RF training + prediction
│   │   ├── repository/
│   │   │   └── MatchRepository.java            # JPA queries (form, H2H, etc.)
│   │   └── service/
│   │       ├── CsvIngestionService.java        # CSV parsing + batch insert
│   │       └── FootballDataService.java        # (API integration placeholder)
│   └── resources/
│       ├── application.properties              # Configuration
│       ├── log4j2.xml                          # Logging config
│       └── data/                               # Premier League CSVs
│           ├── PL_19_20.csv
│           ├── PL_20_21.csv
│           ├── PL_21_22.csv
│           ├── PL_22_23.csv
│           ├── PL_23_24.csv
│           └── PL_24_25.csv
└── test/
    └── java/com/app/footballprediction/
        └── FootballPredictionApplicationTests.java
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
4. Convert to Weka Instances (15 numeric features + 1 nominal label)
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

| Benchmark | Accuracy |
|-----------|----------|
| Naive baseline (always predict Home Win) | ~45% |
| This model (Random Forest) | ~53–56% |
| State of the art (with xG data) | ~58% |

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
csv.data.paths=data/PL_19_20.csv,data/PL_20_21.csv,data/PL_21_22.csv,data/PL_22_23.csv,data/PL_23_24.csv,data/PL_24_25.csv

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
- [x] Feature engineering pipeline (form, H2H, goals)
- [x] Random Forest classifier (Weka)
- [x] REST API with Spring Boot
- [x] Model persistence (load on startup)
- [x] H2 database console
- [x] Comprehensive test suite (unit, integration, API, E2E)
- [ ] Add xG features from Understat/FBref
- [ ] Live data from football-data.org API
- [ ] Upgrade to Gradient Boosting (SMILE library)
- [ ] Docker support
- [ ] Swagger/OpenAPI documentation
- [ ] Scheduled model retraining

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

| Category | Test Class | Tests | Description |
|----------|------------|-------|-------------|
| **Unit** | `MatchTest` | 11 | Entity methods (points, goals) |
| **Unit** | `MatchFeaturesTest` | 4 | Feature vector POJO |
| **Unit** | `PredictRequestTest` | 4 | Request DTO |
| **Unit** | `PredictResponseTest` | 5 | Response DTO |
| **Unit** | `FeatureEngineeringServiceTest` | 5 | Feature computation (mocked) |
| **Unit** | `ModelTrainingServiceTest` | 7 | ML training logic (mocked) |
| **Integration** | `MatchRepositoryIntegrationTest` | 12 | JPA queries with H2 |
| **API** | `PredictionControllerApiTest` | 11 | REST endpoints (MockMvc) |
| **E2E** | `FootballPredictionE2ETest` | 8 | Full application flow |
| **Context** | `FootballPredictionApplicationTests` | 6 | Bean wiring verification |

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

| Component | Unit | Integration | API | E2E |
|-----------|:----:|:-----------:|:---:|:---:|
| Match entity | ✅ | - | - | - |
| MatchFeatures | ✅ | - | - | - |
| DTOs | ✅ | - | - | - |
| FeatureEngineeringService | ✅ | - | - | - |
| ModelTrainingService | ✅ | - | - | - |
| MatchRepository | - | ✅ | - | - |
| PredictionController | - | - | ✅ | ✅ |
| Full Application | - | - | - | ✅ |

---

## 📚 Documentation & Resources

- **[DESIGN.md](DESIGN.md)** — Comprehensive design document with class descriptions and method references
- **Premier League CSVs** — Historical match data (2019-2025)
- [football-data.org](https://www.football-data.org) — Match data API (for future live integration)
- [Weka documentation](https://waikato.github.io/weka-wiki/) — ML library reference

---

## 📄 License

MIT License — feel free to use, modify, and distribute.

---

> Built with Java 21 + Spring Boot 4 + Weka | Premier League data 2019-2025

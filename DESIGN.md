# 📐 Design Document: Football Match Outcome Predictor

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Diagram](#architecture-diagram)
3. [Class Descriptions](#class-descriptions)
   - [Application Layer](#application-layer)
   - [Configuration Layer](#configuration-layer)
   - [Controller Layer](#controller-layer)
   - [Service Layer](#service-layer)
   - [Feature Engineering Layer](#feature-engineering-layer)
   - [Model Training Layer](#model-training-layer)
   - [Repository Layer](#repository-layer)
   - [Model Layer (Entities & DTOs)](#model-layer)
4. [Data Flow](#data-flow)
5. [Method Reference](#method-reference)

---

## System Overview

The Football Match Outcome Predictor is a Spring Boot application that:

1. **Ingests** historical Premier League match data from CSV files (22 seasons, 2004-2026)
2. **Stores** match records in an H2 embedded database
3. **Computes** 25 statistical features (form, goals, head-to-head, streaks, rest days) for each team
4. **Trains** a Random Forest classifier using the Weka ML library
5. **Predicts** match outcomes (Home Win / Draw / Away Win) via REST API

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  PredictionController                                                       │
│  ├── POST /api/predict        → Predict match outcome                       │
│  ├── POST /api/model/train    → Train/retrain model                         │
│  ├── GET  /api/model/status   → Check model readiness                       │
│  └── POST /api/data/reload    → Re-ingest CSV data                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                               SERVICE LAYER                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  CsvIngestionService          │  FeatureEngineeringService                  │
│  ├── ingestAll()              │  ├── buildFeaturesForTraining()             │
│  ├── ingestFile()             │  ├── buildFeaturesForPrediction()           │
│  └── parseRow()               │  └── calcFormPoints(), calcH2H...()         │
├───────────────────────────────┼─────────────────────────────────────────────┤
│  ModelTrainingService                                                       │
│  ├── trainAndEvaluate()       → Full ML pipeline                            │
│  ├── predict()                → Run inference                               │
│  ├── getPredictedLabel()      → Convert probabilities to label              │
│  └── isModelLoaded()          → Check model status                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              REPOSITORY LAYER                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  MatchRepository (JPA)                                                      │
│  ├── findByTeamBeforeDate()           → All matches for a team              │
│  ├── findHomeMatchesByTeamBeforeDate()→ Home matches only                   │
│  ├── findAwayMatchesByTeamBeforeDate()→ Away matches only                   │
│  ├── findH2HBeforeDate()              → Head-to-head history                │
│  ├── findAllByOrderByMatchDateAsc()   → Training dataset                    │
│  └── existsByMatchDateAndHomeTeamAndAwayTeam() → Duplicate check            │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                DATA LAYER                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  H2 Database (embedded)              │  File System                         │
│  └── matches table                   │  └── match_predictor.model           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Class Descriptions

### Application Layer

#### `FootballPredictionApplication`

**Package:** `com.app.footballprediction`

**Purpose:** Main entry point and startup orchestrator. Implements `ApplicationRunner` to execute initialization logic after Spring context loads.

| Method | Description |
|--------|-------------|
| `main(String[] args)` | Bootstrap method that launches the Spring Boot application using `SpringApplication.run()`. |
| `run(ApplicationArguments args)` | Executes after Spring context is fully loaded. Orchestrates: (1) CSV data ingestion, (2) Model loading or training. Ensures the application is ready to serve predictions. |
| `printBanner()` | Logs the application startup banner with project name and tech stack info. |
| `printReadyBanner()` | Logs available endpoints and tools (H2 console URL) when application is ready. |

---

### Configuration Layer

#### `WekaModelConfig`

**Package:** `com.app.footballprediction.config`

**Purpose:** Spring `@Configuration` class that loads pre-trained Weka model and schema from disk at startup. Provides beans for dependency injection into `ModelTrainingService`.

| Method | Description |
|--------|-------------|
| `trainedModel()` | **@Bean** — Loads the serialized `RandomForest` classifier from `model.output.path`. Returns `null` if file doesn't exist (triggers fresh training). |
| `trainingHeader()` | **@Bean** — Loads the Weka `Instances` schema (attribute definitions) saved alongside the model. Required to construct valid `Instance` objects for prediction. |

---

#### `RequestLoggingFilter`

**Package:** `com.app.footballprediction.config`

**Purpose:** Servlet filter that logs all incoming HTTP requests and outgoing responses with timing information.

| Method | Description |
|--------|-------------|
| `doFilter(ServletRequest, ServletResponse, FilterChain)` | Intercepts every HTTP request. Logs: (1) Incoming request method, URI, and client IP, (2) Outgoing response status and duration in milliseconds. |

---

### Controller Layer

#### `PredictionController`

**Package:** `com.app.footballprediction.controller`

**Purpose:** REST controller exposing all API endpoints for predictions, model management, and data operations.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `predict(PredictRequest)` | `POST /api/predict` | **Main prediction endpoint.** Validates input (homeTeam, awayTeam), builds features from historical data, runs model inference, and returns probabilities with confidence level. |
| `trainModel()` | `POST /api/model/train` | Triggers full model training pipeline. Loads all matches, builds features, trains Random Forest, evaluates on test set, saves model to disk. Returns evaluation report. |
| `modelStatus()` | `GET /api/model/status` | Returns JSON indicating whether the ML model is loaded and ready for predictions. |
| `reloadData()` | `POST /api/data/reload` | Re-ingests all configured CSV files. Useful after adding new season data. Skips duplicates automatically. |
| `labelToText(String)` | — | **Private helper.** Converts prediction code ("H"/"D"/"A") to human-readable text ("HOME_WIN"/"DRAW"/"AWAY_WIN"). |
| `getConfidence(double[])` | — | **Private helper.** Determines confidence level based on highest probability: HIGH (≥0.55), MEDIUM (≥0.45), LOW (<0.45). |
| `round(double)` | — | **Private helper.** Rounds double to 2 decimal places for clean JSON output. |

---

### Service Layer

#### `CsvIngestionService`

**Package:** `com.app.footballprediction.service`

**Purpose:** Reads Premier League CSV files from classpath and persists match data to the database. Handles parsing, validation, and duplicate detection.

| Method | Description |
|--------|-------------|
| `ingestAll()` | **Public API.** Iterates all CSV paths from `csv.data.paths` config, calls `ingestFile()` for each, logs total matches loaded. |
| `ingestFile(String)` | Reads a single CSV file from classpath. Parses headers, validates required columns, iterates rows, skips duplicates, saves new matches in batch. Returns count of new records. |
| `buildColumnIndex(String[])` | **Private.** Creates a `Map<String, Integer>` mapping column names to their indices for efficient row parsing. |
| `validateRequiredColumns(Map, String)` | **Private.** Ensures required columns (Date, HomeTeam, AwayTeam, FTHG, FTAG, FTR) exist. Throws `IllegalArgumentException` if missing. |
| `parseRow(String[], Map)` | **Private.** Converts a CSV row into a `Match` entity. Returns `null` for future fixtures, postponed matches, or rows with unparsable data. |
| `getString(String[], Map, String)` | **Private.** Safely extracts a trimmed string value from row by column name. Returns `null` if missing or empty. |
| `getInt(String[], Map, String)` | **Private.** Parses integer from column. Returns `null` if missing or not a valid number. |
| `parseDate(String)` | **Private.** Attempts to parse date string using "dd/MM/yy" then "dd/MM/yyyy" formats. Returns `null` on failure. |

---

#### `FootballDataService`

**Package:** `com.app.footballprediction.service`

**Purpose:** Placeholder service for future integration with external football data APIs (e.g., football-data.org).

| Method | Description |
|--------|-------------|
| *(none yet)* | Service is currently empty. Will implement live data fetching in future versions. |

---

### Feature Engineering Layer

#### `FeatureEngineeringService`

**Package:** `com.app.footballprediction.featureengineering`

**Purpose:** Computes statistical features for each match based on historical data. Transforms raw match records into ML-ready feature vectors.

| Method | Description |
|--------|-------------|
| `buildFeaturesForTraining(Match)` | **Public API.** Builds features using the match's own date as cutoff (prevents data leakage). Sets `actualResult` as the label for supervised learning. |
| `buildFeaturesForPrediction(String, String)` | **Public API.** Builds features using today's date as cutoff. No label set (used for inference). |
| `buildFeatures(String, String, LocalDate)` | **Private core.** Fetches historical data from repository and computes all 25 features: form, goals, H2H, shots, corners, streaks, rest days. |
| `calcFormPoints(List<Match>, String, int)` | **Private.** Calculates average points per game over last N matches. W=3, D=1, L=0. |
| `calcGoalsScoredAvg(List<Match>, String)` | **Private.** Average goals scored by team in last 20 matches. |
| `calcGoalsConcededAvg(List<Match>, String)` | **Private.** Average goals conceded by team in last 20 matches. |
| `calcTotalGoalsAvg(List<Match>, int)` | **Private.** Average total goals (both teams) per game. Captures "open" vs "defensive" play style. |
| `calcH2HWinRate(List<Match>, String)` | **Private.** Win percentage for a team in head-to-head history. Returns 0.33 (neutral prior) if no H2H exists. |
| `calcH2HDrawRate(List<Match>)` | **Private.** Draw percentage in head-to-head history. Returns 0.33 if no H2H exists. |
| `calcShotsOnTargetAvg(List<Match>, boolean)` | **Private.** Average shots on target over last 10 matches. `isHome` flag determines which column to read. |
| `calcCornersAvg(List<Match>, boolean)` | **Private.** Average corners per game over last 10 matches. |

---

### Model Training Layer

#### `ModelTrainingService`

**Package:** `com.app.footballprediction.modeltraining`

**Purpose:** Handles all ML operations: training, evaluation, prediction, and model persistence using Weka's Random Forest classifier.

| Method | Description |
|--------|-------------|
| `trainAndEvaluate()` | **Public API.** Full ML pipeline: (1) Load matches, (2) Build features, (3) 80/20 temporal split, (4) Train Random Forest (100 trees), (5) Evaluate on test set, (6) Save model. Returns evaluation report. |
| `predict(MatchFeatures)` | **Public API.** Runs inference on a single feature vector. Returns `double[3]` with probabilities: [P(HomeWin), P(Draw), P(AwayWin)]. Throws if model not loaded. |
| `getPredictedLabel(double[])` | **Public API.** Converts probability array to class label ("H"/"D"/"A") by selecting highest probability. |
| `isModelLoaded()` | **Public API.** Returns `true` if both `trainedModel` and `trainingHeader` are non-null. |
| `loadModelFromDisk()` | Manually loads model and schema from file. (Usually handled by `WekaModelConfig` at startup.) |
| `buildAttributes()` | **Private.** Defines Weka dataset schema: 25 numeric features + 1 nominal class label. Order must match index constants. |
| `toWekaInstances(List<MatchFeatures>, ArrayList<Attribute>, String)` | **Private.** Converts list of features into Weka `Instances` dataset for training/evaluation. |
| `toWekaInstance(MatchFeatures, Instances)` | **Private.** Converts single feature vector to Weka `Instance`. Uses `safe()` to handle NaN/Infinity. |
| `saveModel(RandomForest, Instances)` | **Private.** Serializes trained model and schema to disk using `ObjectOutputStream`. |
| `buildEvaluationReport(Evaluation, int, int)` | **Private.** Formats Weka evaluation results into a human-readable report with accuracy, per-class metrics, and confusion matrix. |
| `safe(double)` | **Private utility.** Returns 0.0 if input is NaN or Infinity, otherwise returns input unchanged. |

---

### Repository Layer

#### `MatchRepository`

**Package:** `com.app.footballprediction.repository`

**Purpose:** JPA repository interface providing database access for `Match` entities. Contains custom JPQL queries for feature engineering.

| Method | Description |
|--------|-------------|
| `findByTeamBeforeDate(String, LocalDate)` | Returns all matches (home or away) for a team before a given date, ordered by date descending. Used for general form calculation. |
| `findHomeMatchesByTeamBeforeDate(String, LocalDate)` | Returns only home matches for a team before a given date. Used for home-specific form and goal averages. |
| `findAwayMatchesByTeamBeforeDate(String, LocalDate)` | Returns only away matches for a team before a given date. Used for away-specific form and goal averages. |
| `findH2HBeforeDate(String, String, LocalDate)` | Returns head-to-head matches between two teams (in either direction) before a given date. Used for H2H statistics. |
| `findAllByOrderByMatchDateAsc()` | Returns all matches in chronological order. Used by training pipeline to ensure proper temporal split. |
| `existsByMatchDateAndHomeTeamAndAwayTeam(LocalDate, String, String)` | Duplicate detection for CSV ingestion. Returns `true` if match already exists. |
| `count()` | Inherited from `JpaRepository`. Returns total number of matches in database. |

---

### Model Layer

#### `Match` (JPA Entity)

**Package:** `com.app.footballprediction.model`

**Purpose:** JPA entity representing a single football match. Maps to the `matches` table in H2 database.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key (auto-generated) |
| `matchDate` | LocalDate | Date the match was played |
| `homeTeam` | String | Name of home team |
| `awayTeam` | String | Name of away team |
| `fullTimeHomeGoals` | Integer | Home team goals (FTHG) |
| `fullTimeAwayGoals` | Integer | Away team goals (FTAG) |
| `fullTimeResult` | String | Result code: "H" / "D" / "A" |
| `halfTimeHomeGoals` | Integer | Half-time home goals (optional) |
| `halfTimeAwayGoals` | Integer | Half-time away goals (optional) |
| `halfTimeResult` | String | Half-time result (optional) |
| `homeShots` | Integer | Total home shots (optional) |
| `awayShots` | Integer | Total away shots (optional) |
| `homeShotsOnTarget` | Integer | Home shots on target (optional) |
| `awayShotsOnTarget` | Integer | Away shots on target (optional) |
| `homeCorners` | Integer | Home corners (optional) |
| `awayCorners` | Integer | Away corners (optional) |
| `homeYellowCards` | Integer | Home yellow cards (optional) |
| `awayYellowCards` | Integer | Away yellow cards (optional) |
| `homeRedCards` | Integer | Home red cards (optional) |
| `awayRedCards` | Integer | Away red cards (optional) |

| Method | Description |
|--------|-------------|
| `getPointsForTeam(String)` | Returns points earned by the specified team: 3 for win, 1 for draw, 0 for loss. Case-insensitive. |
| `getGoalsScoredByTeam(String)` | Returns goals scored by the specified team in this match. |
| `getGoalsConcededByTeam(String)` | Returns goals conceded by the specified team in this match. |

---

#### `MatchFeatures` (POJO)

**Package:** `com.app.footballprediction.model`

**Purpose:** Data transfer object holding computed features for a match. Used as input to the ML model.

| Field | Type | Description |
|-------|------|-------------|
| `homeTeam` | String | Home team name (not a Weka feature) |
| `awayTeam` | String | Away team name (not a Weka feature) |
| `homeFormPoints` | double | Avg points per game in last 5 home matches |
| `awayFormPoints` | double | Avg points per game in last 5 away matches |
| `homeGoalsScoredAvg` | double | Avg goals scored at home |
| `homeGoalsConcededAvg` | double | Avg goals conceded at home |
| `awayGoalsScoredAvg` | double | Avg goals scored away |
| `awayGoalsConcededAvg` | double | Avg goals conceded away |
| `homeTotalGoalsAvg` | double | Avg total goals in home team's matches |
| `awayTotalGoalsAvg` | double | Avg total goals in away team's matches |
| `h2hHomeWinRate` | double | H2H win rate for home team |
| `h2hDrawRate` | double | H2H draw rate |
| `h2hAwayWinRate` | double | H2H win rate for away team |
| `homeShotsOnTargetAvg` | double | Avg shots on target at home |
| `awayShotsOnTargetAvg` | double | Avg shots on target away |
| `homeCornersAvg` | double | Avg corners at home |
| `awayCornersAvg` | double | Avg corners away |
| `actualResult` | String | Actual result ("H"/"D"/"A") — only set during training |

---

#### `PredictRequest` (DTO)

**Package:** `com.app.footballprediction.dto`

**Purpose:** Request body for `POST /api/predict` endpoint.

| Field | Type | Description |
|-------|------|-------------|
| `homeTeam` | String | Name of home team (must match CSV exactly) |
| `awayTeam` | String | Name of away team (must match CSV exactly) |

---

#### `PredictResponse` (DTO)

**Package:** `com.app.footballprediction.dto`

**Purpose:** Response body for `POST /api/predict` endpoint.

| Field | Type | Description |
|-------|------|-------------|
| `homeTeam` | String | Echo of requested home team |
| `awayTeam` | String | Echo of requested away team |
| `prediction` | String | Human-readable prediction: "HOME_WIN" / "DRAW" / "AWAY_WIN" |
| `predictionCode` | String | Code: "H" / "D" / "A" |
| `probHomeWin` | double | Probability of home win (0.0 - 1.0) |
| `probDraw` | double | Probability of draw (0.0 - 1.0) |
| `probAwayWin` | double | Probability of away win (0.0 - 1.0) |
| `confidence` | String | Confidence level: "HIGH" / "MEDIUM" / "LOW" |
| `features` | FeatureSummary | Nested object with key features used |

**Inner Class: `FeatureSummary`**

| Field | Type | Description |
|-------|------|-------------|
| `homeFormPoints` | double | Home team form |
| `awayFormPoints` | double | Away team form |
| `homeGoalsScoredAvg` | double | Home team attacking strength |
| `awayGoalsScoredAvg` | double | Away team attacking strength |
| `h2hHomeWinRate` | double | Historical H2H home win rate |
| `h2hDrawRate` | double | Historical H2H draw rate |
| `h2hAwayWinRate` | double | Historical H2H away win rate |

---

## Data Flow

### 1. CSV Ingestion Flow

```
CSV File → CsvIngestionService.ingestFile()
                    │
                    ├── Read headers → buildColumnIndex()
                    ├── Validate columns → validateRequiredColumns()
                    ├── For each row:
                    │       ├── parseRow() → Match entity
                    │       └── Check duplicate → existsByMatchDateAndHomeTeamAndAwayTeam()
                    │
                    └── matchRepository.saveAll() → H2 Database
```

### 2. Training Flow

```
POST /api/model/train → ModelTrainingService.trainAndEvaluate()
                                    │
                                    ├── matchRepository.findAllByOrderByMatchDateAsc()
                                    ├── For each match:
                                    │       └── featureEngineeringService.buildFeaturesForTraining()
                                    │
                                    ├── Temporal split (80/20)
                                    ├── Build Weka Instances
                                    ├── Train RandomForest (100 trees)
                                    ├── Evaluate on test set
                                    └── saveModel() → File System
```

### 3. Prediction Flow

```
POST /api/predict { homeTeam, awayTeam }
            │
            └── PredictionController.predict()
                        │
                        ├── Validate input
                        ├── featureEngineeringService.buildFeaturesForPrediction()
                        │           │
                        │           ├── matchRepository.findHomeMatchesByTeamBeforeDate()
                        │           ├── matchRepository.findAwayMatchesByTeamBeforeDate()
                        │           ├── matchRepository.findH2HBeforeDate()
                        │           └── Compute all 15 features
                        │
                        ├── modelTrainingService.predict(features)
                        │           │
                        │           └── RandomForest.distributionForInstance()
                        │
                        └── Build PredictResponse with probabilities
```

---

## Method Reference

### Quick Reference: All Public Methods

| Class | Method | Purpose |
|-------|--------|---------|
| `FootballPredictionApplication` | `main()` | Application entry point |
| `FootballPredictionApplication` | `run()` | Startup initialization |
| `WekaModelConfig` | `trainedModel()` | Load saved model as bean |
| `WekaModelConfig` | `trainingHeader()` | Load schema as bean |
| `RequestLoggingFilter` | `doFilter()` | Log HTTP requests/responses |
| `PredictionController` | `predict()` | Predict match outcome |
| `PredictionController` | `trainModel()` | Train ML model |
| `PredictionController` | `modelStatus()` | Check model readiness |
| `PredictionController` | `reloadData()` | Re-ingest CSV data |
| `CsvIngestionService` | `ingestAll()` | Ingest all configured CSVs |
| `CsvIngestionService` | `ingestFile()` | Ingest single CSV file |
| `FeatureEngineeringService` | `buildFeaturesForTraining()` | Build labeled features |
| `FeatureEngineeringService` | `buildFeaturesForPrediction()` | Build unlabeled features |
| `ModelTrainingService` | `trainAndEvaluate()` | Full training pipeline |
| `ModelTrainingService` | `predict()` | Run model inference |
| `ModelTrainingService` | `getPredictedLabel()` | Convert probs to label |
| `ModelTrainingService` | `isModelLoaded()` | Check model status |
| `MatchRepository` | `findByTeamBeforeDate()` | All matches for team |
| `MatchRepository` | `findHomeMatchesByTeamBeforeDate()` | Home matches only |
| `MatchRepository` | `findAwayMatchesByTeamBeforeDate()` | Away matches only |
| `MatchRepository` | `findH2HBeforeDate()` | Head-to-head history |
| `MatchRepository` | `findAllByOrderByMatchDateAsc()` | All matches ordered |
| `MatchRepository` | `existsByMatchDateAndHomeTeamAndAwayTeam()` | Duplicate check |
| `Match` | `getPointsForTeam()` | Calculate points earned |
| `Match` | `getGoalsScoredByTeam()` | Get goals scored |
| `Match` | `getGoalsConcededByTeam()` | Get goals conceded |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-02-17 | Initial design document |

---

> **Document maintained by:** Development Team  
> **Last updated:** February 17, 2026


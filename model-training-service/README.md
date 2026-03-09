# Model Training Service

> **Part of the [Football Prediction Platform](../README.md)** - Dedicated microservice for ML model training and evaluation.

---

## Module Overview

### Purpose
The `model-training-service` is a **dedicated microservice** for training, evaluating, and managing the machine learning models used in the Football Prediction Platform. It operates independently from the main application, allowing model training to run without impacting prediction service availability.

### Scope within the System

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        SYSTEM ARCHITECTURE                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌───────────────────────────────┐                                      │
│  │    Main Application (8080)    │                                      │
│  │    • REST APIs                │                                      │
│  │    • Web UI                   │                                      │
│  │    • Model Consumption        │◄─────────────────┐                   │
│  └───────────────────────────────┘                  │                   │
│                                                     │                   │
│  ┌───────────────────────────────┐         ┌────────┴────────┐          │
│  │   Training Service (8081)     │  ◄──    │ Shared Storage  │          │
│  │   • Model Training            │──────►  │ • H2 Database   │          │
│  │   • Model Evaluation          │         │ • ML Model File │          │
│  │   • Scheduled Retraining      │         └─────────────────┘          │
│  │   ◄── THIS MODULE             │                                      │
│  └───────────────────────────────┘                                      │
│                                                                         │
│  ┌───────────────────────────────┐                                      │
│  │    Common Module (Shared)     │                                      │
│  │    • Entities, Repositories   │                                      │
│  └───────────────────────────────┘                                      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Related Documentation:**
- [Main Platform README](../README.md)
- [Main Application](../football-prediction-app/README.md)
- [Common Module](../football-prediction-common/README.md)
- [Frontend Components](../frontend/README.md)

---

## Table of Contents

- [Responsibilities](#responsibilities)
- [Architecture](#architecture)
- [ML Model Architecture](#ml-model-architecture)
- [Training Pipeline](#training-pipeline)
- [Feature Vector](#feature-vector)
- [API Documentation](#api-documentation)
- [Scheduled Retraining](#scheduled-retraining)
- [Performance Design](#performance-design)
- [Configuration](#configuration)
- [Testing Strategy](#testing-strategy)

---

## Responsibilities

### 1. Model Training
- Train stacked ensemble classifier on historical match data
- Build 25-feature vectors for each training sample
- Perform temporal train/test split (80/20)
- Save trained model to shared storage

### 2. Model Evaluation
- Evaluate model on held-out test set
- Generate accuracy, precision, recall, F1-score metrics
- Produce confusion matrix
- Calculate per-class performance breakdown

### 3. Model Management
- Persist trained models to disk
- Provide model metadata (size, last modified, path)
- Support model info queries via REST API

### 4. Scheduled Retraining
- Automatic retraining on 1st and 15th of each month (3:00 AM)
- Configurable via cron expression
- No manual intervention required

---

## Architecture

### Package Structure

```
com.app.modeltraining/
├── ModelTrainingServiceApplication.java  # Spring Boot entry point
│
├── controller/
│   └── ModelTrainingController.java      # REST API (3 endpoints)
│
├── service/
│   ├── ModelTrainingService.java         # Core training logic
│   └── FeatureEngineeringService.java    # Feature calculation (delegates to common)
│
├── scheduler/
│   └── ModelTrainingScheduler.java       # Cron-based retraining
│
└── dto/
    ├── TrainingResponse.java             # Training API response
    ├── TestResponse.java                 # Testing API response
    └── ModelInfoResponse.java            # Model info API response
```

> **Note**: Entity and repository classes are provided by the shared `football-prediction-common` module.

---

## ML Model Architecture

### Stacked Ensemble Model

```
┌─────────────────────────────────────────────────────────────────┐
│                     STACKED ENSEMBLE MODEL                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Input: MatchFeatures (25 features)                            │
│                                                                 │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │              BASE CLASSIFIERS (Level 1)                 │   │
│   │                                                         │   │
│   │   ┌─────────────────┐     ┌─────────────────┐           │   │
│   │   │  RandomForest   │     │   AdaBoostM1    │           │   │
│   │   │  • 100 trees    │     │  • 100 iters    │           │   │
│   │   │  • 5 features   │     │  • REPTree base │           │   │
│   │   │  • Seed: 42     │     │                 │           │   │
│   │   └────────┬────────┘     └────────┬────────┘           │   │
│   │            │                       │                    │   │
│   │            └───────────┬───────────┘                    │   │
│   │                        ▼                                │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │              META CLASSIFIER (Level 2)                  │   │
│   │                                                         │   │
│   │              ┌─────────────────────┐                    │   │
│   │              │ Logistic Regression │                    │   │
│   │              │ (combines outputs)  │                    │   │
│   │              └──────────┬──────────┘                    │   │
│   │                         │                               │   │
│   └─────────────────────────┼───────────────────────────────┘   │
│                             ▼                                   │
│                                                                 │
│   Output: Probabilities [P(H), P(D), P(A)]                      │
│   Label: "H" (Home Win) | "D" (Draw) | "A" (Away Win)           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### RandomForest Configuration

```java
RandomForest rf = new RandomForest();
rf.setNumIterations(100);  // 100 decision trees
rf.setNumFeatures(5);       // sqrt(25) ≈ 5 features per split
rf.setSeed(42);             // Reproducible results
rf.buildClassifier(trainData);
```

---

## Training Pipeline

### Data Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         TRAINING PIPELINE                                │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Step 1: Data Loading                                                    │
│  ┌────────────────────┐                                                  │
│  │ MatchRepository    │                                                  │
│  │ .findAllByOrder    │──► ~12,500 matches (chronological)               │
│  │  ByMatchDateAsc()  │                                                  │
│  └────────────────────┘                                                  │
│            │                                                             │
│            ▼                                                             │
│  Step 2: Feature Engineering                                             │
│  ┌────────────────────────────────────────┐                              │
│  │ FeatureEngineeringService              │                              │
│  │ .buildFeaturesForTraining(match)       │                              │
│  │                                        │                              │
│  │ For each match:                        │                              │
│  │ • Use match_date as temporal cutoff    │                              │
│  │ • Calculate 25 features from history   │                              │
│  │ • Set actualResult as label            │                              │
│  └────────────────────────────────────────┘                              │
│            │                                                             │
│            ▼                                                             │
│  Step 3: Temporal Split                                                  │
│  ┌──────────────────────────────────────────────────────────┐            │
│  │                                                          │            │
│  │   ◄─────────── 80% Train ───────────►│◄── 20% Test ──►   │            │
│  │   [Older matches]                     │ [Recent matches] │            │
│  │                                       │                  │            │
│  │   No future data leakage!             │ Held-out eval    │            │
│  │                                                          │            │
│  └──────────────────────────────────────────────────────────┘            │
│            │                                                             │
│            ▼                                                             │
│  Step 4: Model Training                                                  │
│  ┌────────────────────────────────────────┐                              │
│  │ StackedEnsemble.buildClassifier()      │                              │
│  └────────────────────────────────────────┘                              │
│            │                                                             │
│            ▼                                                             │
│  Step 5: Evaluation & Persistence                                        │
│  ┌────────────────────────────────────────┐                              │
│  │ Evaluation.evaluateModel(model, test)  │──► Metrics                   │
│  │ ObjectOutputStream.writeObject(model)  │──► predictor.model           │
│  └────────────────────────────────────────┘                              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Temporal Split Strategy

```java
// Critical: Chronological split prevents future data leakage
int splitIdx = (int) (allFeatures.size() * 0.8);  // 80% train

// Train set: older matches (indices 0 to splitIdx-1)
List<MatchFeatures> trainSet = allFeatures.subList(0, splitIdx);

// Test set: recent matches (indices splitIdx to end)
List<MatchFeatures> testSet = allFeatures.subList(splitIdx, allFeatures.size());
```

**Why temporal split?**
- Random split would leak future information into training
- Model would "memorize" team states that haven't occurred yet
- Temporal split mimics real-world prediction scenario

---

## Feature Vector

Each training sample has 25 numeric features + 1 class label:

| Index | Feature | Type | Description |
|-------|---------|------|-------------|
| 0 | homeFormPoints | Numeric | Avg points/game (last 5 home) |
| 1 | awayFormPoints | Numeric | Avg points/game (last 5 away) |
| 2 | homeGoalsScoredAvg | Numeric | Avg goals scored at home |
| 3 | homeGoalsConcededAvg | Numeric | Avg goals conceded at home |
| 4 | awayGoalsScoredAvg | Numeric | Avg goals scored away |
| 5 | awayGoalsConcededAvg | Numeric | Avg goals conceded away |
| 6 | homeTotalGoalsAvg | Numeric | Avg total goals/game |
| 7 | awayTotalGoalsAvg | Numeric | Avg total goals/game |
| 8 | h2hHomeWinRate | Numeric | H2H home win rate |
| 9 | h2hDrawRate | Numeric | H2H draw rate |
| 10 | h2hAwayWinRate | Numeric | H2H away win rate |
| 11 | homeShotsOnTargetAvg | Numeric | Avg shots on target (home) |
| 12 | awayShotsOnTargetAvg | Numeric | Avg shots on target (away) |
| 13 | homeCornersAvg | Numeric | Avg corners (home) |
| 14 | awayCornersAvg | Numeric | Avg corners (away) |
| 15 | homeGoalDifference | Numeric | Recent goal difference |
| 16 | awayGoalDifference | Numeric | Recent goal difference |
| 17 | homeOverallFormPoints | Numeric | Overall form (all matches) |
| 18 | awayOverallFormPoints | Numeric | Overall form (all matches) |
| 19 | homeWinStreak | Numeric | Consecutive wins |
| 20 | awayWinStreak | Numeric | Consecutive wins |
| 21 | homeUnbeatenStreak | Numeric | Consecutive non-losses |
| 22 | awayUnbeatenStreak | Numeric | Consecutive non-losses |
| 23 | homeDaysRest | Numeric | Days since last match |
| 24 | awayDaysRest | Numeric | Days since last match |
| 25 | result | Nominal | Class: {H, D, A} |

> For detailed feature engineering documentation, see [Common Module](../football-prediction-common/README.md#feature-engineering-pipeline).

---

## API Documentation

### Base URL
```
http://localhost:8081/api/training
```

### Endpoints

#### POST `/train`
Train a new model from all historical data.

**Response:**
```json
{
  "success": true,
  "message": "Model training completed successfully",
  "report": "══════════════════════════════════════════\n   MATCH OUTCOME PREDICTOR — TRAINING   \n══════════════════════════════════════════\n  Train set : 6000 matches\n  Test set  : 1500 matches (most recent)\n  Accuracy  : 62.3%\n  Kappa     : 0.3842\n  F-Measure : 0.6152\n...",
  "trainingTimeMs": 8432
}
```

#### POST `/test`
Evaluate the current model on the test set.

**Response:**
```json
{
  "success": true,
  "message": "Model testing completed successfully",
  "report": "══════════════════════════════════════════\n   MATCH OUTCOME PREDICTOR — TESTING   \n══════════════════════════════════════════\n  Test set  : 1500 matches\n  Accuracy  : 61.8%\n..."
}
```

#### GET `/model-info`
Get metadata about the current model.

**Response:**
```json
{
  "success": true,
  "modelInfo": {
    "modelExists": true,
    "modelPath": "../data/match_predictor.model",
    "modelSize": 1547832,
    "lastModified": "2026-02-15T03:00:00",
    "totalMatches": 8420
  }
}
```

---

## Scheduled Retraining

### Cron Configuration

```java
@Scheduled(cron = "${training.schedule.cron:0 0 3 1,15 * *}")
public void scheduledRetraining() {
    if (enabled) {
        log.info("Starting scheduled model retraining...");
        String report = modelTrainingService.trainModel();
        log.info("Scheduled retraining complete: {}", report);
    }
}
```

### Schedule Explanation

```
0 0 3 1,15 * *
│ │ │  │   │ │
│ │ │  │   │ └── Day of week (any)
│ │ │  │   └──── Month (any)
│ │ │  └──────── Day of month (1st and 15th)
│ │ └─────────── Hour (3 AM)
│ └───────────── Minute (0)
└─────────────── Second (0)
```

---

## Performance Design

### Training Time Optimization

| Optimization | Implementation | Impact |
|--------------|----------------|--------|
| Chronological ordering | Pre-sorted query | O(1) access |
| Feature caching | In-memory list | Single DB round-trip |
| Skipping invalid samples | Early continue | Reduces dataset size |
| Parallel tree building | RandomForest internal | Multi-core utilization |

### Typical Performance Metrics

| Metric | Value |
|--------|-------|
| Training time | 5-10 seconds |
| Dataset size | ~12,500 matches |
| Feature vectors | ~11,000 (after filtering) |
| Model file size | 1-2 MB |
| Memory usage | 512 MB - 1 GB |
| Target accuracy | ~62% |

---

## Configuration

### Application Properties

```properties
# Server
server.port=8081

# Database (shared with main app)
spring.datasource.url=jdbc:h2:file:../data/footballdb

# Model Output
model.output.path=../data/match_predictor.model

# Training Configuration
model.training.min-matches=100
model.training.train-split=0.8

# Cross-validation
model.crossvalidation.enabled=true
model.crossvalidation.folds=10

# Scheduled Retraining (1st & 15th @ 3 AM)
training.schedule.enabled=true
training.schedule.cron=0 0 3 1,15 * *
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MODEL_OUTPUT_PATH` | `../data/match_predictor.model` | Model file location |
| `SPRING_PROFILES_ACTIVE` | `default` | Active Spring profile |

---

## Testing Strategy

### Unit Tests

| Test Class | Coverage |
|------------|----------|
| `ModelTrainingServiceTest` | Training, testing, model info |
| `FeatureEngineeringServiceTest` | Feature calculation |
| `MatchRepositoryTest` | Query correctness |

### Test Scenarios

```java
@Test
void trainsModelSuccessfully() {
    String report = service.trainModel();
    assertThat(report).contains("Accuracy");
    assertThat(new File(modelPath)).exists();
}

@Test
void throwsExceptionForInsufficientData() {
    // Given: Database has < 100 matches
    assertThrows(IllegalStateException.class, () -> service.trainModel());
}

@Test
void ensuresNoFutureDataLeakage() {
    // Verify temporal split integrity
}
```

---

## Metrics

| Metric | Value |
|--------|-------|
| Lines of Code | ~800 |
| Service Classes | 2 |
| Controller Endpoints | 3 |
| ML Features | 25 |
| Target Accuracy | ~62% |

---

**[← Back to Main README](../README.md)**

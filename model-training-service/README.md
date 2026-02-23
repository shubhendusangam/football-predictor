# Model Training Service

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

---

## Responsibilities

### 1. Model Training
- Train RandomForest classifier on historical match data
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
│   ├── ModelTrainingService.java         # Core training logic (354 lines)
│   └── FeatureEngineeringService.java    # Feature calculation (local copy)
│
├── model/
│   ├── Match.java                        # Match entity (local copy)
│   └── MatchFeatures.java                # Feature DTO (local copy)
│
├── repository/
│   └── MatchRepository.java              # Data access (local copy)
│
├── scheduler/
│   └── TrainingScheduler.java            # Cron-based retraining
│
└── dto/
    └── TrainingResponse.java             # API response DTOs
```

### ML Model Architecture

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

### Training Pipeline Data Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         TRAINING PIPELINE                                │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Step 1: Data Loading                                                    │
│  ┌────────────────────┐                                                  │
│  │ MatchRepository    │                                                  │
│  │ .findAllByOrder    │──► ~8,000 matches (chronological)                │
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
│  │ RandomForest.buildClassifier(trainData)│                              │
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

---

## Core Business Logic

### Feature Vector Construction (25 Features)

Each training sample is converted to a Weka `Instance` with 25 numeric features + 1 class label:

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

### RandomForest Configuration

```java
RandomForest rf = new RandomForest();
rf.setNumIterations(100);  // 100 decision trees
rf.setNumFeatures(5);       // sqrt(25) ≈ 5 features per split
rf.setSeed(42);             // Reproducible results
rf.buildClassifier(trainData);
```

### Model Persistence Format

```java
// Model file contains both classifier and training header
try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile))) {
    oos.writeObject(model);       // RandomForest classifier
    oos.writeObject(header);      // Weka Instances header (for feature schema)
}
```

---

## Data Dependencies

### Database Access

| Table | Usage | Query Pattern |
|-------|-------|---------------|
| `matches` | Training data source | `findAllByOrderByMatchDateAsc()` |

### Shared Storage

| Resource | Path | Description |
|----------|------|-------------|
| H2 Database | `../data/footballdb.mv.db` | Shared with main app |
| ML Model | `../data/match_predictor.model` | Trained model file |

### External Services

- **None**: This service has no external API dependencies
- Training is fully offline using historical data

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
| Dataset size | ~8,000 matches |
| Feature vectors | ~7,000 (after filtering) |
| Model file size | 1-2 MB |
| Memory usage | 512 MB - 1 GB |

### Avoidance of N+1

```java
// BAD: Feature engineering per match triggers N+1
for (Match match : allMatches) {
    MatchFeatures features = featureService.buildFeatures(match);  // Multiple queries
}

// GOOD: Feature engineering uses batch queries internally
List<Match> homeMatches = matchRepository.findHomeMatchesByTeamBeforeDate(team, date);
List<Match> awayMatches = matchRepository.findAwayMatchesByTeamBeforeDate(team, date);
// All data loaded upfront, then processed in-memory
```

---

## Edge Case Handling

### Minimum Data Requirement

```java
if (allMatches.size() < minMatches) {  // Default: 100
    throw new IllegalStateException(
        "Not enough data to train. Need at least " + minMatches + 
        " matches, found: " + allMatches.size());
}
```

### Invalid Feature Vectors

```java
// Skip matches with insufficient history
if (features.getHomeFormPoints() == 0.0 && features.getHomeGoalsScoredAvg() == 0.0) {
    skipped++;
    continue;  // New teams without history
}
```

### Missing Model File

```java
File modelFile = new File(modelOutputPath);
if (!modelFile.exists()) {
    throw new IllegalStateException("Model file not found. Please train the model first.");
}
```

### Safe Numeric Values

```java
// PredictionUtils.safe() handles null/NaN/Infinity
inst.setValue(IDX_HOME_FORM, PredictionUtils.safe(f.getHomeFormPoints()));
```

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

## Testing Strategy

### Unit Tests

| Test Class | Coverage |
|------------|----------|
| `ModelTrainingServiceTest` | `trainModel()`, `testModel()`, `getModelInfo()` |
| `FeatureEngineeringServiceTest` | Feature calculation accuracy |
| `MatchRepositoryTest` | Query correctness |

### Test Scenarios

```java
// 1. Training with sufficient data
@Test
void trainsModelSuccessfully() {
    String report = service.trainModel();
    assertThat(report).contains("Accuracy");
    assertThat(new File(modelPath)).exists();
}

// 2. Training with insufficient data
@Test
void throwsExceptionForInsufficientData() {
    // Given: Database has < 100 matches
    assertThrows(IllegalStateException.class, () -> service.trainModel());
}

// 3. Testing without trained model
@Test
void throwsExceptionWhenModelMissing() {
    Files.deleteIfExists(Path.of(modelPath));
    assertThrows(IllegalStateException.class, () -> service.testModel());
}

// 4. Temporal split validation
@Test
void ensuresNoFutureDataLeakage() {
    // Train on matches before 2024-01-01
    // Test on matches after 2024-01-01
    // Verify no overlap
}
```

### Integration Tests

```java
@SpringBootTest
class ModelTrainingIntegrationTest {
    
    @Test
    void fullTrainingPipeline() {
        // Train
        String trainReport = service.trainModel();
        assertThat(trainReport).contains("Accuracy");
        
        // Test
        String testReport = service.testModel();
        assertThat(testReport).contains("Accuracy");
        
        // Model info
        Map<String, Object> info = service.getModelInfo();
        assertThat(info.get("modelExists")).isEqualTo(true);
    }
}
```

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

## Future Enhancements

| Enhancement | Description | Priority |
|-------------|-------------|----------|
| Hyperparameter Tuning | Grid search for optimal RF parameters | High |
| Deep Learning | LSTM models for sequence prediction | Medium |
| Model Versioning | Keep history of trained models | Medium |
| A/B Testing | Compare model versions in production | Low |
| Distributed Training | Spark MLlib for larger datasets | Low |

---

## Metrics

| Metric | Value |
|--------|-------|
| Lines of Code | ~800 |
| Service Classes | 2 |
| Controller Endpoints | 3 |
| ML Features | 25 |
| Target Accuracy | ~62% |

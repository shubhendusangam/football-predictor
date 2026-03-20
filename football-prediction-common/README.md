# Football Prediction Common Module

> **Part of the [Football Prediction Platform](../README.md)** - Shared foundation layer providing entities, repositories, services, and feature engineering.

---

## Module Overview

### Purpose
The `football-prediction-common` module serves as the **shared foundation layer** for the Football Prediction Platform. It provides core domain entities, data access repositories, feature engineering services, Elo rating calculations, ingestion event infrastructure, and utility functions that are consumed by both the main application (`football-prediction-app`) and the model training service (`model-training-service`).

### Scope within the System
```
┌─────────────────────────────────────────────────────────────────┐
│                     System Architecture                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────┐         ┌───────────────────┐            │
│  │ football-         │         │ model-training-   │            │
│  │ prediction-app    │         │ service           │            │
│  │ (Port 8080)       │         │ (Port 8081)       │            │
│  └─────────┬─────────┘         └─────────┬─────────┘            │
│            │                             │                      │
│            └──────────┬──────────────────┘                      │
│                       │                                         │
│                       ▼                                         │
│            ┌─────────────────────┐                              │
│            │ football-prediction-│  ◄── THIS MODULE             │
│            │ common              │                              │
│            │ • Entities (15)     │                              │
│            │ • Repositories (13) │                              │
│            │ • Services (4)      │                              │
│            │ • Utilities (3)     │                              │
│            │ • Ingestion Events  │                              │
│            │ • Weka Integration  │                              │
│            └─────────────────────┘                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Related Documentation:**
- [Main Platform README](../README.md)
- [Main Application](../football-prediction-app/README.md)
- [Model Training Service](../model-training-service/README.md)

---

## Table of Contents

- [Responsibilities](#responsibilities)
- [Architecture](#architecture)
- [Package Structure](#package-structure)
- [Domain Entities](#domain-entities)
- [Feature Engineering Pipeline](#feature-engineering-pipeline)
- [Elo Rating System](#elo-rating-system)
- [Repository Layer](#repository-layer)
- [Ingestion Infrastructure](#ingestion-infrastructure)
- [Data Dependencies](#data-dependencies)
- [Performance Design](#performance-design)
- [Edge Case Handling](#edge-case-handling)
- [Testing Strategy](#testing-strategy)

---

## Responsibilities

### 1. Domain Entity Management
- Define JPA entities for core domain objects (15 entities)
- Enforce database constraints and indexing
- Provide entity-level business methods

### 2. Data Access Layer
- Spring Data JPA repositories for all entities (13 repositories)
- Temporal queries with date-based filtering
- Season-scoped queries for insights engine
- Head-to-head (H2H) historical queries

### 3. Feature Engineering
- Calculate 47 ML features for match prediction (10 phases)
- Support training mode (with labels) and prediction mode (without labels)
- Implement temporal cutoffs to prevent data leakage

### 4. Elo Rating System
- Dynamic Elo rating calculation and updates
- Season-wise Elo tracking
- Elo-based probability adjustments for predictions

### 5. League Position Service
- Real-time league position lookups
- Season-scoped standings queries

### 6. Ingestion Infrastructure
- Canonical data mapping for multiple data sources
- Event-driven architecture (ingestion completed, match updated, cache invalidation)
- Internal DTOs for data normalization
- Data provider interfaces for extensibility

### 7. Weka ML Integration
- Weka schema building for ML model training
- Feature-to-Weka instance conversion

### 8. Utility Functions
- Prediction utilities (rounding, confidence calculation)
- Team name normalization across data sources
- Feature drift monitoring
- Safe value handling for null protection
- Label-to-text conversion

---

## Architecture

### Package Structure

```
com.app.common/
├── model/                           # JPA Entities (15)
│   ├── Match.java                   # Core match data with statistics
│   ├── Team.java                    # Team metadata with logos
│   ├── League.java                  # League configuration
│   ├── LeagueStanding.java         # Season standings
│   ├── Prediction.java             # Prediction tracking
│   ├── PredictionEvaluation.java   # Prediction evaluation metrics
│   ├── MatchFeatures.java          # ML feature DTO (47 features, 10 phases)
│   ├── ModelAccuracy.java          # Model accuracy tracking
│   ├── ModelTrainingHistory.java   # Training run history
│   ├── SeasonTeamStats.java        # Per-season team statistics
│   ├── PlayerAvailability.java     # Player injury/suspension tracking
│   ├── PoissonParameters.java      # Dixon-Coles Poisson model parameters
│   ├── SyncStatusEntry.java        # Data sync status tracking
│   ├── AdminAuditLog.java          # Admin action logging
│   └── SystemSettings.java         # Application settings
│
├── dto/                             # Data Transfer Objects
│   └── ShotQualityDTO.java         # Shot quality metrics DTO
│
├── repository/                      # Spring Data JPA (13)
│   ├── MatchRepository.java                # Match queries
│   ├── TeamRepository.java                 # Team queries
│   ├── LeagueRepository.java               # League queries
│   ├── LeagueStandingRepository.java       # Standings queries
│   ├── PredictionRepository.java           # Prediction tracking
│   ├── PredictionEvaluationRepository.java # Evaluation queries
│   ├── ModelAccuracyRepository.java        # Accuracy history
│   ├── ModelTrainingHistoryRepository.java # Training history
│   ├── SeasonTeamStatsRepository.java      # Season team stats
│   ├── PlayerAvailabilityRepository.java   # Player availability queries
│   ├── SyncStatusEntryRepository.java      # Sync status queries
│   ├── AdminAuditLogRepository.java        # Audit queries
│   └── SystemSettingsRepository.java       # Settings queries
│
├── service/                         # Shared Business Logic (4)
│   ├── FeatureEngineeringService.java  # 47-feature ML pipeline (10 phases)
│   ├── EloRatingService.java           # Elo rating calculations
│   ├── LeaguePositionService.java      # League position lookups
│   └── MotivationService.java          # Motivation level calculations
│
├── ingestion/                       # Data Ingestion Infrastructure
│   ├── dto/
│   │   ├── InternalMatchDto.java       # Canonical match DTO
│   │   └── InternalStandingDto.java    # Canonical standing DTO
│   ├── event/
│   │   ├── CacheInvalidationEvent.java # Cache invalidation trigger
│   │   ├── IngestionCompletedEvent.java # Ingestion complete signal
│   │   ├── MatchUpdatedEvent.java      # Match data updated event
│   │   └── StatsRefreshEvent.java      # Stats refresh trigger
│   ├── mapper/
│   │   └── CanonicalMapper.java        # CSV/API field mapping
│   └── provider/
│       ├── MatchDataProvider.java      # Match data provider interface
│       └── StandingsDataProvider.java  # Standings provider interface
│
├── weka/                            # Weka ML Integration
│   └── WekaSchemaBuilder.java       # Weka ARFF schema builder
│
└── util/                            # Utilities (3)
    ├── PredictionUtils.java         # Prediction helper functions
    ├── TeamNameNormalizer.java      # Team name normalization
    └── FeatureDriftMonitor.java     # Feature distribution monitoring
```

### Data Flow

```
                  External Data (CSV/API/ESPN)
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Ingestion Layer                           │
│  ┌──────────────────────┐  ┌────────────────────────────┐   │
│  │ CanonicalMapper      │  │ MatchDataProvider (iface)  │   │
│  │ • CSV field mapping  │  │ StandingsDataProvider      │   │
│  └──────────┬───────────┘  └────────────┬───────────────┘   │
│             └──────────┬────────────────┘                    │
│                        ▼                                     │
│              InternalMatchDto / InternalStandingDto           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Repository Layer                         │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │ MatchRepository │  │ TeamRepository  │  + 9 more         │
│  └────────┬────────┘  └────────┬────────┘                   │
│           │                    │                            │
│           └────────┬───────────┘                            │
│                    ▼                                        │
│         ┌─────────────────────────────┐                     │
│         │ FeatureEngineeringService   │                     │
│         │ • buildFeaturesForTraining()│                     │
│         │ • buildFeaturesForPrediction│                     │
│         └──────────────┬──────────────┘                     │
│                        │                                    │
│         ┌──────────────┼──────────────┐                     │
│         ▼              ▼              ▼                     │
│  EloRatingService  WekaSchema   MatchFeatures DTO           │
│  • Elo calculation  Builder     (47 features, 10 phases)    │
│  • Rating updates                                           │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
          Consumed by App / Training Service
                         │
                         ▼
              ┌──────────────────────┐
              │  Event Infrastructure │
              │  • IngestionCompleted │
              │  • MatchUpdated       │
              │  • CacheInvalidation  │
              │  • StatsRefresh       │
              └──────────────────────┘
```

---

## Domain Entities

### Match Entity

Core entity representing a historical or upcoming match.

```java
@Entity
@Table(name = "matches")
public class Match {
    @Id @GeneratedValue
    private Long id;

    private LocalDate matchDate;
    private String homeTeam;
    private String awayTeam;
    private String season;
    private String referee;

    private Integer fullTimeHomeGoals;
    private Integer fullTimeAwayGoals;
    private String fullTimeResult;  // H, D, A

    // Match statistics
    private Integer homeShots, awayShots;
    private Integer homeShotsOnTarget, awayShotsOnTarget;
    private Integer homeCorners, awayCorners;
    private Integer homeFouls, awayFouls;
    private Integer homeYellowCards, awayYellowCards;
    private Integer homeRedCards, awayRedCards;

    // Business methods
    public int getPointsForTeam(String teamName) { ... }
    public int getGoalsScoredByTeam(String teamName) { ... }
    public int getGoalsConcededByTeam(String teamName) { ... }
}
```

### MatchFeatures DTO

Data transfer object containing 47 ML features across 10 phases.

```java
public class MatchFeatures {
    // Phase 1: Form & Goals (11 features)
    private double homeFormPoints, awayFormPoints;
    private double homeGoalsScoredAvg, homeGoalsConcededAvg;
    private double awayGoalsScoredAvg, awayGoalsConcededAvg;
    private double h2hHomeWinRate, h2hDrawRate, h2hAwayWinRate;
    private double homeTotalGoalsAvg, awayTotalGoalsAvg;

    // Phase 2: Match Statistics (4 features)
    private double homeShotsOnTargetAvg, awayShotsOnTargetAvg;
    private double homeCornersAvg, awayCornersAvg;

    // Phase 3: Momentum & Fatigue (10 features)
    private double homeGoalDifference, awayGoalDifference;
    private double homeOverallFormPoints, awayOverallFormPoints;
    private int homeWinStreak, awayWinStreak;
    private int homeUnbeatenStreak, awayUnbeatenStreak;
    private int homeDaysSinceLastMatch, awayDaysSinceLastMatch;

    // Phase 4: Half-Time & League Position (6 features)
    private double homeHalfTimeLeadRate, awayHalfTimeLeadRate;
    private double homeComebackRate, awayComebackRate;
    private int homeLeaguePosition, awayLeaguePosition;

    // Phase 5: Possession Proxy (2 features)
    private double homePossessionProxy, awayPossessionProxy;

    // Phase 6: Elo Ratings (2 features)
    private double homeEloRating, awayEloRating;

    // Phase 7: Derived Interactions (5 features)
    private double formDifference, goalDiffDifference;
    private double h2hDominance, restAdvantage, eloDifference;

    // Phase 8: Recency-Weighted Form (2 features)
    private double homeWeightedForm, awayWeightedForm;

    // Phase 9: Motivation Level (2 features)
    private int homeMotivationLevel, awayMotivationLevel;

    // Phase 10: Squad Strength (3 features)
    private double homeSquadStrength, awaySquadStrength;
    private double squadStrengthDifference;

    // Label (training only)
    private String actualResult;
}
```

### PlayerAvailability Entity

Tracks player injuries, suspensions, and fitness doubts for squad strength analysis.

```java
@Entity
@Table(name = "player_availability")
public class PlayerAvailability {
    @Id @GeneratedValue
    private Long id;

    private String teamName;
    private String playerName;
    private String position;           // GK, DEF, MID, FWD

    @Enumerated(EnumType.STRING)
    private AvailabilityStatus status; // INJURED, SUSPENDED, DOUBTFUL, AVAILABLE

    private String reason;
    private LocalDate expectedReturn;
    private int importanceRating;      // 1–10 (9-10 = star player)
    private boolean keyStar;
    private double avgGoalsPerGame;
    private double avgAssistsPerGame;
    private int suspensionMatchesRemaining;
    private LocalDate reportDate;
    private String season;

    public enum AvailabilityStatus {
        INJURED, SUSPENDED, DOUBTFUL, AVAILABLE
    }
}
```

### Additional Entities

| Entity | Purpose |
|--------|---------|
| **Team** | Team metadata (name, logo URL, short name) |
| **League** | League configuration (code, name, current season) |
| **LeagueStanding** | Season standings (position, points, form) |
| **Prediction** | Prediction tracking (predicted vs actual result) |
| **PredictionEvaluation** | Prediction evaluation metrics and scoring |
| **ModelAccuracy** | Model accuracy history per evaluation run |
| **ModelTrainingHistory** | Training run logs with parameters and metrics |
| **SeasonTeamStats** | Per-season team statistics (Elo, form, streaks, goals) |
| **PoissonParameters** | Dixon-Coles Poisson model parameters (attack/defence strengths) |
| **SyncStatusEntry** | Data synchronization status tracking |
| **AdminAuditLog** | Admin actions audit trail |
| **SystemSettings** | Application configuration key-value pairs |
| **ShotQualityDTO** | Shot quality metrics for team analytics |

---

## Feature Engineering Pipeline

The `FeatureEngineeringService` computes 47 features organized in 10 phases:

### Phase 1: Form & Goals (11 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeFormPoints` | `AVG(points) OVER last 5 home matches` | Points per game at home |
| `awayFormPoints` | `AVG(points) OVER last 5 away matches` | Points per game away |
| `homeGoalsScoredAvg` | `AVG(goals_scored) OVER last 20 home matches` | Avg goals scored at home |
| `homeGoalsConcededAvg` | `AVG(goals_conceded) OVER last 20 home matches` | Avg goals conceded at home |
| `awayGoalsScoredAvg` | `AVG(goals_scored) OVER last 20 away matches` | Avg goals scored away |
| `awayGoalsConcededAvg` | `AVG(goals_conceded) OVER last 20 away matches` | Avg goals conceded away |
| `homeTotalGoalsAvg` | `AVG(home_goals + away_goals) OVER last 5` | Total goals per game |
| `h2hHomeWinRate` | `COUNT(home_wins) / COUNT(h2h_matches)` | H2H home win rate |
| `h2hDrawRate` | `COUNT(draws) / COUNT(h2h_matches)` | H2H draw rate |
| `h2hAwayWinRate` | `COUNT(away_wins) / COUNT(h2h_matches)` | H2D away win rate |

### Phase 2: Match Statistics (4 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeShotsOnTargetAvg` | `AVG(home_shots_on_target) OVER last 10` | Avg shots on target at home |
| `awayShotsOnTargetAvg` | `AVG(away_shots_on_target) OVER last 10` | Avg shots on target away |
| `homeCornersAvg` | `AVG(home_corners) OVER last 10` | Avg corners at home |
| `awayCornersAvg` | `AVG(away_corners) OVER last 10` | Avg corners away |

### Phase 3: Momentum & Fatigue (11 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeGoalDifference` | `AVG(scored - conceded) OVER last 5` | Recent goal difference |
| `awayGoalDifference` | `AVG(scored - conceded) OVER last 5` | Recent goal difference |
| `homeOverallFormPoints` | `AVG(points) OVER last 5 all matches` | Overall form |
| `awayOverallFormPoints` | `AVG(points) OVER last 5 all matches` | Overall form |
| `homeWinStreak` | `COUNT consecutive wins from latest` | Current win streak |
| `awayWinStreak` | `COUNT consecutive wins from latest` | Current win streak |
| `homeUnbeatenStreak` | `COUNT consecutive non-losses` | Unbeaten run |
| `awayUnbeatenStreak` | `COUNT consecutive non-losses` | Unbeaten run |
| `homeDaysSinceLastMatch` | `DAYS_BETWEEN(last_match, today)` | Rest days (capped at 30) |
| `awayDaysSinceLastMatch` | `DAYS_BETWEEN(last_match, today)` | Rest days (capped at 30) |

### Phase 4: Half-Time & League Position (6 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeHalfTimeLeadRate` | `COUNT(leading at HT) / COUNT(matches)` | Rate of leading at half-time |
| `awayHalfTimeLeadRate` | `COUNT(leading at HT) / COUNT(matches)` | Rate of leading at half-time |
| `homeComebackRate` | `COUNT(comebacks) / COUNT(trailing at HT)` | Comeback rate from trailing |
| `awayComebackRate` | `COUNT(comebacks) / COUNT(trailing at HT)` | Comeback rate from trailing |
| `homeLeaguePosition` | `POSITION in season standings` | League position (1-20) |
| `awayLeaguePosition` | `POSITION in season standings` | League position (1-20) |

### Phase 5: Possession Proxy (2 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homePossessionProxy` | `(shotRatio × 0.6) + (cornerRatio × 0.4)` | Estimated possession (0.0-1.0) |
| `awayPossessionProxy` | `(shotRatio × 0.6) + (cornerRatio × 0.4)` | Estimated possession (0.0-1.0) |

### Phase 6: Elo Ratings (2 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeEloRating` | From `SeasonTeamStats` | Current Elo rating (default 1500) |
| `awayEloRating` | From `SeasonTeamStats` | Current Elo rating (default 1500) |

### Phase 7: Derived Interaction Features (5 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `formDifference` | `homeFormPoints - awayFormPoints` | Relative form |
| `goalDiffDifference` | `homeGoalDiff - awayGoalDiff` | Relative goal difference |
| `h2hDominance` | `h2hHomeWinRate - h2hAwayWinRate` | Historical H2H dominance |
| `restAdvantage` | `homeDaysRest - awayDaysRest` | Relative rest advantage |
| `eloDifference` | `homeElo - awayElo` | Elo rating gap |

### Phase 8: Recency-Weighted Form (2 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeWeightedForm` | `Σ(points × 0.7^i) / Σ(0.7^i)` | Exponential decay form (most recent = highest) |
| `awayWeightedForm` | `Σ(points × 0.7^i) / Σ(0.7^i)` | Exponential decay form (most recent = highest) |

### Phase 9: Motivation Level (2 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeMotivationLevel` | Based on league position context | 0-10 score (title/relegation fight = 10) |
| `awayMotivationLevel` | Based on league position context | 0-10 score (title/relegation fight = 10) |

### Phase 10: Squad Strength (3 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeSquadStrength` | `1.0 - (Σ importance × statusWeight) / 30` | Squad strength (0.3-1.0, 1.0 = full) |
| `awaySquadStrength` | `1.0 - (Σ importance × statusWeight) / 30` | Squad strength (0.3-1.0, 1.0 = full) |
| `squadStrengthDifference` | `homeSquadStrength - awaySquadStrength` | Relative squad fitness |

### Points System

```java
W = 3 points  // Win
D = 1 point   // Draw
L = 0 points  // Loss
```

### Temporal Filtering

**Critical for ML integrity**: All feature calculations use date-based cutoffs to prevent future data leakage.

```java
// Training mode: Use match date as cutoff
MatchFeatures features = buildFeatures(homeTeam, awayTeam, match.getMatchDate());

// Prediction mode: Use today as cutoff
MatchFeatures features = buildFeatures(homeTeam, awayTeam, LocalDate.now());
```

---

## Elo Rating System

The `EloRatingService` provides dynamic team strength ratings.

```java
public class EloRatingService {
    // Elo rating calculation with K-factor adjustment
    // Season-wise Elo tracking
    // Initial Elo: 1500 for new teams
    // Home advantage built into Elo calculation
    // Elo-based win probability: 1 / (1 + 10^((ratingB - ratingA) / 400))
}
```

---

## Repository Layer

### MatchRepository

Key query methods for match data access:

```java
public interface MatchRepository extends JpaRepository<Match, Long> {

    // Season-scoped queries
    List<Match> findBySeasonOrderByMatchDateDesc(String season);

    // Team history queries
    List<Match> findByHomeTeamAndMatchDateBeforeOrderByMatchDateDesc(
        String homeTeam, LocalDate beforeDate);

    List<Match> findByAwayTeamAndMatchDateBeforeOrderByMatchDateDesc(
        String awayTeam, LocalDate beforeDate);

    // H2H queries
    @Query("SELECT m FROM Match m WHERE " +
           "((m.homeTeam = :team1 AND m.awayTeam = :team2) OR " +
           " (m.homeTeam = :team2 AND m.awayTeam = :team1)) " +
           "AND m.matchDate < :beforeDate " +
           "ORDER BY m.matchDate DESC")
    List<Match> findH2HBeforeDate(String team1, String team2, LocalDate beforeDate);

    // Season team list
    @Query("SELECT DISTINCT m.homeTeam FROM Match m WHERE m.season = :season")
    Set<String> findDistinctHomeTeamsBySeason(String season);

    // All matches chronological (for training)
    List<Match> findAllByOrderByMatchDateAsc();
}
```

### Season-scoped Query Pattern

```sql
SELECT m FROM Match m
WHERE (m.homeTeam = :team OR m.awayTeam = :team)
  AND m.season = :season
  AND m.fullTimeResult IS NOT NULL
  AND m.matchDate < :beforeDate
ORDER BY m.matchDate DESC
```

---

## Ingestion Infrastructure

The ingestion package provides a shared event-driven architecture for data ingestion:

### Events
| Event | Purpose |
|-------|---------|
| `IngestionCompletedEvent` | Signals that a data ingestion batch is complete |
| `MatchUpdatedEvent` | Signals that a match record was created or updated |
| `CacheInvalidationEvent` | Triggers cache invalidation after data changes |
| `StatsRefreshEvent` | Triggers stats recalculation after data updates |

### DTOs
| DTO | Purpose |
|-----|---------|
| `InternalMatchDto` | Canonical match data format for all data sources |
| `InternalStandingDto` | Canonical standing data format |

### Providers
| Interface | Purpose |
|-----------|---------|
| `MatchDataProvider` | Contract for match data sources (CSV, API, ESPN) |
| `StandingsDataProvider` | Contract for standings data sources |

### Mapper
| Class | Purpose |
|-------|---------|
| `CanonicalMapper` | Maps CSV/API fields to canonical internal DTOs |

---

## Data Dependencies

### Database Tables

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `matches` | Historical match data | `id`, `match_date`, `home_team`, `away_team`, `season`, `full_time_result`, `referee` |
| `teams` | Team metadata | `id`, `name`, `logo_url`, `short_name` |
| `leagues` | League configuration | `id`, `code`, `name`, `current_season` |
| `league_standings` | Season standings | `league_id`, `season`, `team_name`, `position`, `points` |
| `predictions` | Prediction tracking | `match_id`, `predicted_result`, `actual_result`, `is_correct` |
| `prediction_evaluations` | Evaluation metrics | `id`, `evaluation_date`, `accuracy`, `f1_score` |
| `model_accuracy` | Accuracy history | `id`, `recorded_at`, `accuracy`, `total_predictions` |
| `model_training_history` | Training logs | `id`, `trained_at`, `duration_ms`, `accuracy`, `model_type` |
| `season_team_stats` | Season team stats | `id`, `season`, `team_id`, `elo_rating`, `form_points` |
| `player_availability` | Player injury/suspension tracking | `id`, `team_name`, `player_name`, `status`, `importance_rating` |
| `sync_status` | Data sync status | `id`, `source`, `last_sync`, `status` |
| `admin_audit_logs` | Admin actions | `id`, `action`, `username`, `timestamp` |
| `system_settings` | App config | `id`, `key`, `value` |

### Index Strategy

```sql
CREATE INDEX idx_match_date ON matches(match_date DESC);
CREATE INDEX idx_match_home_team ON matches(home_team);
CREATE INDEX idx_match_away_team ON matches(away_team);
CREATE INDEX idx_match_season ON matches(season);
CREATE INDEX idx_match_team_date ON matches(home_team, match_date DESC);
CREATE INDEX idx_match_season_date ON matches(season, match_date DESC);
```

---

## Performance Design

### Batch Loading for Feature Engineering

```java
// Single database round-trip per feature build
List<Match> homeTeamHomeMatches = matchRepository
    .findHomeMatchesByTeamBeforeDate(homeTeam, beforeDate);
List<Match> awayTeamAwayMatches = matchRepository
    .findAwayMatchesByTeamBeforeDate(awayTeam, beforeDate);
List<Match> h2hMatches = matchRepository
    .findH2HBeforeDate(homeTeam, awayTeam, beforeDate);
```

### Stream-based Aggregation with Limits

```java
return matches.stream()
    .limit(window)  // Typically 5, 10, or 20
    .mapToInt(m -> m.getPointsForTeam(teamName))
    .average()
    .orElse(0.0);
```

### Query Constraints

| Operation | Constraint | Rationale |
|-----------|------------|-----------|
| Form calculation | `LIMIT 5` | Recent form window |
| Shot/corner averages | `LIMIT 10` | Larger window for stats |
| Goal averages | `LIMIT 20` | Stable historical average |
| Days since last match | `MAX 30` | Cap to prevent outliers |

---

## Edge Case Handling

### Null Value Protection

```java
private double calcShotsOnTargetAvg(List<Match> matches, boolean isHome) {
    if (matches.isEmpty()) return 0.0;
    return matches.stream()
        .limit(10)
        .filter(m -> isHome ? m.getHomeShotsOnTarget() != null
                            : m.getAwayShotsOnTarget() != null)
        .mapToInt(m -> isHome ? m.getHomeShotsOnTarget()
                              : m.getAwayShotsOnTarget())
        .average()
        .orElse(0.0);
}
```

### Empty Dataset Handling

```java
private double calcH2HWinRate(List<Match> h2hMatches, String teamName) {
    if (h2hMatches.isEmpty()) return 0.33;  // Neutral prior (1/3)
}

private int calcDaysSinceLastMatch(List<Match> matches, LocalDate beforeDate) {
    if (matches.isEmpty()) return 14;  // Default: 2 weeks rest
}
```

### Division by Zero Protection

```java
if (h2hMatches.isEmpty()) return 0.33;
return (double) wins / h2hMatches.size();  // Guaranteed non-zero denominator
```

---

## Testing Strategy

### Unit Tests

| Test Class | Coverage |
|------------|----------|
| `MatchTest` | Entity methods |
| `MatchFeaturesTest` | DTO builder and validation |
| `FeatureEngineeringServiceTest` | All 47 feature calculations (10 phases) |

### Test Scenarios

```java
@Test
void buildsCorrectFeaturesForTeamWithHistory() { ... }

@Test
void handlesNewTeamWithNoHistory() { ... }

@Test
void calculatesFormWithExactlyFiveMatches() { ... }

@Test
void preventsDataLeakageWithTemporalCutoff() { ... }
```

---

## Maven Dependency

To use this module in other modules:

```xml
<dependency>
    <groupId>com.app</groupId>
    <artifactId>football-prediction-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## Metrics

| Metric | Value |
|--------|-------|
| Entities | 15 |
| Repositories | 13 |
| Services | 4 |
| Utilities | 3 |
| ML Features | 47 (10 phases) |
| Ingestion Events | 4 |
| Ingestion DTOs | 2 |
| Provider Interfaces | 2 |

---

**[← Back to Main README](../README.md)**

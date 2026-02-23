# Football Prediction Common Module

## Module Overview

### Purpose
The `football-prediction-common` module serves as the **shared foundation layer** for the Football Prediction Platform. It provides core domain entities, data access repositories, feature engineering services, and utility functions that are consumed by both the main application (`football-prediction-app`) and the model training service (`model-training-service`).

### Scope within the System
```
┌─────────────────────────────────────────────────────────────────┐
│                     System Architecture                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────┐         ┌───────────────────┐           │
│  │ football-         │         │ model-training-   │           │
│  │ prediction-app    │         │ service           │           │
│  │ (Port 8080)       │         │ (Port 8081)       │           │
│  └─────────┬─────────┘         └─────────┬─────────┘           │
│            │                             │                      │
│            └──────────┬──────────────────┘                      │
│                       │                                         │
│                       ▼                                         │
│            ┌─────────────────────┐                              │
│            │ football-prediction-│  ◄── THIS MODULE             │
│            │ common              │                              │
│            │ • Entities          │                              │
│            │ • Repositories      │                              │
│            │ • Services          │                              │
│            │ • Utilities         │                              │
│            └─────────────────────┘                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Responsibilities

### 1. Domain Entity Management
- Define JPA entities for core domain objects
- Enforce database constraints and indexing
- Provide entity-level business methods

### 2. Data Access Layer
- Spring Data JPA repositories for all entities
- Temporal queries with date-based filtering
- Season-scoped queries for insights engine
- Head-to-head (H2H) historical queries

### 3. Feature Engineering
- Calculate 25 ML features for match prediction
- Support training mode (with labels) and prediction mode (without labels)
- Implement temporal cutoffs to prevent data leakage

### 4. Utility Functions
- Prediction utilities (rounding, confidence calculation)
- Safe value handling for null protection
- Label-to-text conversion

---

## Architecture

### Package Structure

```
com.app.common/
├── model/                    # JPA Entities
│   ├── Match.java           # Core match data (126 lines)
│   ├── Team.java            # Team metadata (53 lines)
│   ├── League.java          # League configuration (75 lines)
│   ├── LeagueStanding.java  # Season standings (201 lines)
│   ├── Prediction.java      # Prediction tracking (165 lines)
│   ├── MatchFeatures.java   # ML feature DTO
│   ├── AdminAuditLog.java   # Admin action logging
│   └── SystemSettings.java  # Application settings
│
├── repository/               # Spring Data JPA
│   ├── MatchRepository.java          # Match queries (167 lines)
│   ├── TeamRepository.java           # Team queries
│   ├── LeagueRepository.java         # League queries
│   ├── LeagueStandingRepository.java # Standings queries
│   ├── PredictionRepository.java     # Prediction tracking
│   ├── AdminAuditLogRepository.java  # Audit queries
│   └── SystemSettingsRepository.java # Settings queries
│
├── service/                  # Shared Business Logic
│   └── FeatureEngineeringService.java  # 25-feature ML pipeline (354 lines)
│
└── util/                     # Utilities
    └── PredictionUtils.java  # Helper functions
```

### Data Flow

```
External Data (CSV/API)
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Repository Layer                          │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │ MatchRepository │  │ TeamRepository  │  ...              │
│  └────────┬────────┘  └────────┬────────┘                   │
│           │                    │                             │
│           └────────┬───────────┘                             │
│                    ▼                                         │
│         ┌─────────────────────────────┐                      │
│         │ FeatureEngineeringService   │                      │
│         │ • buildFeaturesForTraining()│                      │
│         │ • buildFeaturesForPrediction│                      │
│         └──────────────┬──────────────┘                      │
│                        │                                     │
│                        ▼                                     │
│              MatchFeatures DTO (25 features)                 │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
   Consumed by App / Training Service
```

---

## Core Business Logic

### Feature Engineering Pipeline

The `FeatureEngineeringService` computes 25 features organized in 3 phases:

#### Phase 1: Form & Goals (10 features)

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
| `h2hAwayWinRate` | `COUNT(away_wins) / COUNT(h2h_matches)` | H2H away win rate |

#### Phase 2: Match Statistics (4 features)

| Feature | Formula | Description |
|---------|---------|-------------|
| `homeShotsOnTargetAvg` | `AVG(home_shots_on_target) OVER last 10` | Avg shots on target at home |
| `awayShotsOnTargetAvg` | `AVG(away_shots_on_target) OVER last 10` | Avg shots on target away |
| `homeCornersAvg` | `AVG(home_corners) OVER last 10` | Avg corners at home |
| `awayCornersAvg` | `AVG(away_corners) OVER last 10` | Avg corners away |

#### Phase 3: Momentum & Fatigue (11 features)

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

### Points System

```java
// Standard football points calculation
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

### Season-scoped Queries

All insights queries must filter by season:

```sql
-- Standard pattern for season-scoped queries
SELECT m FROM Match m 
WHERE (m.homeTeam = :team OR m.awayTeam = :team)
  AND m.season = :season
  AND m.fullTimeResult IS NOT NULL
  AND m.matchDate < :beforeDate
ORDER BY m.matchDate DESC
```

---

## Data Dependencies

### Database Tables

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `matches` | Historical match data | `id`, `match_date`, `home_team`, `away_team`, `season`, `full_time_result` |
| `teams` | Team metadata | `id`, `name`, `logo_url`, `short_name` |
| `leagues` | League configuration | `id`, `code`, `name`, `current_season` |
| `league_standings` | Season standings | `league_id`, `season`, `team_name`, `position`, `points` |
| `predictions` | Prediction tracking | `match_id`, `predicted_result`, `actual_result`, `is_correct` |

### External Dependencies

- **None**: This module has no external service dependencies
- All data comes from the H2 database

---

## Performance Design

### Index Strategy

```sql
-- Recommended indexes for optimal query performance
CREATE INDEX idx_match_date ON matches(match_date DESC);
CREATE INDEX idx_match_home_team ON matches(home_team);
CREATE INDEX idx_match_away_team ON matches(away_team);
CREATE INDEX idx_match_season ON matches(season);
CREATE INDEX idx_match_team_date ON matches(home_team, match_date DESC);
CREATE INDEX idx_match_season_date ON matches(season, match_date DESC);
```

### Query Optimization

#### Batch Loading for Feature Engineering

```java
// Single database round-trip per feature build
List<Match> homeTeamHomeMatches = matchRepository.findHomeMatchesByTeamBeforeDate(homeTeam, beforeDate);
List<Match> awayTeamAwayMatches = matchRepository.findAwayMatchesByTeamBeforeDate(awayTeam, beforeDate);
List<Match> h2hMatches = matchRepository.findH2HBeforeDate(homeTeam, awayTeam, beforeDate);
```

#### Stream-based Aggregation with Limits

```java
// Limit processing to relevant window size
return matches.stream()
    .limit(window)  // Typically 5, 10, or 20
    .mapToInt(m -> m.getPointsForTeam(teamName))
    .average()
    .orElse(0.0);
```

### Avoidance of N+1

```java
// Cache matches to avoid N+1 queries
Map<String, List<Match>> matchCache = new HashMap<>();
for (String team : teams) {
    List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
    matchCache.put(team, matches);  // Cache for reuse
}
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
        .filter(m -> isHome ? m.getHomeShotsOnTarget() != null : m.getAwayShotsOnTarget() != null)
        .mapToInt(m -> isHome ? m.getHomeShotsOnTarget() : m.getAwayShotsOnTarget())
        .average()
        .orElse(0.0);
}
```

### Empty Dataset Handling

```java
// Return sensible defaults for empty data
private double calcH2HWinRate(List<Match> h2hMatches, String teamName) {
    if (h2hMatches.isEmpty()) return 0.33;  // Neutral prior (1/3)
    // ...
}

private int calcDaysSinceLastMatch(List<Match> matches, LocalDate beforeDate) {
    if (matches.isEmpty()) return 14;  // Default: 2 weeks rest
    // ...
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
| `MatchTest` | Entity methods (`getPointsForTeam`, `getGoalsScoredByTeam`) |
| `MatchFeaturesTest` | DTO builder and field validation |
| `FeatureEngineeringServiceTest` | All 25 feature calculations |

### Test Scenarios

```java
// Normal case: Team with full history
@Test
void buildsCorrectFeaturesForTeamWithHistory();

// Edge case: New team with no history
@Test
void handlesNewTeamWithNoHistory();

// Boundary: Exactly 5 matches for form
@Test
void calculatesFormWithExactlyFiveMatches();
```

---

## Future Enhancements

| Enhancement | Description | Priority |
|-------------|-------------|----------|
| xG Features | Add expected goals data | High |
| Player Availability | Track key player injuries | Medium |
| Referee Features | Historical referee statistics | Medium |
| Read Replicas | Distribute query load | Low |
| Async Features | Parallel computation | Low |

---

## Configuration

```properties
# Feature Engineering
feature.form.window=5
```

### Maven Dependency

```xml
<dependency>
    <groupId>com.app</groupId>
    <artifactId>football-prediction-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

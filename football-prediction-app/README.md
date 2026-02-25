# Football Prediction Application

> **Part of the [Football Prediction Platform](../README.md)** - Main application module providing REST APIs, services, and web UI.

---

## Module Overview

### Purpose
The `football-prediction-app` is the **main application module** of the Football Prediction Platform. It serves as the primary interface for users, providing REST APIs for match predictions, comprehensive analytics dashboards, team statistics, and a modern web UI. This module orchestrates all user-facing functionality and integrates with external APIs for live data.

### Scope within the System

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SYSTEM ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────┐                │
│  │             Main Application (Port 8080)                │  ◄── THIS     │
│  │                                                         │      MODULE   │
│  │  ┌─────────────────────────────────────────────────┐    │                │
│  │  │                 Controllers                      │    │                │
│  │  │  • PredictionController   • DashboardController  │    │                │
│  │  │  • AnalyticsController    • TeamStatsController  │    │                │
│  │  │  • ExternalApiController  • AdminController      │    │                │
│  │  └─────────────────────────────────────────────────┘    │                │
│  │                          │                              │                │
│  │                          ▼                              │                │
│  │  ┌─────────────────────────────────────────────────┐    │                │
│  │  │                  Services                        │    │                │
│  │  │  • PreMatchInsightsService  • H2HInsightsService │    │                │
│  │  │  • TrendingInsightsService  • TeamStatsService   │    │                │
│  │  │  • ShotQualityService       • FoulsAnalysisService│    │                │
│  │  │  • LeagueStandingService    • DashboardService   │    │                │
│  │  └─────────────────────────────────────────────────┘    │                │
│  │                          │                              │                │
│  │                          ▼                              │                │
│  │  ┌───────────────┐  ┌────────────────┐                  │                │
│  │  │ Caffeine Cache│  │ ML Model       │                  │                │
│  │  │ (16 caches)   │  │ Integration    │                  │                │
│  │  └───────────────┘  └────────────────┘                  │                │
│  │                                                         │                │
│  └─────────────────────────────────────────────────────────┘                │
│                               │                                             │
│           ┌───────────────────┼───────────────────┐                         │
│           ▼                   ▼                   ▼                         │
│  ┌─────────────────┐  ┌───────────────┐  ┌──────────────────┐              │
│  │ Common Module   │  │ H2 Database   │  │ External APIs    │              │
│  │ • Entities      │  │ • matches     │  │ • football-data  │              │
│  │ • Repositories  │  │ • teams       │  │ • News RSS feeds │              │
│  └─────────────────┘  └───────────────┘  └──────────────────┘              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Related Documentation:**
- [Main Platform README](../README.md)
- [Common Module](../football-prediction-common/README.md)
- [Model Training Service](../model-training-service/README.md)
- [Frontend Components](../frontend/README.md)

---

## Table of Contents

- [Responsibilities](#responsibilities)
- [Architecture](#architecture)
- [Package Structure](#package-structure)
- [Core Services](#core-services)
- [API Endpoints](#api-endpoints)
- [Caching Configuration](#caching-configuration)
- [Business Logic](#business-logic)
- [Performance Design](#performance-design)
- [Edge Case Handling](#edge-case-handling)
- [Testing Strategy](#testing-strategy)
- [Configuration](#configuration)

---

## Responsibilities

### 1. Match Prediction
- Execute ML model predictions for any team matchup
- Calculate prediction probabilities (Home/Draw/Away)
- Determine confidence levels (HIGH/MEDIUM/LOW)
- Return pre-match insights with predictions

### 2. Season-aware Analytics
- Hot teams (3+ winning streaks per season)
- Cold teams (5+ matches without win per season)
- Top scorers (season aggregate)
- Defensive walls (clean sheet leaders)
- Upset alerts (away team favorites)
- Goal fest predictions

### 3. Pre-Match Insights
- Form comparison between teams
- Win/unbeaten streak indicators
- Rest days and fatigue warnings
- Goal threat meters
- Over/Under 2.5 probability
- Both Teams to Score percentage

### 4. Head-to-Head Analysis
- Historical meeting record
- Last 5 encounters
- Average goals in H2H
- Most common results
- Venue advantage statistics

### 5. League Management
- Current standings with zones
- Season-filtered standings
- Form indicators (W/D/L)
- Live data from football-data.org

### 6. Dashboard Aggregation
- Upcoming matches
- Today's predictions
- Model accuracy metrics
- Top teams widget

### 7. Shot Quality Analytics
- Shot efficiency scoring (0-100 scale)
- Shot accuracy percentage calculation
- Conversion rate (goals per shot)
- Home/Away split analysis
- League average comparison
- Last 10 matches trend sparklines

### 8. Fouls & Discipline Analysis
- Discipline score (0-10 scale)
- Fouls committed/drawn averages
- Fouls differential visualization
- Win rate by foul count (low/controlled/high)
- Color-coded discipline badges
- Team comparison for predictions

---

## Architecture

### Layered Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CONTROLLER LAYER                               │
│  • Input validation          • Request/Response mapping                 │
│  • Error handling            • Logging                                  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            SERVICE LAYER                                 │
│  • Business logic            • Data aggregation                         │
│  • Season filtering          • Threshold calculations                   │
│  • Cache management          • External API calls                       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           REPOSITORY LAYER                               │
│  • JPA queries               • Season-scoped queries                    │
│  • Temporal filtering        • Data access                              │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            DATA LAYER                                    │
│  • H2 Database               • ML Model File                            │
│  • External APIs             • RSS Feeds                                │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.app.footballprediction/
├── FootballPredictionApplication.java    # Spring Boot entry point
│
├── controller/                           # REST API Layer
│   ├── PredictionController.java         # Match predictions
│   ├── AnalyticsController.java          # Pre-match & H2H insights
│   ├── DashboardController.java          # Dashboard widgets
│   ├── TeamStatsController.java          # Team statistics & analytics
│   ├── ExternalApiController.java        # External API proxy
│   ├── AdminController.java              # Admin operations
│   ├── NewsController.java               # News aggregation
│   └── SeasonsController.java            # Season management
│
├── service/                              # Business Logic Layer
│   ├── PreMatchInsightsService.java      # Pre-match analysis
│   ├── TrendingInsightsService.java      # Season trends
│   ├── H2HInsightsService.java           # Head-to-head analysis
│   ├── LeagueStandingService.java        # Standings management
│   ├── LeagueStatsService.java           # League-wide statistics
│   ├── TeamStatsService.java             # Team statistics
│   ├── TeamAnalyticsService.java         # Full team analytics
│   ├── ShotQualityService.java           # Shot efficiency metrics
│   ├── FoulsAnalysisService.java         # Fouls & discipline analysis
│   ├── DashboardService.java             # Dashboard aggregation
│   ├── FootballDataApiService.java       # football-data.org integration
│   ├── NewsService.java                  # RSS news aggregation
│   ├── CsvIngestionService.java          # CSV data loading
│   ├── CacheWarmingService.java          # Cache pre-population
│   ├── CacheStatisticsService.java       # Cache monitoring
│   ├── PredictionTrackingService.java    # Accuracy tracking
│   ├── SeasonStatsService.java           # Season statistics
│   ├── TeamService.java                  # Team CRUD
│   └── AdminService.java                 # Admin operations
│
├── dto/                                  # Data Transfer Objects
│   ├── PredictRequest.java
│   ├── PredictResponse.java
│   ├── PreMatchInsightsResponse.java
│   ├── TrendingInsightsResponse.java
│   ├── H2HInsightsResponse.java
│   ├── LeagueStandingsResponse.java
│   ├── TeamStatsResponse.java
│   ├── FoulsAnalysisDTO.java             # Fouls & discipline metrics
│   └── dashboard/                        # Dashboard-specific DTOs
│
├── config/                               # Configuration
│   ├── CacheConfig.java                  # Caffeine cache setup
│   ├── TeamLogoSeeder.java               # Team logo initialization
│   └── WebConfig.java                    # CORS, static resources
│
├── modeltraining/                        # ML Integration
│   └── ModelTrainingService.java         # Model loading & prediction
│
└── scheduler/                            # Scheduled Tasks
    └── DataUpdateScheduler.java          # Periodic data refresh
```

---

## Core Services

### ShotQualityService

Calculates shot efficiency metrics for teams.

```java
public class ShotQualityService {

    public ShotQualityDTO getShotQuality(String teamName, Boolean isHome) {
        // Calculate quality score (0-100)
        // Shot accuracy percentage
        // Conversion rate (goals/shots)
        // Last 10 matches trend data
    }

    public ShotQualityDTO getSplitShotQuality(String teamName) {
        // Returns home and away metrics separately
    }
}
```

**Key Metrics:**
| Metric | Formula |
|--------|---------|
| Quality Score | `(shotAccuracy * 0.4 + conversionRate * 0.6) * 10` |
| Shot Accuracy | `shotsOnTarget / totalShots * 100` |
| Conversion Rate | `goals / totalShots * 100` |

### FoulsAnalysisService

Analyzes team discipline and foul patterns.

```java
public class FoulsAnalysisService {

    public FoulsAnalysisDTO getFoulsAnalysis(String teamName, Boolean isHome) {
        // Discipline score (0-10)
        // Fouls committed/drawn averages
        // Win rate by foul count
    }
}
```

**Discipline Score Calculation:**
```java
// Lower fouls = higher discipline score
double normalizedFouls = (maxFouls - avgFouls) / (maxFouls - minFouls);
disciplineScore = normalizedFouls * 10;
```

### TrendingInsightsService

All trending insights are **strictly season-scoped**:

```java
@Cacheable(value = "trendingInsights", key = "#season")
public TrendingInsightsResponse getTrendingInsightsBySeason(String season) {
    Set<String> seasonTeams = getTeamsForSeason(season);

    List<HotTeam> hotTeams = calculateHotTeams(seasonTeams, beforeDate, season);
    List<ColdTeam> coldTeams = calculateColdTeams(seasonTeams, beforeDate, season);
    // ...
}
```

**Threshold Logic:**
| Insight | Threshold |
|---------|-----------|
| Hot Teams | `winStreak >= 3` OR `winsInLast5 >= 4` |
| Cold Teams | `matchesWithoutWin >= 5` |
| Top Scorers | `ORDER BY goalsScored DESC LIMIT 5` |
| Defensive Walls | `ORDER BY cleanSheets DESC LIMIT 5` |

### PreMatchInsightsService

| Insight | Formula |
|---------|---------|
| Goal Threat | `MIN(100, MAX(0, (goalsScoredAvg * 30) + (opponentConcededAvg * 20)))` |
| Over 2.5 Probability | `(homeGoalsAvg + awayGoalsAvg) / 5.0` |
| BTTS % | `scoringRate * opponentScoringRate` |
| Fatigue Warning | `daysSinceLastMatch < 4` |

---

## API Endpoints

### Prediction Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/predict` | POST | Predict match outcome |
| `/api/predict/batch` | POST | Batch predictions |

### Analytics Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/analytics/pre-match` | GET | Pre-match insights |
| `/api/analytics/trends` | GET | Season trending insights |
| `/api/analytics/h2h` | GET | Head-to-head analysis |

### Team Statistics Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/teams/{name}/stats` | GET | Team statistics |
| `/api/teams/{name}/shot-quality` | GET | Shot quality metrics |
| `/api/teams/{name}/fouls-analysis` | GET | Fouls & discipline |

### Dashboard Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/dashboard/upcoming-matches` | GET | Upcoming fixtures |
| `/api/dashboard/league-standings` | GET | League table |
| `/api/dashboard/model-accuracy` | GET | Model accuracy stats |

### Admin Endpoints (Authenticated)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/verify` | GET | Verify credentials |
| `/api/admin/dashboard` | GET | Admin stats |
| `/api/admin/toggle-engine` | POST | Toggle predictions |
| `/api/admin/retrain` | POST | Trigger retraining |

---

## Caching Configuration

```java
// CacheConfig.java - 16 cache definitions
public static final String CACHE_STANDINGS = "standings";           // 5 min TTL
public static final String CACHE_MATCHES = "matches";               // 5 min TTL
public static final String CACHE_NEWS = "news";                     // 15 min TTL
public static final String CACHE_PREDICTIONS = "predictions";       // 1 min TTL
public static final String CACHE_TEAM_STATS = "teamStats";         // 10 min TTL
public static final String CACHE_TEAM_FORM = "teamForm";           // 10 min TTL
public static final String CACHE_TEAM_LOGOS = "teamLogos";         // 60 min TTL
public static final String CACHE_SHOT_QUALITY = "shotQuality";     // 10 min TTL
public static final String CACHE_FOULS_ANALYSIS = "foulsAnalysis"; // 10 min TTL
public static final String CACHE_H2H_INSIGHTS = "h2hInsights";     // 10 min TTL
public static final String CACHE_TRENDING_INSIGHTS = "trendingInsights";  // 5 min TTL
public static final String CACHE_API_RESPONSES = "apiResponses";   // 5 min TTL
public static final String CACHE_SEASONS = "seasons";              // 60 min TTL
public static final String CACHE_SEASON_STATS = "seasonStats";     // 30 min TTL
public static final String CACHE_TEAM_ANALYTICS = "teamAnalytics"; // 15 min TTL
public static final String CACHE_PRE_MATCH_INSIGHTS = "preMatchInsights";  // 10 min TTL
```

### Cache Warming

Caches are pre-populated on application startup:

```java
@EventListener(ApplicationReadyEvent.class)
public void warmCaches() {
    // Pre-load current season standings
    // Pre-load team logos
    // Pre-load trending insights
}
```

---

## Business Logic

### Prediction Confidence Levels

```java
public static String getConfidence(double[] probabilities) {
    double maxProb = Arrays.stream(probabilities).max().orElse(0);

    if (maxProb >= 0.55) return "HIGH";
    if (maxProb >= 0.45) return "MEDIUM";
    return "LOW";
}
```

### Hot Teams Calculation

```java
private List<HotTeam> calculateHotTeams(Set<String> teams, LocalDate beforeDate, String season) {
    for (String team : teams) {
        List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);

        if (matches.size() < HOT_FORM_WINDOW) continue;  // Min 5 matches required

        int winStreak = calcWinStreak(matches, team);

        if (winStreak >= HOT_STREAK_THRESHOLD) {  // 3+ consecutive wins
            hotTeams.add(buildHotTeam(team, matches, winStreak));
        }
    }

    return hotTeams.stream().limit(TOP_N_RESULTS).toList();  // LIMIT 5
}
```

---

## Performance Design

### N+1 Prevention Strategies

#### 1. Match Caching in Trending Insights

```java
Map<String, List<Match>> matchCache = new HashMap<>();

for (String team : teams) {
    List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
    matchCache.put(team, matches);  // Cache for reuse
}
```

#### 2. Batch Team Logo Lookup

```java
Map<String, String> teamLogos = teamRepository.findAll().stream()
    .collect(Collectors.toMap(Team::getName, Team::getLogoUrl));

for (Match match : matches) {
    String homeLogo = teamLogos.get(match.getHomeTeam());  // O(1) lookup
}
```

### Query Constraints

| Service | Constraint | Rationale |
|---------|------------|-----------|
| TrendingInsightsService | `LIMIT 5` per category | Display limit |
| PreMatchInsightsService | `LIMIT 5` for form window | Recent form only |
| H2HInsightsService | `LIMIT 5` for recent meetings | UI display |

### Response Time Targets

| Endpoint | Target | Strategy |
|----------|--------|----------|
| `/api/predict` | <200ms | Pre-loaded model |
| `/api/dashboard/*` | <300ms | Caffeine cache |
| `/api/analytics/*` | <500ms | Season-filtered queries |

---

## Edge Case Handling

### Null/Empty Data

```java
private TrendingInsightsResponse buildEmptyResponse() {
    return TrendingInsightsResponse.builder()
        .hotTeams(Collections.emptyList())
        .coldTeams(Collections.emptyList())
        .totalTeamsAnalyzed(0)
        .season(null)
        .build();
}
```

### Minimum Match Requirements

```java
if (matches.size() < HOT_FORM_WINDOW) continue;  // Skip new teams
```

### Division by Zero

```java
double percentage = total > 0 ? (double) count / total * 100 : 0.0;
```

### Invalid Team Names

```java
if (request.getHomeTeam() == null || request.getHomeTeam().isBlank()) {
    return ResponseEntity.badRequest().body(Map.of(
        "error", "homeTeam is required",
        "hint", "Use GET /api/teams to see valid team names"
    ));
}
```

---

## Testing Strategy

### Unit Tests

| Test Class | Coverage |
|------------|----------|
| `PredictionControllerTest` | Prediction endpoints |
| `TrendingInsightsServiceTest` | Season-aware insights |
| `ShotQualityServiceTest` | Shot quality calculations |
| `FoulsAnalysisServiceTest` | Fouls analysis |
| `PossessionProxyCalculatorTest` | Possession estimation |

### Test Scenarios

```java
@Test
void identifiesHotTeamsWithConsecutiveWins() {
    // Given: Team with 4 consecutive wins
    // When: Calculate hot teams
    // Then: Team appears in hot teams list
}

@Test
void doesNotMixCrossSeasonData() {
    // Given: Team with wins in 2024-25 and 2025-26
    // When: Query for 2025-26 season
    // Then: Only 2025-26 wins counted
}

@Test
void cachesInsightsPerSeason() {
    service.getTrendingInsightsBySeason("2025-26");
    service.getTrendingInsightsBySeason("2025-26");
    verify(matchRepository, times(1)).findBySeasonOrderByMatchDateDesc("2025-26");
}
```

---

## Configuration

### Application Properties

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:h2:file:./data/footballdb

# External API
football.api.key=${FOOTBALL_API_KEY:your_api_key_here}
football.api.base-url=https://api.football-data.org/v4

# Model
model.output.path=./data/match_predictor.model
model.type=STACKED_ENSEMBLE

# Scheduler
scheduler.enabled=true
scheduler.cron=0 0 6 * * MON,FRI
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FOOTBALL_API_KEY` | - | football-data.org API key |
| `ADMIN_USERNAME` | admin | Admin panel username |
| `ADMIN_PASSWORD` | - | Admin panel password |

---

## Metrics

| Metric | Value |
|--------|-------|
| Lines of Code | ~15,000 |
| Controllers | 8 |
| Services | 20 |
| REST Endpoints | 45+ |
| Cache Definitions | 16 |
| DTOs | 25+ |

---

**[← Back to Main README](../README.md)**


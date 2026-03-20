# Football Prediction Application

> **Part of the [Football Prediction Platform](../README.md)** - Main application module providing REST APIs, services, and web UI.

---

## Module Overview

### Purpose
The `football-prediction-app` is the **main application module** of the Football Prediction Platform. It serves as the primary interface for users, providing REST APIs for match predictions (outcome + exact score), comprehensive analytics dashboards, team statistics, expected goals (xG), referee analysis, fixture congestion tracking, kickoff time analysis, player availability & squad strength impact, Dixon-Coles Poisson score predictions, and a modern web UI. This module orchestrates all user-facing functionality and integrates with external APIs for live data, along with a real-time SSE event system and smart data ingestion pipeline.

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
│  │  │  • PredictionController   • TeamStatsController  │    │                │
│  │  │  • SeasonTeamStatsController                     │    │                │
│  │  │  • SeasonsController      • RefereeController    │    │                │
│  │  └─────────────────────────────────────────────────┘    │                │
│  │  ┌─────────────────────────────────────────────────┐    │                │
│  │  │           Polling / Ingestion / SSE              │    │                │
│  │  │  • SyncStatusController   • SseController        │    │                │
│  │  │  • IngestionAdminController                      │    │                │
│  │  └─────────────────────────────────────────────────┘    │                │
│  │                          │                              │                │
│  │                          ▼                              │                │
│  │  ┌─────────────────────────────────────────────────┐    │                │
│  │  │                  Services                        │    │                │
│  │  │  • PreMatchInsightsService  • H2HInsightsService │    │                │
│  │  │  • TrendingInsightsService  • TeamStatsService   │    │                │
│  │  │  • ShotQualityService       • FoulsAnalysisService│   │                │
│  │  │  • ExpectedGoalsService     • CornerStatsService │    │                │
│  │  │  • CardsPredictionService   • HalfAnalysisService│    │                │
│  │  │  • RefereeStatsService      • KickoffTimeService │    │                │
│  │  │  • FixtureCongestionService • ModelAccuracyService│   │                │
│  │  │  • LeagueStandingService    • DashboardService   │    │                │
│  │  └─────────────────────────────────────────────────┘    │                │
│  │                          │                              │                │
│  │                          ▼                              │                │
│  │  ┌───────────────┐  ┌────────────────┐                  │                │
│  │  │ Caffeine Cache│  │ ML Model       │                  │                │
│  │  │ (19 caches)   │  │ Integration    │                  │                │
│  │  └───────────────┘  └────────────────┘                  │                │
│  │                                                         │                │
│  └─────────────────────────────────────────────────────────┘                │
│                               │                                             │
│           ┌───────────────────┼───────────────────┐                         │
│           ▼                   ▼                   ▼                         │
│  ┌─────────────────┐  ┌───────────────┐  ┌──────────────────┐              │
│  │ Common Module   │  │ H2 Database   │  │ External APIs    │              │
│  │ • Entities      │  │ • matches     │  │ • football-data  │              │
│  │ • Repositories  │  │ • teams       │  │ • ESPN           │              │
│  │ • Services      │  │ • predictions │  │ • News RSS feeds │              │
│  └─────────────────┘  └───────────────┘  └──────────────────┘              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Related Documentation:**
- [Main Platform README](../README.md)
- [Common Module](../football-prediction-common/README.md)
- [Model Training Service](../model-training-service/README.md)

---

## Table of Contents

- [Responsibilities](#responsibilities)
- [Architecture](#architecture)
- [Package Structure](#package-structure)
- [Core Services](#core-services)
- [API Endpoints](#api-endpoints)
- [API Documentation (OpenAPI / Swagger)](#api-documentation-openapi--swagger)
- [Error Handling (RFC 7807)](#error-handling-rfc-7807)
- [Observability (Prometheus & Grafana)](#observability-prometheus--grafana)
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
- Prediction explainability with factor breakdown

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

### 9. Corner Statistics & Predictions
- Corner kick statistics per team (home/away split)
- Average corners won and conceded
- Corner dominance calculation
- Weighted averages with recency decay
- Match corner predictions with probability distribution
- Over/under probabilities (9.5, 10.5, 11.5 corners)

### 10. Cards Prediction & Team Discipline
- Yellow and red card predictions
- Referee influence on card rates
- Team discipline ratings (0-10)
- Recent bookings summary
- Card risk level classification (HIGH/MEDIUM/LOW)

### 11. Half-Time Analysis
- First half vs second half goal distribution
- Win rates based on half-time position
- Comeback rate statistics
- Pattern classification (Fast Starter/Strong Finisher/Balanced)
- Confidence levels based on matches analyzed

### 12. Expected Goals (xG)
- Team xG based on shots on target × league conversion rate
- Over/underperformance relative to xG
- Match xG predictions with over/under goal probabilities
- Home/Away split xG analysis
- Recency-weighted xG calculation
- Home advantage factor (1.10× multiplier)

### 13. Referee Analytics
- Per-referee statistics (yellows, reds, goals per match)
- Strictness rating relative to league average
- Result distribution per referee
- Strictest and most lenient referee rankings
- Match referee impact analysis

### 14. Fixture Congestion & Fatigue
- Fatigue index (0-100) based on recent match gaps
- Win rate segmented by short/normal/long rest
- Congestion comparison for match previews
- Rest advantage detection between teams

### 15. Kickoff Time Analysis
- Performance breakdown by time slot (Early/Afternoon/Late/Evening)
- Win/draw/loss breakdown per slot
- Goal averages per time slot
- Strong/Average/Weak classification per slot
- Best and worst kickoff times

### 16. Smart Data Polling & SSE
- Daily automated match data polling
- Smart retrain trigger on new data
- Server-Sent Events (SSE) for match completion notifications
- System status and sync status monitoring
- Match day status tracking

### 17. Data Ingestion Pipeline
- Idempotent upsert service for match data
- Shadow validation for data integrity
- Ingestion orchestration and routing
- ESPN integration for data enrichment
- Cache invalidation on data changes
- Feature flag service for controlled rollouts

### 18. Score Prediction (Dixon-Coles Poisson)
- Exact scoreline prediction (e.g. "2-1" with probability)
- Top 3 most likely scores
- Over/Under goal market probabilities (1.5, 2.5, 3.5)
- Both Teams to Score (BTTS) probability
- Clean sheet probabilities for both teams
- Dixon-Coles modification for low-score correlation

### 19. Player Availability & Squad Strength
- Player injury/suspension/doubt tracking
- Squad strength calculation (0.0–1.0) from key player absences
- Attack impact: goals/assists contribution of absent players
- Defence impact: position-weighted absence effect
- Availability ratings: FULL_STRENGTH / MINOR_CONCERNS / WEAKENED / SEVERELY_WEAKENED
- Probability adjustment: ±8% max shift based on squad asymmetry
- Daily automatic sync from football-data.org (10:00 AM)
- Admin API for manual player status updates

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
│  • ESPN API                  • SSE Event Stream                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.app.footballprediction/
├── FootballPredictionApplication.java    # Spring Boot entry point
│
├── controller/                           # REST API Layer (24 controllers)
│   ├── PredictionController.java         # Predictions, H2H, trending insights
│   ├── AdminController.java              # Admin dashboard, settings, system controls
│   ├── AnalyticsController.java          # League stats, pre-match, H2H, trends
│   ├── CacheManagementController.java    # Cache clear, status, warmup
│   ├── CardsController.java              # Cards prediction, team discipline
│   ├── CornerStatsController.java        # Corner statistics & predictions
│   ├── DashboardController.java          # Dashboard widgets & aggregation
│   ├── DataManagementController.java     # CSV reload, DB reset, data update
│   ├── ExpectedGoalsController.java      # Expected goals (xG) stats & predictions
│   ├── ExternalApiController.java        # football-data.org integration
│   ├── FixtureCongestionController.java  # Fixture congestion & fatigue analysis
│   ├── FormGuideController.java          # Team form guide
│   ├── HalfAnalysisController.java       # First-half vs second-half analysis
│   ├── KickoffTimeController.java        # Kickoff time performance analysis
│   ├── LeagueController.java             # Top-4 race, relegation, goals trends
│   ├── MatchHistoryController.java       # Match history & upcoming fixtures
│   ├── ModelPerformanceController.java   # Model accuracy, error analysis
│   ├── ModelTrainingController.java      # ML model training & status
│   ├── NewsController.java               # Football news (RSS feeds)
│   ├── PlayerAvailabilityController.java # Player injury/suspension tracking & API
│   ├── RefereeController.java            # Referee statistics & rankings
│   ├── SeasonTeamStatsController.java    # Season team stats, Elo/form rankings
│   ├── SeasonsController.java            # Season management and stats
│   └── TeamStatsController.java          # Team listings, form, logos, analytics
│
├── service/                              # Business Logic Layer
│   ├── PreMatchInsightsService.java      # Pre-match analysis
│   ├── TrendingInsightsService.java      # Season trends
│   ├── H2HInsightsService.java           # Head-to-head analysis
│   ├── LeagueStandingService.java        # Standings management
│   ├── LeagueStatsService.java           # League-wide statistics
│   ├── TeamStatsService.java             # Team statistics
│   ├── TeamAnalyticsService.java         # Full team analytics
│   ├── TeamValidationService.java        # Team name validation
│   ├── ShotQualityService.java           # Shot efficiency metrics
│   ├── FoulsAnalysisService.java         # Fouls & discipline analysis
│   ├── CornerStatsService.java           # Corner statistics & predictions
│   ├── CardsPredictionService.java       # Cards prediction & team discipline
│   ├── HalfAnalysisService.java          # First/second half analysis
│   ├── ExpectedGoalsService.java         # Expected goals (xG) analytics
│   ├── RefereeStatsService.java          # Referee statistics & rankings
│   ├── FixtureCongestionService.java     # Fixture congestion & fatigue analysis
│   ├── KickoffTimeService.java           # Kickoff time performance analysis
│   ├── EloPredictionService.java         # Elo-based prediction adjustments
│   ├── FootballDataApiService.java       # football-data.org integration
│   ├── NewsService.java                  # RSS news aggregation
│   ├── CsvIngestionService.java          # CSV data loading
│   ├── CacheWarmingService.java          # Cache pre-population
│   ├── CacheStatisticsService.java       # Cache monitoring
│   ├── PredictionTrackingService.java    # Accuracy tracking
│   ├── ModelAccuracyService.java         # Model performance metrics
│   ├── ModelSelfTrainingService.java     # In-app model training
│   ├── MatchCompletionService.java       # Match completion processing
│   ├── MatchResultProcessor.java         # Result processing pipeline
│   ├── InsightsValidationService.java    # Insights data validation
│   ├── FeatureRecalculationService.java  # Feature recomputation
│   ├── HistoricalPredictionGenerator.java # Backfill predictions
│   ├── SeasonStatsService.java           # Season statistics
│   ├── SeasonTeamStatsService.java       # Per-season team stats
│   ├── ApiDataSyncService.java           # API data synchronization
│   ├── TeamService.java                  # Team CRUD
│   ├── AdminService.java                 # Admin operations
│   ├── ScorePredictionService.java       # Dixon-Coles Poisson score prediction
│   ├── PoissonSelfTrainingService.java   # Poisson model self-training at startup
│   ├── PredictionOrchestrationService.java # Full prediction pipeline orchestrator
│   ├── PlayerImpactService.java          # Squad strength & availability impact
│   └── PlayerAvailabilityApiService.java # External player data sync (football-data.org)
│
├── dto/                                  # Data Transfer Objects
│   ├── PredictRequest.java
│   ├── PredictResponse.java
│   ├── PredictionExplanation.java
│   ├── PreMatchInsightsResponse.java
│   ├── TrendingInsightsResponse.java
│   ├── H2HInsightsResponse.java
│   ├── LeagueStandingsResponse.java
│   ├── LeagueStatsResponse.java
│   ├── TeamStatsResponse.java
│   ├── TeamFormResponse.java
│   ├── TeamDTO.java
│   ├── TeamAnalyticsDto.java
│   ├── FoulsAnalysisDTO.java             # Fouls & discipline metrics
│   ├── CornerStatsDTO.java               # Corner statistics
│   ├── CornerPredictionDTO.java          # Corner match prediction
│   ├── CardsPredictionDTO.java           # Cards prediction
│   ├── TeamDisciplineDTO.java            # Team discipline metrics
│   ├── HalfAnalysisDTO.java              # Half-time analysis
│   ├── ExpectedGoalsDTO.java             # Expected goals (xG)
│   ├── MatchXGPredictionDTO.java         # Match xG prediction
│   ├── RefereeStats.java                 # Referee statistics
│   ├── FixtureCongestionDTO.java         # Fixture congestion data
│   ├── CongestionComparisonDTO.java      # Congestion comparison
│   ├── KickoffTimeAnalysisDTO.java       # Kickoff time analysis
│   ├── KickoffTimeStatsDTO.java          # Kickoff time stats
│   ├── ScorePredictionDTO.java           # Dixon-Coles score prediction
│   ├── PlayerAvailabilityDTO.java        # Player availability & impact
│   ├── SeasonStatsResponse.java
│   ├── SeasonTeamStatsResponse.java
│   ├── UpcomingPredictionResponse.java
│   ├── dashboard/                        # Dashboard-specific DTOs
│   │   ├── ModelAccuracyResponse.java
│   │   ├── TodaysPredictionsResponse.java
│   │   ├── TopTeamsResponse.java
│   │   └── UpcomingMatchesResponse.java
│   └── external/                         # External API DTOs
│       ├── FootballApiResponse.java
│       ├── NewsResponse.java
│       └── StandingsResponse.java
│
├── config/                               # Configuration
│   ├── CacheConfig.java                  # Caffeine cache setup (19 caches)
│   ├── FootballApiConfig.java            # Football-data.org API config
│   ├── GlobalExceptionHandler.java       # RFC 7807 ProblemDetail error handling
│   ├── MetricsConfig.java                # Micrometer/Prometheus custom metrics
│   ├── OpenApiConfig.java                # Springdoc OpenAPI / Swagger UI config
│   ├── SecurityConfig.java               # Security configuration
│   ├── RateLimitFilter.java              # API rate limiting
│   ├── RequestLoggingFilter.java         # Request logging
│   ├── TeamLogoSeeder.java               # Team logo initialization
│   ├── WebConfig.java                    # CORS, static resources
│   └── WekaModelConfig.java              # ML model configuration
│
├── exception/                            # Custom Exceptions & Error Codes
│   ├── ErrorCode.java                    # Machine-readable error codes enum
│   ├── ResourceNotFoundException.java    # 404 exception with resource type
│   ├── ValidationException.java          # 400 exception with field errors
│   ├── TeamNotFoundException.java        # Unknown team name
│   ├── ModelNotReadyException.java       # Model not trained yet
│   └── DataSyncException.java            # External data sync failure
│
├── modeltraining/                        # ML Integration
│   ├── ModelTrainingService.java         # Model loading & prediction
│   ├── StackedEnsembleService.java       # Stacked ensemble model
│   ├── EnsembleModelService.java         # Ensemble model management
│   ├── CrossValidationResult.java        # CV result model
│   ├── GridSearchResult.java             # Grid search result model
│   └── ModelComparisonResult.java        # Model comparison result
│
├── ingestion/                            # Data Ingestion Pipeline
│   ├── config/
│   │   └── FeatureFlagService.java       # Feature flags for rollouts
│   ├── controller/
│   │   └── IngestionAdminController.java # Ingestion admin API
│   ├── listener/
│   │   └── CacheInvalidationListener.java # Cache invalidation on data change
│   ├── model/
│   │   ├── IngestionResult.java
│   │   ├── ShadowValidationResult.java
│   │   └── UpsertResult.java
│   ├── orchestrator/
│   │   ├── IngestionOrchestrator.java    # Main orchestration
│   │   ├── IngestionRouter.java          # Data routing
│   │   └── ShadowValidator.java          # Shadow validation
│   ├── provider/
│   │   └── legacy/                       # Legacy data providers
│   └── service/
│       ├── IdempotentUpsertService.java  # Idempotent data upserts
│       └── IngestionMetricsService.java  # Ingestion metrics
│
├── integration/                          # External Integrations
│   └── espn/                             # ESPN data integration
│       ├── client/                       # ESPN API client
│       ├── dto/                          # ESPN DTOs
│       ├── mapper/                       # ESPN data mappers
│       └── service/                      # ESPN service layer
│
├── polling/                              # Smart Polling & SSE
│   ├── controller/
│   │   └── SyncStatusController.java     # Sync status & manual triggers
│   ├── dto/
│   │   ├── MatchDayStatus.java
│   │   └── SystemStatusResponse.java
│   ├── listener/
│   │   └── DashboardRefreshListener.java # Dashboard auto-refresh
│   ├── model/
│   │   ├── PollingResult.java
│   │   └── SyncStatus.java
│   ├── scheduler/
│   │   └── DailyMatchPollingJob.java     # Daily polling cron job
│   ├── service/
│   │   ├── MatchDayService.java          # Match day status
│   │   ├── MatchPollingService.java      # Data polling logic
│   │   └── SmartRetrainService.java      # Smart retrain triggers
│   └── sse/
│       ├── MatchCompletionEvent.java     # SSE event model
│       ├── SseController.java            # SSE endpoints
│       └── SseEmitterService.java        # SSE emitter management
│
├── listener/                             # Application Listeners
│   ├── PredictionBackfillListener.java   # Backfill on startup
│   └── StartupDataSyncListener.java      # Data sync on startup
│
├── scheduler/                            # Scheduled Tasks (4)
│   ├── DailyPredictionScheduler.java     # Daily prediction generation
│   ├── DataUpdateScheduler.java          # Periodic data refresh
│   ├── PlayerAvailabilityScheduler.java  # Daily player injury/suspension sync (10 AM)
│   └── PredictionRecalculationScheduler.java # Prediction recalculation
│
└── util/                                 # Utilities
    └── SeasonUtils.java                  # Season date utilities
```

---

## Core Services

### ExpectedGoalsService

Calculates xG statistics and match predictions using a shots-on-target proxy model.

```java
public class ExpectedGoalsService {

    public ExpectedGoalsDTO getExpectedGoals(String teamName) {
        // xG = avgShotsOnTarget × league conversion rate (0.28)
        // Over/underperformance = actualGoals - xG
        // Team-specific conversion rate for comparison
    }

    public MatchXGPredictionDTO predictXG(String homeTeam, String awayTeam) {
        // Match xG with home advantage factor (1.10×)
        // Over/under goal probabilities
    }
}
```

### RefereeStatsService

Computes referee statistics from match history.

```java
public class RefereeStatsService {

    public RefereeStats getRefereeStats(String refereeName) {
        // Average yellow/red cards per match
        // Strictness relative to league average
        // Result distribution
    }

    public List<RefereeStats> getStrictestReferees() { ... }
    public List<RefereeStats> getLenientReferees() { ... }
}
```

### FixtureCongestionService

Analyzes fixture congestion and fatigue impact.

```java
public class FixtureCongestionService {

    public FixtureCongestionDTO analyzeFixtureCongestion(String teamName, LocalDate asOfDate) {
        // Fatigue index (0-100)
        // Win rate by rest period (short/normal/long)
    }

    public CongestionComparisonDTO compareCongestion(String home, String away) {
        // Head-to-head fatigue comparison
        // Advantage detection
    }
}
```

### KickoffTimeService

Analyzes team performance by kick-off time slot.

```java
public class KickoffTimeService {

    public KickoffTimeAnalysisDTO analyzeByKickoffTime(String teamName) {
        // Time slots: Early (12-13:30), Afternoon (14-16), Late (16:30-18:30), Evening (19-21)
        // Win/draw/loss breakdown per slot
        // Strong/Average/Weak classification
    }
}
```

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

**Discipline Score Calculation:**
```java
double normalizedFouls = (maxFouls - avgFouls) / (maxFouls - minFouls);
disciplineScore = normalizedFouls * 10;
```

### CornerStatsService

Calculates corner kick statistics and match predictions.

**Key Metrics:**
| Metric | Formula |
|--------|---------|
| Corner Dominance | `cornersWon / (cornersWon + cornersAgainst)` |
| Weighted Avg | Exponential decay with factor `0.15` |
| Home Advantage | `1.10` multiplier for home team |

### CardsPredictionService

Predicts yellow/red cards with referee influence.

**Key Metrics:**
| Metric | Formula |
|--------|---------|
| Card Risk Level | Total yellows > 5.0 = HIGH |
| Discipline Rating | Based on avg cards relative to league average |
| Referee Influence | Adjusted by referee's historical card rate |

### HalfAnalysisService

Analyzes team performance by half (first vs second).

**Key Metrics:**
| Metric | Description |
|--------|-------------|
| Pattern | Fast Starter (>60% 1H) / Strong Finisher (>60% 2H) / Balanced |
| Comeback Rate | % of wins after losing at half-time |
| Win from HT Lead | % of wins when leading at half-time |

### TrendingInsightsService

All trending insights are **strictly season-scoped**:

```java
@Cacheable(value = "trendingInsights", key = "#season")
public TrendingInsightsResponse getTrendingInsightsBySeason(String season) {
    Set<String> seasonTeams = getTeamsForSeason(season);
    List<HotTeam> hotTeams = calculateHotTeams(seasonTeams, beforeDate, season);
    List<ColdTeam> coldTeams = calculateColdTeams(seasonTeams, beforeDate, season);
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

### Prediction Endpoints (PredictionController - `/api`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/predict` | POST | Predict match outcome |
| `/api/predict/score` | POST | Predict exact scoreline (Dixon-Coles Poisson) |
| `/api/h2h` | GET | Head-to-head analysis |
| `/api/insights/trending` | GET | Season trending insights |
| `/api/insights/seasons` | GET | Available seasons |
| `/api/predictions` | GET | Historical predictions |
| `/api/predictions/today` | GET | Today's predictions |
| `/api/matches/history` | GET | Match history |
| `/api/matches/{id}` | GET | Match details |
| `/api/matches/upcoming` | GET | Upcoming matches |

### Model Management Endpoints (PredictionController - `/api`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/model/train` | POST | Train basic model |
| `/api/model/train/advanced` | POST | Train with advanced settings |
| `/api/model/train/cv` | POST | Train with cross-validation |
| `/api/model/train/boosting` | POST | Train with boosting |
| `/api/model/train/ensemble` | POST | Train ensemble model |
| `/api/model/train/stacked` | POST | Train stacked ensemble |
| `/api/model/grid-search` | POST | Grid search hyperparameters |
| `/api/model/compare` | GET | Compare model types |
| `/api/model/status` | GET | Model status |
| `/api/model/accuracy` | GET | Model accuracy |
| `/api/model/performance` | GET | Detailed performance |
| `/api/model/feature-importance` | GET | Feature importance |
| `/api/model/sliding-accuracy` | GET | Sliding window accuracy |
| `/api/model/temporal-cv` | GET | Temporal cross-validation |
| `/api/model/retraining-history` | GET | Retraining history |
| `/api/model/evaluations` | GET | Evaluation history |
| `/api/model/retrain` | POST | Trigger retraining |
| `/api/model/train-smote` | POST | Train with SMOTE |

### Team Statistics Endpoints (TeamStatsController - `/api/teams`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/teams/{name}/stats` | GET | Team statistics |
| `/api/teams/{name}/analytics` | GET | Full team analytics |
| `/api/teams/{name}/shot-quality` | GET | Shot quality metrics |
| `/api/teams/{name}/fouls-analysis` | GET | Fouls & discipline |
| `/api/teams/{name}/corner-stats` | GET | Corner statistics |
| `/api/teams/{name}/half-analysis` | GET | Half-time analysis |
| `/api/teams/{name}/expected-goals` | GET | Expected goals (xG) |
| `/api/teams/{name}/expected-goals/split` | GET | xG home/away split |
| `/api/teams/{name}/kickoff-analysis` | GET | Kickoff time analysis |
| `/api/teams/{name}/fixture-congestion` | GET | Fixture congestion |
| `/api/teams/{name}/discipline` | GET | Team discipline metrics |
| `/api/teams/logos` | GET | All team logos |
| `/api/teams/search` | GET | Search teams |
| `/api/teams/summary` | GET | All teams summary |
| `/api/teams/compare` | GET | Compare two teams |

### Match Prediction Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/matches/predict-corners` | GET | Corner prediction |
| `/api/matches/predict-cards` | GET | Cards prediction |
| `/api/matches/predict-xg` | GET | xG prediction |
| `/api/matches/congestion-comparison` | GET | Fatigue comparison |

### Player Availability Endpoints (PlayerAvailabilityController - `/api/availability`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/availability/team` | GET | Team squad availability & strength |
| `/api/availability/all` | GET | All teams' availability overview |
| `/api/availability/match` | GET | Match availability context (both teams) |
| `/api/availability/update` | POST | Update player injury/suspension status |
| `/api/availability/sync` | POST | Trigger manual player data sync |

### Referee Endpoints (RefereeController - `/api/referees`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/referees` | GET | All referee names |
| `/api/referees/stats` | GET | All referee statistics |
| `/api/referees/{name}` | GET | Specific referee stats |
| `/api/referees/strictest` | GET | Strictest referees |
| `/api/referees/lenient` | GET | Most lenient referees |

### Season Endpoints (SeasonsController - `/api/seasons`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/seasons` | GET | All available seasons |
| `/api/seasons/{year}/stats` | GET | Season statistics |

### Season Team Stats (SeasonTeamStatsController - `/api/season`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/season/{id}/team/{teamId}/stats` | GET | Team stats for season |
| `/api/season/{id}/team/stats` | GET | All team stats for season |
| `/api/season/{id}/stats` | GET | Season overview stats |
| `/api/season/{id}/elo-rankings` | GET | Elo rankings |
| `/api/season/{id}/form-rankings` | GET | Form rankings |
| `/api/season/{id}/winning-streaks` | GET | Winning streaks |
| `/api/season/{id}/losing-streaks` | GET | Losing streaks |
| `/api/season/{id}/recalculate` | POST | Recalculate stats |
| `/api/season/team/{teamId}/history` | GET | Team history across seasons |

### Dashboard Endpoints (PredictionController - `/api/dashboard`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/dashboard/upcoming-matches` | GET | Upcoming fixtures |
| `/api/dashboard/league-standings` | GET | League table |
| `/api/dashboard/todays-predictions` | GET | Today's predictions |
| `/api/dashboard/top-teams` | GET | Top performing teams |
| `/api/dashboard/model-accuracy` | GET | Model accuracy stats |
| `/api/dashboard/stats` | GET | Dashboard statistics |
| `/api/dashboard/accuracy` | GET | Accuracy overview |
| `/api/dashboard/activity` | GET | Recent activity |
| `/api/dashboard/available-leagues` | GET | Available leagues |
| `/api/dashboard/available-seasons` | GET | Available seasons |

### Cache Management Endpoints (PredictionController - `/api/cache`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/cache/clear` | POST | Clear all caches |
| `/api/cache/clear/{name}` | POST | Clear specific cache |
| `/api/cache/status` | GET | Cache status |
| `/api/cache/warmup` | GET/POST | Cache warm-up |
| `/api/cache/stats/{name}` | GET | Cache statistics |
| `/api/cache/invalidate/{name}` | POST | Invalidate cache |

### Data Management Endpoints (PredictionController - `/api/data`)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/data/reload` | POST | Reload data |
| `/api/data/reset` | POST | Reset data |
| `/api/data/update` | POST | Update data |

### Polling & SSE Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/events/match-completion` | GET (SSE) | Match completion event stream |
| `/api/events/match-completion/{date}` | GET (SSE) | Date-specific event stream |
| `/api/events/status` | GET | SSE connection status |
| `/api/sync-status` | GET | Sync status |
| `/api/sync-status/detailed` | GET | Detailed sync status |
| `/api/match-day-status` | GET | Match day status |
| `/api/system-status` | GET | System health |
| `/api/poll/trigger` | POST | Manual poll trigger |
| `/api/poll/trigger-with-retrain` | POST | Poll + retrain |
| `/api/retrain/trigger` | POST | Manual retrain |
| `/api/retrain/status` | GET | Retrain status |
| `/api/retrain/pending-data` | GET | Pending data for retrain |

---

## API Documentation (OpenAPI / Swagger)

The application ships with **Springdoc OpenAPI 3** integration, providing interactive API documentation out of the box.

### Access

| Resource | URL |
|----------|-----|
| **Swagger UI** | [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html) |
| **OpenAPI JSON** | [`http://localhost:8080/v3/api-docs`](http://localhost:8080/v3/api-docs) |
| **OpenAPI YAML** | [`http://localhost:8080/v3/api-docs.yaml`](http://localhost:8080/v3/api-docs.yaml) |

All Swagger/OpenAPI endpoints are publicly accessible (no authentication required).

### API Groups

The Swagger UI organises endpoints into four logical groups selectable from the top drop-down:

| Group | Display Name | Paths Included |
|-------|-------------|----------------|
| `predictions` | Predictions | `/api/predict`, `/api/predictions/**`, `/api/h2h`, `/api/insights/**`, `/api/external/**` |
| `analytics` | Analytics | `/api/analytics/**`, `/api/league/**`, match-level predictions (corners, cards, xG, congestion), team analysis endpoints |
| `teams` | Teams | `/api/teams`, `/api/referees/**`, `/api/seasons/**`, `/api/season/**`, `/api/news/**` |
| `health` | Health & Admin | `/api/model/**`, `/api/dashboard/**`, `/api/admin/**`, `/api/cache/**`, `/api/data/**`, `/api/matches/**` |

### Controller Tags

Every controller is annotated with `@Tag` so endpoints are grouped logically in the UI:

| Tag | Controllers |
|-----|-------------|
| **Predictions** | `PredictionController`, `ExternalApiController` |
| **Analytics** | `AnalyticsController`, `CardsController`, `CornerStatsController`, `ExpectedGoalsController`, `FixtureCongestionController`, `HalfAnalysisController`, `KickoffTimeController` |
| **Teams** | `TeamStatsController`, `FormGuideController`, `SeasonTeamStatsController`, `SeasonsController` |
| **League** | `LeagueController` |
| **Dashboard** | `DashboardController` |
| **Model** | `ModelTrainingController`, `ModelPerformanceController` |
| **Admin** | `AdminController` (secured with `@SecurityRequirement(name = "basicAuth")`) |
| **Cache** | `CacheManagementController` |
| **Data Management** | `DataManagementController` |
| **Matches** | `MatchHistoryController` |
| **News** | `NewsController` |
| **Referees** | `RefereeController` |

### DTO Schema Annotations

Key request/response DTOs are annotated with `@Schema` for rich documentation in the Swagger UI:

- **`PredictRequest`** — `homeTeam` and `awayTeam` with examples and validation constraints
- **`PredictResponse`** — Prediction outcome, probabilities, confidence, and allowable values
- **`TeamDTO`** — Team name, logo, position, zone, and status with examples

### Configuration

Springdoc properties in `application.properties`:

```properties
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.tags-sorter=alpha
springdoc.swagger-ui.operations-sorter=method
springdoc.swagger-ui.display-request-duration=true
springdoc.default-produces-media-type=application/json
springdoc.show-actuator=false
```

---

## Error Handling (RFC 7807)

All error responses use **RFC 7807 Problem Detail** (`application/problem+json`) via Spring's `ProblemDetail` class. The `GlobalExceptionHandler` (`@RestControllerAdvice`) converts every exception into a consistent response body.

### Response Structure

```json
{
  "type": "/errors/invalid-team-name",
  "title": "Bad Request",
  "status": 400,
  "detail": "Team not found: FooFC",
  "errorCode": "INVALID_TEAM_NAME",
  "timestamp": "2026-03-14T10:30:00Z",
  "hint": "Use GET /api/teams to see valid team names"
}
```

| Field | Description |
|-------|-------------|
| `type` | URI identifying the error category (e.g. `/errors/validation-failed`) |
| `title` | HTTP status reason phrase |
| `status` | HTTP status code |
| `detail` | Human-readable explanation |
| `errorCode` | Machine-readable `ErrorCode` enum value |
| `timestamp` | ISO-8601 timestamp |
| Extra fields | `hint`, `fieldErrors`, `resourceType`, `identifier`, etc. as applicable |

### ErrorCode Enum

| Code | HTTP Status | Meaning |
|------|-------------|---------|
| `PREDICTION_NOT_FOUND` | 404 | The requested prediction does not exist |
| `RESOURCE_NOT_FOUND` | 404 | Generic resource not found |
| `INVALID_TEAM_NAME` | 400 | The supplied team name is not recognised |
| `VALIDATION_FAILED` | 400 | One or more fields failed bean validation |
| `INVALID_REQUEST` | 400 | Malformed or invalid request data |
| `MODEL_NOT_TRAINED` | 503 | The ML model has not been trained yet |
| `DATA_SYNC_FAILED` | 500 | External data synchronisation failed |
| `INTERNAL_ERROR` | 500 | Unexpected internal error |

### Custom Exceptions

| Exception | Status | Error Code | Use Case |
|-----------|--------|------------|----------|
| `ResourceNotFoundException` | 404 | Configurable | Any missing resource with `resourceType` + `identifier` |
| `ValidationException` | 400 | Configurable | Business validation with `fieldErrors` map |
| `TeamNotFoundException` | 400 | `INVALID_TEAM_NAME` | Unknown team name supplied by user |
| `ModelNotReadyException` | 503 | `MODEL_NOT_TRAINED` | Prediction requested before training |
| `DataSyncException` | 500 | `DATA_SYNC_FAILED` | External API sync failure |

### Handled Spring Exceptions

| Exception | Status | Trigger |
|-----------|--------|---------|
| `MethodArgumentNotValidException` | 400 | `@Valid` bean validation failure |
| `MissingServletRequestParameterException` | 400 | Missing required query parameter |
| `MethodArgumentTypeMismatchException` | 400 | Wrong parameter type |
| `HttpMessageNotReadableException` | 400 | Invalid JSON body |
| `HttpRequestMethodNotSupportedException` | 405 | Wrong HTTP method |
| `NoHandlerFoundException` | 404 | Unknown endpoint |
| `IllegalArgumentException` | 400 | Business logic error |
| `IllegalStateException` | 503 | Service not ready |
| `NullPointerException` | 500 | Defensive catch-all |
| `Exception` | 500 | Generic catch-all |

---

## Observability (Prometheus & Grafana)

The application exposes Prometheus-compatible metrics via **Micrometer** and Spring Boot Actuator. A pre-built **Grafana** dashboard is included for immediate visibility.

### Dependencies

```xml
<!-- Already included in pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Actuator Endpoints

| Endpoint | Access | Description |
|----------|--------|-------------|
| `/actuator/prometheus` | Public | Prometheus scrape target (all metrics) |
| `/actuator/health` | Public | Health indicators (DB, disk, model) |
| `/actuator/metrics` | Public | Available metric names |
| `/actuator/metrics/{name}` | Public | Single metric detail |

All actuator paths above are permitted in `SecurityConfig` without authentication.

### Custom Metrics (`MetricsConfig.java`)

| Metric | Micrometer Type | Tags | Description |
|--------|----------------|------|-------------|
| `prediction.requests.total` | `Counter` | `outcome` = HOME / DRAW / AWAY | Prediction requests segmented by predicted outcome |
| `prediction.latency` | `Timer` | — | End-to-end ML inference time (p50 / p95 / p99 histograms) |
| `model.accuracy.current` | `Gauge` | — | Winner-prediction accuracy over the trailing 30 days |
| `cache.hits` | `FunctionCounter` | `cache` | Caffeine cache hit count per cache name |
| `cache.misses` | `FunctionCounter` | `cache` | Caffeine cache miss count per cache name |
| `cache.size` | `Gauge` | `cache` | Estimated entry count per cache |

These are registered in `MetricsConfig` and automatically instrumented inside `PredictionOrchestrationService`.

### Auto-instrumented Metrics (Spring Boot)

Spring Boot and Micrometer automatically provide:

| Category | Key Metrics |
|----------|------------|
| **HTTP** | `http.server.requests` (count, sum, max per URI + status + method) |
| **JVM Memory** | `jvm.memory.used`, `jvm.memory.committed`, `jvm.memory.max` |
| **JVM GC** | `jvm.gc.pause`, `jvm.gc.memory.allocated` |
| **JVM Threads** | `jvm.threads.live`, `jvm.threads.daemon`, `jvm.threads.peak` |
| **HikariCP** | `hikaricp.connections.active`, `hikaricp.connections.idle` |
| **Process** | `process.cpu.usage`, `process.uptime`, `system.cpu.usage` |

### Grafana Dashboard

The pre-built dashboard is auto-provisioned when using Docker Compose:

| Panel | Query Highlights |
|-------|-----------------|
| ⚽ Prediction Request Rate | `rate(prediction_requests_total[…])` stacked by outcome |
| ⏱️ Prediction Latency | `histogram_quantile(0.99, …prediction_latency_seconds_bucket…)` |
| 🎯 Prediction Distribution | Donut of total prediction counts by outcome |
| 📊 Model Accuracy | Gauge of `model_accuracy_current` |
| 💾 Cache Hit Rate | `cache_hits / (cache_hits + cache_misses)` |
| 🔥 Cache Hits vs Misses | Top-10 caches by hit/miss rate |
| ☕ JVM Heap Memory | `jvm_memory_used_bytes{area="heap"}` |
| 🌐 HTTP Request Rate | By status code (2xx / 4xx / 5xx) |
| 🧵 JVM Threads | Live / Daemon / Peak |
| 🗑️ GC Pause Time | `rate(jvm_gc_pause_seconds_sum[…])` |

### Configuration Properties

```properties
# Actuator endpoints exposed
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true
management.metrics.tags.application=${spring.application.name}

# Histogram buckets for prediction latency
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.distribution.percentiles.prediction.latency=0.5,0.95,0.99
```

---

## Caching Configuration

```java
// CacheConfig.java - 19 cache definitions
public static final String CACHE_TEAM_ANALYTICS = "teamAnalytics";         // 15 min TTL
public static final String CACHE_PRE_MATCH_INSIGHTS = "preMatchInsights";  // 10 min TTL
public static final String CACHE_LEAGUE_STATS = "leagueStats";             // 10 min TTL
public static final String CACHE_ELO_RATINGS = "eloRatings";               // 10 min TTL
public static final String CACHE_SHOT_QUALITY = "shotQuality";             // 10 min TTL
public static final String CACHE_FOULS_ANALYSIS = "foulsAnalysis";         // 10 min TTL
public static final String CACHE_CORNER_STATS = "cornerStats";             // 10 min TTL
public static final String CACHE_CORNER_PREDICTION = "cornerPrediction";   // 10 min TTL
public static final String CACHE_CARDS_PREDICTION = "cardsPrediction";     // 10 min TTL
public static final String CACHE_TEAM_DISCIPLINE = "teamDiscipline";       // 10 min TTL
public static final String CACHE_HALF_ANALYSIS = "halfAnalysis";           // 10 min TTL
public static final String CACHE_EXPECTED_GOALS = "expectedGoals";         // 10 min TTL
public static final String CACHE_XG_PREDICTION = "xgPrediction";           // 10 min TTL
public static final String CACHE_KICKOFF_TIME_ANALYSIS = "kickoffTimeAnalysis"; // 10 min TTL
public static final String CACHE_FIXTURE_CONGESTION = "fixtureCongestion"; // 10 min TTL
public static final String CACHE_API_SYNC = "apiSync";                     // 5 min TTL
public static final String CACHE_REFEREE_STATS = "refereeStats";           // 10 min TTL
public static final String CACHE_ALL_REFEREES = "allReferees";             // 10 min TTL
public static final String CACHE_ALL_REFEREE_STATS = "allRefereeStats";   // 10 min TTL
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
| `GlobalExceptionHandlerTest` | RFC 7807 error handling (13 handler tests via MockMvc) |
| `SecurityConfigTest` | Auth, authorization, security headers, CORS |
| `TrendingInsightsServiceTest` | Season-aware trending insights |
| `PreMatchInsightsServiceTest` | Pre-match analysis |
| `FoulsAnalysisServiceTest` | Fouls & discipline |
| `CardsPredictionServiceTest` | Cards prediction |
| `HalfAnalysisServiceTest` | Half-time analysis |
| `HalfAnalysisControllerTest` | Controller integration |
| `EloPredictionServiceTest` | Elo prediction logic |
| `ModelAccuracyServiceTest` | Model accuracy tracking |
| `TeamAnalyticsServiceTest` | Team analytics |
| `TeamStatsServiceTest` | Team statistics |
| `H2HInsightsServiceTest` | H2H analysis |
| `LeagueStandingServiceTest` | League standings |
| `InsightsValidationServiceTest` | Data validation |
| `MatchCompletionServiceTest` | Match completion |
| `ApiDataSyncServiceTest` | API sync |
| `DailyPollingSystemTest` | Polling system |
| `SystemStatusApiTest` | System status API |

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
| Controllers | 24 (main) + 3 (polling/ingestion/SSE) |
| Services | 55+ |
| REST Endpoints | 80+ |
| Scheduled Jobs | 4 (predictions, data sync, retrain, player availability) |
| Cache Definitions | 19 |
| DTOs | 47+ |
| Config Classes | 11 |
| Custom Exceptions | 5 |
| Error Codes | 8 |
| OpenAPI Groups | 4 (predictions, analytics, teams, health) |
| Custom Prometheus Metrics | 6 (counter, timer, gauge, cache stats) |
| Grafana Dashboard Panels | 10 |

---

**[← Back to Main README](../README.md)**

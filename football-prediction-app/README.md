# Football Prediction Application

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
│  │  │  • LeagueStandingService    • DashboardService   │    │                │
│  │  │  • FootballDataApiService   • NewsService        │    │                │
│  │  └─────────────────────────────────────────────────┘    │                │
│  │                          │                              │                │
│  │                          ▼                              │                │
│  │  ┌───────────────┐  ┌────────────────┐                  │                │
│  │  │ Caffeine Cache│  │ ML Model       │                  │                │
│  │  │ (14 caches)   │  │ Integration    │                  │                │
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

---

## Architecture

### Package Structure

```
com.app.footballprediction/
├── FootballPredictionApplication.java    # Spring Boot entry point
│
├── controller/                           # REST API Layer
│   ├── PredictionController.java         # Match predictions (1251 lines)
│   ├── AnalyticsController.java          # Pre-match & H2H insights
│   ├── DashboardController.java          # Dashboard widgets
│   ├── TeamStatsController.java          # Team statistics
│   ├── ExternalApiController.java        # External API proxy
│   ├── AdminController.java              # Admin operations
│   ├── NewsController.java               # News aggregation
│   └── SeasonsController.java            # Season management
│
├── service/                              # Business Logic Layer
│   ├── PreMatchInsightsService.java      # Pre-match analysis
│   ├── TrendingInsightsService.java      # Season trends (670 lines)
│   ├── H2HInsightsService.java           # Head-to-head analysis
│   ├── LeagueStandingService.java        # Standings management
│   ├── LeagueStatsService.java           # League-wide statistics
│   ├── TeamStatsService.java             # Team statistics
│   ├── TeamAnalyticsService.java         # Full team analytics
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
│   └── dashboard/                        # Dashboard-specific DTOs
│
├── config/                               # Configuration
│   ├── CacheConfig.java                  # Caffeine cache setup (294 lines)
│   ├── TeamLogoSeeder.java               # Team logo initialization
│   └── WebConfig.java                    # CORS, static resources
│
├── modeltraining/                        # ML Integration
│   └── ModelTrainingService.java         # Model loading & prediction
│
└── scheduler/                            # Scheduled Tasks
    └── DataUpdateScheduler.java          # Periodic data refresh
```

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

## Core Business Logic

### Trending Insights Engine

All trending insights are **strictly season-scoped** to prevent cross-season data contamination:

```java
@Cacheable(value = "trendingInsights", key = "#season")
public TrendingInsightsResponse getTrendingInsightsBySeason(String season) {
    // Get teams that played in this season only
    Set<String> seasonTeams = getTeamsForSeason(season);
    
    // Calculate insights using season-filtered data
    List<HotTeam> hotTeams = calculateHotTeams(seasonTeams, beforeDate, season);
    List<ColdTeam> coldTeams = calculateColdTeams(seasonTeams, beforeDate, season);
    // ...
}
```

#### Threshold Logic

| Insight | Threshold | Query Pattern |
|---------|-----------|---------------|
| Hot Teams | `winStreak >= 3` OR `winsInLast5 >= 4` | `WHERE season = :season AND fullTimeResult IS NOT NULL` |
| Cold Teams | `matchesWithoutWin >= 5` | `WHERE season = :season AND fullTimeResult IS NOT NULL` |
| Top Scorers | `ORDER BY goalsScored DESC LIMIT 5` | Season aggregate |
| Defensive Walls | `ORDER BY cleanSheets DESC LIMIT 5` | Season aggregate |
| Upset Alerts | `awayWinProbability > 0.5` | ML prediction on upcoming |
| Goal Fest | `expectedGoals > threshold` | Combined averages |

#### Hot Teams Calculation

```java
private List<HotTeam> calculateHotTeams(Set<String> teams, LocalDate beforeDate, String season) {
    // First pass: Find consecutive winning streaks
    for (String team : teams) {
        List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
        
        if (matches.size() < HOT_FORM_WINDOW) continue;  // Min 5 matches required
        
        int winStreak = calcWinStreak(matches, team);
        
        if (winStreak >= HOT_STREAK_THRESHOLD) {  // 3+ consecutive wins
            hotTeams.add(buildHotTeam(team, matches, winStreak));
        }
    }
    
    // Second pass: Include hot form teams (4+ wins in last 5, not consecutive)
    // ...
    
    return hotTeams.stream().limit(TOP_N_RESULTS).toList();  // LIMIT 5
}
```

### Pre-Match Insights Formula

| Insight | Formula |
|---------|---------|
| Goal Threat | `MIN(100, MAX(0, (goalsScoredAvg * 30) + (opponentConcededAvg * 20)))` |
| Over 2.5 Probability | `(homeGoalsAvg + awayGoalsAvg) / 5.0` (normalized) |
| BTTS % | `scoringRate * opponentScoringRate` |
| Fatigue Warning | `daysSinceLastMatch < 4` |

### Prediction Confidence Levels

```java
public static String getConfidence(double[] probabilities) {
    double maxProb = Arrays.stream(probabilities).max().orElse(0);
    
    if (maxProb >= 0.55) return "HIGH";
    if (maxProb >= 0.45) return "MEDIUM";
    return "LOW";
}
```

---

## Data Dependencies

### Database Tables

| Table | Service | Usage |
|-------|---------|-------|
| `matches` | All services | Historical data |
| `teams` | TeamService, TeamStatsService | Team metadata |
| `leagues` | LeagueStandingService | League config |
| `league_standings` | LeagueStandingService | Season standings |
| `predictions` | PredictionTrackingService | Accuracy tracking |

### External APIs

| API | Service | Rate Limit |
|-----|---------|------------|
| football-data.org | FootballDataApiService | 10 req/min (free tier) |
| BBC Sport RSS | NewsService | No limit |
| Sky Sports RSS | NewsService | No limit |

### Caching Configuration

```java
// CacheConfig.java - 14 cache definitions
public static final String CACHE_STANDINGS = "standings";           // 5 min TTL
public static final String CACHE_MATCHES = "matches";               // 5 min TTL
public static final String CACHE_NEWS = "news";                     // 15 min TTL
public static final String CACHE_PREDICTIONS = "predictions";       // 1 min TTL
public static final String CACHE_TEAM_STATS = "teamStats";         // 10 min TTL
public static final String CACHE_TEAM_FORM = "teamForm";           // 10 min TTL
public static final String CACHE_TEAM_LOGOS = "teamLogos";         // 60 min TTL
public static final String CACHE_H2H_INSIGHTS = "h2hInsights";     // 10 min TTL
public static final String CACHE_TRENDING_INSIGHTS = "trendingInsights";  // 5 min TTL
public static final String CACHE_API_RESPONSES = "apiResponses";   // 5 min TTL
public static final String CACHE_SEASONS = "seasons";              // 60 min TTL
public static final String CACHE_SEASON_STATS = "seasonStats";     // 30 min TTL
public static final String CACHE_TEAM_ANALYTICS = "teamAnalytics"; // 15 min TTL
public static final String CACHE_PRE_MATCH_INSIGHTS = "preMatchInsights";  // 10 min TTL
```

---

## Performance Design

### Index Utilization

Services are designed to leverage database indexes:

```sql
-- Queries use indexed columns
WHERE season = :season              -- idx_match_season
  AND fullTimeResult IS NOT NULL
  AND matchDate < :beforeDate       -- idx_match_date
ORDER BY matchDate DESC
```

### N+1 Prevention Strategies

#### 1. Match Caching in Trending Insights

```java
private List<HotTeam> calculateHotTeams(Set<String> teams, LocalDate beforeDate, String season) {
    Map<String, List<Match>> matchCache = new HashMap<>();
    
    // First pass - cache matches
    for (String team : teams) {
        List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
        matchCache.put(team, matches);  // Cache for reuse in second pass
    }
    
    // Second pass - use cached data
    for (String team : teams) {
        List<Match> matches = matchCache.get(team);  // O(1) lookup
        // ...
    }
}
```

#### 2. Batch Team Logo Lookup

```java
// Pre-fetch all logos once
Map<String, String> teamLogos = teamRepository.findAll().stream()
    .collect(Collectors.toMap(Team::getName, Team::getLogoUrl));

// Use in loop - O(1) per team
for (Match match : matches) {
    String homeLogo = teamLogos.get(match.getHomeTeam());
    String awayLogo = teamLogos.get(match.getAwayTeam());
}
```

### Query Constraints

| Service | Constraint | Rationale |
|---------|------------|-----------|
| TrendingInsightsService | `LIMIT 5` per category | Display limit |
| PreMatchInsightsService | `LIMIT 5` for form window | Recent form only |
| H2HInsightsService | `LIMIT 5` for recent meetings | UI display |
| DashboardService | `LIMIT 10` for upcoming | Dashboard size |

### Response Time Targets

| Endpoint | Target | Strategy |
|----------|--------|----------|
| `/api/predict` | <200ms | Pre-loaded model |
| `/api/dashboard/*` | <300ms | Caffeine cache |
| `/api/analytics/*` | <500ms | Season-filtered queries |
| `/api/insights/trending` | <500ms | Cached results |

---

## Edge Case Handling

### Null/Empty Data

```java
// TrendingInsightsService
private TrendingInsightsResponse buildEmptyResponse() {
    return TrendingInsightsResponse.builder()
        .hotTeams(Collections.emptyList())
        .coldTeams(Collections.emptyList())
        .topScorers(Collections.emptyList())
        .defensiveWalls(Collections.emptyList())
        .upsetAlerts(Collections.emptyList())
        .goalFestMatches(Collections.emptyList())
        .totalTeamsAnalyzed(0)
        .season(null)
        .build();
}
```

### Minimum Match Requirements

```java
// Require minimum matches to prevent false positives
if (matches.size() < HOT_FORM_WINDOW) continue;  // Skip new teams
if (matches.size() < COLD_STREAK_THRESHOLD) continue;
```

### Division by Zero

```java
// Safe percentage calculation
double percentage = total > 0 ? (double) count / total * 100 : 0.0;
```

### Missing Season Data

```java
// Validate season exists
List<String> availableSeasons = getAvailableSeasons();
if (!availableSeasons.contains(season)) {
    log.warn("Season {} not found. Available: {}", season, availableSeasons);
    return buildEmptyResponse();
}
```

### Invalid Team Names

```java
// Team validation in controller
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
| `PreMatchInsightsServiceTest` | Pre-match calculations |
| `H2HInsightsServiceTest` | Head-to-head analysis |
| `LeagueStandingServiceTest` | Standings calculations |

### Test Scenarios

```java
// 1. Hot teams calculation
@Test
void identifiesHotTeamsWithConsecutiveWins() {
    // Given: Team with 4 consecutive wins
    // When: Calculate hot teams
    // Then: Team appears in hot teams list
}

// 2. Season isolation
@Test
void doesNotMixCrossSeasonData() {
    // Given: Team with wins in 2024-25 and 2025-26
    // When: Query for 2025-26 season
    // Then: Only 2025-26 wins counted
}

// 3. Empty season handling
@Test
void returnsEmptyResponseForMissingSeason() {
    TrendingInsightsResponse response = service.getTrendingInsightsBySeason("9999-00");
    assertThat(response.getHotTeams()).isEmpty();
}

// 4. Cache behavior
@Test
void cachesInsightsPerSeason() {
    // First call - cache miss
    service.getTrendingInsightsBySeason("2025-26");
    // Second call - cache hit
    service.getTrendingInsightsBySeason("2025-26");
    verify(matchRepository, times(1)).findBySeasonOrderByMatchDateDesc("2025-26");
}
```

### Integration Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class PredictionApiIntegrationTest {
    
    @Test
    void predictReturnsValidResponse() throws Exception {
        mockMvc.perform(post("/api/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"homeTeam\":\"Arsenal\",\"awayTeam\":\"Chelsea\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.prediction").exists())
            .andExpect(jsonPath("$.probHomeWin").isNumber());
    }
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
football.api.competition=PL

# Model
model.output.path=./data/match_predictor.model
model.type=STACKED_ENSEMBLE

# Feature Engineering
feature.form.window=5

# Scheduler
scheduler.enabled=true
scheduler.cron=0 0 6 * * MON,FRI

# Cache TTLs (seconds)
cache.standings.ttl=300
cache.trending.ttl=300
cache.teamStats.ttl=600
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FOOTBALL_API_KEY` | - | football-data.org API key |
| `ADMIN_USERNAME` | admin | Admin panel username |
| `ADMIN_PASSWORD` | - | Admin panel password (set your own) |

---

## Future Enhancements

| Enhancement | Description | Priority |
|-------------|-------------|----------|
| WebSocket Updates | Real-time prediction updates | High |
| GraphQL API | Alternative query interface | Medium |
| Player Analytics | Individual player impact | Medium |
| xG Integration | Expected goals data | Medium |
| Multi-league | La Liga, Bundesliga support | Low |

---

## Metrics

| Metric | Value |
|--------|-------|
| Lines of Code | ~15,000 |
| Controllers | 8 |
| Services | 18 |
| REST Endpoints | 40+ |
| Cache Definitions | 14 |
| DTOs | 20+ |

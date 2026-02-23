# ⚽ Football Match Prediction & Insights Platform

A **Season-aware AI-powered Football Match Prediction & Insights Platform** built with Spring Boot and Machine Learning. This production-ready application predicts Premier League match outcomes using advanced ensemble learning while providing comprehensive pre-match insights, season-based trending analytics, and historical performance tracking.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Weka ML](https://img.shields.io/badge/Weka-3.8.6-0052CC?style=for-the-badge&logo=apache&logoColor=white)](https://www.cs.waikato.ac.nz/ml/weka/)
[![H2 Database](https://img.shields.io/badge/H2-Embedded-0000BB?style=for-the-badge&logo=databricks&logoColor=white)](https://www.h2database.com/)
[![Docker](https://img.shields.io/badge/Docker-Supported-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

---

## 📋 Table of Contents

- [Project Overview](#-project-overview)
- [Core Features](#-core-features)
- [Architecture](#-architecture)
- [API Documentation](#-api-documentation)
- [Database Schema](#-database-schema)
- [How It Works](#-how-it-works)
- [Setup Instructions](#-setup-instructions)
- [Production Considerations](#-production-considerations)
- [Future Roadmap](#-future-roadmap)

---

## 🎯 Project Overview

This platform is designed for **football analytics enthusiasts** who want data-driven match predictions and comprehensive team insights. The system combines:

- **Machine Learning Predictions**: Stacked ensemble model (RandomForest + AdaBoostM1 + Logistic Regression)
- **Season-aware Analytics**: All insights are computed within season boundaries—no cross-season data mixing
- **Real-time Data Integration**: Live data from football-data.org API for upcoming matches and standings
- **Historical Analysis**: 22 seasons of Premier League data (2004/05 - 2025/26)

### Key Differentiators

| Feature | Description |
|---------|-------------|
| **Season Isolation** | Hot teams, cold teams, and all metrics are strictly per-season |
| **25 ML Features** | Form, goals, H2H, shots, corners, streaks, rest days |
| **Pre-Match Insights** | Goal threat, fatigue warnings, BTTS probability |
| **Multi-module Architecture** | Separate prediction app and training service |

---

## ✨ Core Features

### 🤖 Match Prediction Engine

The prediction engine uses a **Stacked Ensemble ML Model**:

```
┌─────────────────────────────────────────────┐
│           Stacked Ensemble Model            │
├─────────────────────────────────────────────┤
│  Base Models:                               │
│  ├── RandomForest (100 trees)               │
│  └── AdaBoostM1 (100 iterations)            │
│                                             │
│  Meta-Model:                                │
│  └── Logistic Regression                    │
│                                             │
│  Output: H (Home Win) / D (Draw) / A (Away) │
└─────────────────────────────────────────────┘
```

- **25 Engineered Features** across 3 phases
- **Confidence Levels**: HIGH / MEDIUM / LOW based on probability distribution
- **Automatic Retraining**: Scheduled bi-monthly (1st & 15th @ 3 AM)

### 📊 Pre-Match Insights

Comprehensive analysis generated for every match prediction:

| Insight | Description |
|---------|-------------|
| **Form Comparison** | Home vs Away form points (last 5 matches) |
| **Streak Indicators** | 🔥 Win streaks, unbeaten streaks |
| **Rest Days Warning** | ⚠️ Fatigue indicator (<4 days rest) |
| **Goal Threat Meter** | Offensive threat percentage (0-100%) |
| **Over/Under 2.5** | Probability based on goal averages |
| **BTTS %** | Both teams to score probability |

### 🔥 Season-wise Trending Insights

All trending insights are calculated **strictly within the selected season**:

| Insight | Criteria |
|---------|----------|
| **🔥 Hot Teams** | Teams on 3+ match winning streaks |
| **❄️ Cold Teams** | Teams without a win in 5+ matches |
| **⚽ Top Scorers** | Highest-scoring teams (season aggregate) |
| **🧱 Defensive Walls** | Most clean sheets in the season |
| **🎯 Upset Alerts** | Away team favored (>50% win probability) |
| **🎉 Goal Fest** | Matches with highest expected total goals |

### 🆚 Head-to-Head Analytics

- **Historical Record**: "Arsenal leads 15-8-7 vs Chelsea"
- **Last 5 Meetings**: Scores and outcomes
- **H2H Goal Stats**: Average goals when these teams meet
- **Common Results**: Most frequent scoreline
- **Venue Advantage**: H2H win % at home vs away

### 🏆 League Standings

- **Current Season Table**: Real-time Premier League standings
- **Season Filter**: View historical standings by season
- **Zone Indicators**: Champions League, Europa League, Relegation
- **Form Column**: Last 5 match results (W/D/L)

### 📅 Upcoming Matches

- **Match-day Predictions**: Auto-predictions for scheduled fixtures
- **Date-based Filtering**: Today/Tomorrow/Weekend quick filters
- **Live Data Integration**: Fetched from football-data.org API

### 📈 Model Accuracy Tracking

- **Overall Accuracy**: Current model performance
- **High Confidence Accuracy**: Hit rate for HIGH confidence predictions
- **Trend Indicator**: Performance trend (UP/DOWN/STABLE)

---

## 🏗️ Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        FOOTBALL PREDICTION PLATFORM                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────┐   ┌────────────────────────────┐   │
│  │      Main Application (8080)        │   │  Training Service (8081)   │   │
│  │                                     │   │                            │   │
│  │  ┌───────────────────────────────┐  │   │  ┌──────────────────────┐  │   │
│  │  │        Controllers            │  │   │  │   Training API       │  │   │
│  │  │  • PredictionController       │  │   │  │  POST /train         │  │   │
│  │  │  • AnalyticsController        │  │   │  │  POST /test          │  │   │
│  │  │  • DashboardController        │  │   │  │  GET  /model-info    │  │   │
│  │  │  • TeamStatsController        │  │   │  └──────────────────────┘  │   │
│  │  │  • ExternalApiController      │  │   │                            │   │
│  │  └───────────────────────────────┘  │   │  ┌──────────────────────┐  │   │
│  │               │                     │   │  │  Scheduled Tasks     │  │   │
│  │               ▼                     │   │  │  • Bi-monthly train  │  │   │
│  │  ┌───────────────────────────────┐  │   │  └──────────────────────┘  │   │
│  │  │      Service Layer            │  │   └────────────────┬───────────┘   │
│  │  │  • PreMatchInsightsService    │  │                    │               │
│  │  │  • TrendingInsightsService    │  │   ┌────────────────▼───────────┐   │
│  │  │  • LeagueStandingService      │  │   │     Shared Storage         │   │
│  │  │  • TeamStatsService           │  │   │                            │   │
│  │  │  • H2HInsightsService         │  │   │  ┌──────────────────────┐  │   │
│  │  │  • DashboardService           │  │   │  │   H2 Database        │  │   │
│  │  │  • TeamAnalyticsService       │  │   │  │   footballdb.mv.db   │  │   │
│  │  └───────────────────────────────┘  │   │  └──────────────────────┘  │   │
│  │               │                     │   │                            │   │
│  │               ▼                     │   │  ┌──────────────────────┐  │   │
│  │  ┌───────────────────────────────┐  │   │  │   ML Model           │  │   │
│  │  │     Repository Layer          │  │   │  │   predictor.model    │  │   │
│  │  │  • MatchRepository            │◄─┼───┼──│                      │  │   │
│  │  │  • TeamRepository             │  │   │  └──────────────────────┘  │   │
│  │  │  • LeagueStandingRepository   │  │   │                            │   │
│  │  │  • PredictionRepository       │  │   └────────────────────────────┘   │
│  │  └───────────────────────────────┘  │                                    │
│  │                                     │                                    │
│  │  ┌───────────────────────────────┐  │                                    │
│  │  │     Caffeine Cache Layer      │  │                                    │
│  │  │  • standings (5 min TTL)      │  │                                    │
│  │  │  • trendingInsights (5 min)   │  │                                    │
│  │  │  • teamStats (10 min)         │  │                                    │
│  │  │  • preMatchInsights (10 min)  │  │                                    │
│  │  │  • h2hInsights (10 min)       │  │                                    │
│  │  └───────────────────────────────┘  │                                    │
│  └─────────────────────────────────────┘                                    │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    Common Module (Shared Library)                    │   │
│  │  • Match Entity           • MatchFeatures DTO                        │   │
│  │  • Team Entity            • FeatureEngineeringService                │   │
│  │  • League Entity          • Prediction Entity                        │   │
│  │  • LeagueStanding Entity  • Shared Repositories                      │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Module Structure

| Module | Description |
|--------|-------------|
| `football-prediction-app` | Main application (REST APIs, Web UI, Services) |
| `football-prediction-common` | Shared entities, repositories, utilities |
| `model-training-service` | Dedicated ML training microservice |

### Layered Architecture

```
Controller Layer (REST APIs)
         │
         ▼
Service Layer (Business Logic)
         │
         ├──► Caffeine Cache Layer
         │
         ▼
Repository Layer (Data Access)
         │
         ▼
Database (H2 Embedded)
```

### Season-aware Filtering Strategy

All insight services implement season-scoped queries:

```java
// Example: TrendingInsightsService.java
@Cacheable(value = "trendingInsights", key = "#season")
public TrendingInsightsResponse getTrendingInsightsBySeason(String season) {
    // All queries filter by season column
    List<Match> seasonMatches = matchRepository.findBySeasonOrderByMatchDateDesc(season);
    
    // Insights computed only from season-scoped data
    List<HotTeam> hotTeams = calculateHotTeams(seasonTeams, beforeDate, season);
    List<ColdTeam> coldTeams = calculateColdTeams(seasonTeams, beforeDate, season);
    // ...
}
```

### Caching Strategy

Built-in **Caffeine cache** with configurable TTLs:

| Cache | TTL | Max Size | Purpose |
|-------|-----|----------|---------|
| `standings` | 5 min | 50 | League table data |
| `trendingInsights` | 5 min | 20 | Hot/cold teams, alerts |
| `teamStats` | 10 min | 100 | Team statistics |
| `preMatchInsights` | 10 min | 200 | Pre-match analysis |
| `h2hInsights` | 10 min | 200 | H2H historical data |
| `teamAnalytics` | 15 min | 100 | Full team analytics |
| `news` | 15 min | 50 | News feed |
| `teamLogos` | 60 min | 200 | Team logo URLs |

---

## 📡 API Documentation

### Base URLs

- **Main Application**: `http://localhost:8080/api`
- **Training Service**: `http://localhost:8081/api/training`

---

### Prediction Endpoints

#### POST `/api/predict`
Predict match outcome between two teams.

**Request:**
```json
{
  "homeTeam": "Arsenal",
  "awayTeam": "Chelsea"
}
```

**Response:**
```json
{
  "homeTeam": "Arsenal",
  "awayTeam": "Chelsea",
  "prediction": "Home Win",
  "predictionCode": "H",
  "probHomeWin": 0.52,
  "probDraw": 0.28,
  "probAwayWin": 0.20,
  "confidence": "MEDIUM",
  "features": {
    "homeFormPoints": 12,
    "awayFormPoints": 9,
    "homeGoalsScoredAvg": 2.1,
    "awayGoalsScoredAvg": 1.8,
    "homeGoalsConcededAvg": 0.8,
    "awayGoalsConcededAvg": 1.2,
    "h2hHomeWinRate": 0.45,
    "h2hDrawRate": 0.25,
    "h2hAwayWinRate": 0.30,
    "homeWinStreak": 3,
    "awayWinStreak": 1,
    "homeUnbeatenStreak": 5,
    "awayUnbeatenStreak": 2,
    "homeDaysSinceLastMatch": 7,
    "awayDaysSinceLastMatch": 4,
    "homeGoalThreat": 72.5,
    "awayGoalThreat": 58.0
  },
  "h2hInsights": {
    "totalMeetings": 30,
    "homeTeamWins": 15,
    "draws": 8,
    "awayTeamWins": 7,
    "homeTeamWinRate": 0.50,
    "last5Meetings": [
      {"date": "2025-01-15", "homeTeam": "Arsenal", "awayTeam": "Chelsea", "score": "2-1", "result": "H"}
    ]
  }
}
```

---

### Analytics Endpoints

#### GET `/api/analytics/pre-match`
Get comprehensive pre-match insights.

**Request:**
```
GET /api/analytics/pre-match?homeTeam=Arsenal&awayTeam=Chelsea
```

**Response:**
```json
{
  "homeTeam": "Arsenal",
  "awayTeam": "Chelsea",
  "homeForm": {
    "points": 12,
    "form": "WWDWW",
    "winStreak": 2,
    "unbeatenStreak": 5
  },
  "awayForm": {
    "points": 9,
    "form": "WDLWW",
    "winStreak": 2,
    "unbeatenStreak": 2
  },
  "homeRestDays": 7,
  "awayRestDays": 4,
  "awayFatigueWarning": true,
  "homeGoalThreat": 72.5,
  "awayGoalThreat": 58.0,
  "overUnder25Probability": 0.68,
  "bttsPercentage": 0.55
}
```

#### GET `/api/analytics/trends`
Get season-wise trending insights.

**Request:**
```
GET /api/analytics/trends?season=2025-26
```

**Response:**
```json
{
  "season": "2025-26",
  "hotTeams": [
    {"teamName": "Liverpool", "winStreak": 5, "goalsScored": 15, "recentForm": "WWWWW"}
  ],
  "coldTeams": [
    {"teamName": "Southampton", "matchesWithoutWin": 7, "recentForm": "LLDLL"}
  ],
  "topScorers": [
    {"teamName": "Man City", "goalsScored": 45, "matchesAnalyzed": 20, "avgGoalsPerMatch": 2.25}
  ],
  "defensiveWalls": [
    {"teamName": "Arsenal", "cleanSheets": 10, "matchesAnalyzed": 20, "cleanSheetPercentage": 50.0}
  ],
  "upsetAlerts": [
    {"homeTeam": "Wolves", "awayTeam": "Liverpool", "awayWinProbability": 0.62}
  ],
  "goalFestMatches": [
    {"homeTeam": "Man City", "awayTeam": "Chelsea", "expectedGoals": 3.8}
  ]
}
```

#### GET `/api/analytics/h2h`
Get head-to-head insights between two teams.

**Request:**
```
GET /api/analytics/h2h?homeTeam=Arsenal&awayTeam=Chelsea
```

**Response:**
```json
{
  "homeTeam": "Arsenal",
  "awayTeam": "Chelsea",
  "totalMeetings": 30,
  "homeTeamWins": 15,
  "draws": 8,
  "awayTeamWins": 7,
  "homeTeamWinRate": 0.50,
  "drawRate": 0.27,
  "awayTeamWinRate": 0.23,
  "avgGoalsPerMatch": 2.5,
  "bttsPercentage": 0.60,
  "mostCommonResult": "1-1",
  "last5Meetings": [
    {"date": "2025-01-15", "homeTeam": "Arsenal", "awayTeam": "Chelsea", "homeGoals": 2, "awayGoals": 1}
  ]
}
```

---

### Dashboard Endpoints

#### GET `/api/dashboard/upcoming-matches`
Get upcoming matches for dashboard.

**Response:**
```json
{
  "matches": [
    {
      "id": 12345,
      "homeTeam": "Arsenal",
      "awayTeam": "Chelsea",
      "matchDate": "2026-02-28",
      "matchday": 27,
      "homeTeamLogo": "https://...",
      "awayTeamLogo": "https://..."
    }
  ],
  "count": 10,
  "competition": "Premier League"
}
```

#### GET `/api/dashboard/league-standings`
Get league table with optional season filter.

**Request:**
```
GET /api/dashboard/league-standings?leagueId=1&season=2025-26
```

**Response:**
```json
{
  "leagueName": "Premier League",
  "leagueCode": "PL",
  "season": "2025/26",
  "totalTeams": 20,
  "standings": [
    {
      "position": 1,
      "teamName": "Liverpool",
      "teamLogo": "https://...",
      "played": 25,
      "won": 18,
      "drawn": 5,
      "lost": 2,
      "goalsFor": 55,
      "goalsAgainst": 20,
      "goalDifference": 35,
      "points": 59,
      "form": "WWDWW",
      "zone": "CHAMPIONS_LEAGUE"
    }
  ]
}
```

#### GET `/api/dashboard/model-accuracy`
Get model accuracy statistics.

**Response:**
```json
{
  "overallAccuracy": 62.3,
  "totalPredictions": 500,
  "correctPredictions": 312,
  "highConfidenceAccuracy": 71.5,
  "trendIndicator": "UP",
  "trendChange": 2.5,
  "lastUpdated": "2026-02-23T10:30:00"
}
```

---

### Team Statistics Endpoints

#### GET `/api/teams/{teamName}/stats`
Get comprehensive team statistics.

**Request:**
```
GET /api/teams/Arsenal/stats
```

**Response:**
```json
{
  "teamName": "Arsenal",
  "teamLogo": "https://...",
  "overview": {
    "totalMatches": 500,
    "wins": 250,
    "draws": 125,
    "losses": 125,
    "winPercentage": 50.0
  },
  "currentForm": {
    "lastMatches": "WWDWW",
    "formPoints": 13,
    "winStreak": 2
  },
  "goals": {
    "totalScored": 800,
    "totalConceded": 500,
    "avgScoredPerMatch": 1.6,
    "avgConcededPerMatch": 1.0,
    "cleanSheets": 150
  },
  "homeVsAway": {
    "homeWinRate": 55.0,
    "awayWinRate": 40.0,
    "homeGoalsAvg": 1.8,
    "awayGoalsAvg": 1.4
  }
}
```

---

### Insights Endpoints

#### GET `/api/insights/trending`
Get trending insights (alias to analytics/trends).

**Request:**
```
GET /api/insights/trending?season=2025-26
```

#### GET `/api/insights/seasons`
Get list of available seasons.

**Response:**
```json
{
  "seasons": ["2025-26", "2024-25", "2023-24", "2022-23"],
  "currentSeason": "2025-26"
}
```

---

### Model Management Endpoints

#### POST `/api/model/train` (Port 8080)
Trigger model training from main app.

#### POST `/api/model/load` (Port 8080)
Load/reload the trained model.

#### GET `/api/model/status` (Port 8080)
Check if model is loaded and ready.

#### POST `/api/training/train` (Port 8081)
Train model via training service.

#### GET `/api/training/model-info` (Port 8081)
Get model metadata.

**Response:**
```json
{
  "success": true,
  "modelInfo": {
    "modelExists": true,
    "modelPath": "../data/match_predictor.model",
    "modelSize": 1234567,
    "lastModified": "2026-02-20T03:00:00",
    "totalMatches": 8420
  }
}
```

---

## 🗄️ Database Schema

### Entity Relationship Diagram

```
┌─────────────────┐       ┌──────────────────────┐       ┌─────────────────┐
│     leagues     │       │   league_standings   │       │      teams      │
├─────────────────┤       ├──────────────────────┤       ├─────────────────┤
│ id (PK)         │◄──────│ league_id (FK)       │       │ id (PK)         │
│ code            │       │ season               │───────►│ name (UNIQUE)  │
│ name            │       │ team_name            │       │ logo_url        │
│ country_code    │       │ position             │       │ short_name      │
│ current_season  │       │ played/won/drawn/lost│       │ primary_color   │
└─────────────────┘       │ goals_for/against    │       └─────────────────┘
                          │ points/form          │
                          └──────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                           matches                                   │
├─────────────────────────────────────────────────────────────────────┤
│ id (PK)                                                             │
│ match_date, home_team, away_team, season, referee                   │
│ full_time_home_goals, full_time_away_goals, full_time_result        │
│ half_time_home_goals, half_time_away_goals                          │
│ home_shots, away_shots, home_shots_on_target, away_shots_on_target  │
│ home_corners, away_corners                                          │
│ home_yellow_cards, away_yellow_cards, home_red_cards, away_red_cards│
│ b365h, b365d, b365a, bwh, bwd, bwa, iwh, iwd, iwa, psh, psd, psa    │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                          predictions                             │
├──────────────────────────────────────────────────────────────────┤
│ id (PK)                                                          │
│ match_id (FK), team_id, team_name, opponent_name                 │
│ is_home, season, match_date                                      │
│ predicted_result, actual_result                                  │
│ home_win_probability, draw_probability, away_win_probability     │
│ confidence_level, is_correct, prediction_date                    │
│                                                                  │
│ INDEXES: idx_prediction_team, idx_prediction_season,             │
│          idx_prediction_match, idx_prediction_date               │
└──────────────────────────────────────────────────────────────────┘
```

### Key Tables

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `matches` | Historical match data | date, teams, goals, stats, season |
| `teams` | Team information | name, logo_url, colors |
| `leagues` | League metadata | code, name, current_season |
| `league_standings` | Season standings | position, points, form |
| `predictions` | Prediction tracking | predicted vs actual results |

> **Note**: Season data is stored as a column in `matches` table (e.g., "2025-26"), not as a separate `seasons` table.

---

## ⚙️ How It Works

### 1. Data Ingestion

```
┌────────────────────────┐     ┌─────────────────────────┐
│  CSV Files (22 seasons)│     │  football-data.org API  │
│  • PL_04_05.csv        │     │  • Live standings       │
│  • PL_05_06.csv        │     │  • Upcoming matches     │
│  • ...                 │     │  • Match results        │
│  • PL_25_26.csv        │     │                         │
└──────────┬─────────────┘     └───────────┬─────────────┘
           │                               │
           ▼                               ▼
    ┌──────────────────────────────────────────────┐
    │           CsvIngestionService                │
    │  • Parse date/score/stats                    │
    │  • Validate team names                       │
    │  • Deduplicate existing records              │
    │  • Map season from filename                  │
    └──────────────────────┬───────────────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  H2 Database │
                    │  ~8000 rows  │
                    └──────────────┘
```

### 2. Feature Engineering (25 Features)

```
Match Input (homeTeam, awayTeam)
              │
              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    FeatureEngineeringService                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Phase 1: Form & Goals                                          │
│  ├── homeFormPoints (last 5 matches)                            │
│  ├── awayFormPoints                                             │
│  ├── homeGoalsScoredAvg                                         │
│  ├── awayGoalsScoredAvg                                         │
│  ├── homeGoalsConcededAvg                                       │
│  ├── awayGoalsConcededAvg                                       │
│  ├── totalGoalsAvg                                              │
│  ├── h2hHomeWinRate                                             │
│  ├── h2hDrawRate                                                │
│  └── h2hAwayWinRate                                             │
│                                                                 │
│  Phase 2: Match Statistics                                      │
│  ├── homeShotsOnTargetAvg                                       │
│  ├── awayShotsOnTargetAvg                                       │
│  ├── homeCornersAvg                                             │
│  └── awayCornersAvg                                             │
│                                                                 │
│  Phase 3: Momentum & Fatigue                                    │
│  ├── homeGoalDifference                                         │
│  ├── awayGoalDifference                                         │
│  ├── homeOverallForm                                            │
│  ├── awayOverallForm                                            │
│  ├── homeWinStreak                                              │
│  ├── awayWinStreak                                              │
│  ├── homeUnbeatenStreak                                         │
│  ├── awayUnbeatenStreak                                         │
│  ├── homeDaysSinceLastMatch                                     │
│  └── awayDaysSinceLastMatch                                     │
│                                                                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
                        MatchFeatures DTO
```

### 3. Prediction Generation

```
MatchFeatures (25 features)
              │
              ▼
┌─────────────────────────────────────────────────┐
│            ModelTrainingService                 │
├─────────────────────────────────────────────────┤
│                                                 │
│  Stacked Ensemble:                              │
│  ┌───────────────┐  ┌───────────────┐           │
│  │ RandomForest  │  │  AdaBoostM1   │           │
│  │  (100 trees)  │  │ (100 iters)   │           │
│  └───────┬───────┘  └───────┬───────┘           │
│          │                  │                   │
│          └────────┬─────────┘                   │
│                   ▼                             │
│          ┌───────────────┐                      │
│          │   Logistic    │                      │
│          │  Regression   │ (Meta-classifier)    │
│          └───────┬───────┘                      │
│                  │                              │
└──────────────────┼──────────────────────────────┘
                   │
                   ▼
         ┌─────────────────┐
         │  Probabilities  │
         │  H: 0.52        │
         │  D: 0.28        │
         │  A: 0.20        │
         └────────┬────────┘
                  │
                  ▼
          Prediction: "H"
          Confidence: MEDIUM
```

### 4. Insight Computation

```
┌─────────────────────────────────────────────────────────────────┐
│                Season-aware Insight Engine                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Input: season = "2025-26"                                      │
│                                                                 │
│  Step 1: Filter matches by season                               │
│  SELECT * FROM matches WHERE season = '2025-26'                 │
│                                                                 │
│  Step 2: Calculate per-team metrics (within season only)        │
│  • Win streaks, form points, goals scored/conceded              │
│  • Clean sheets, matches without win                            │
│                                                                 │
│  Step 3: Rank and filter                                        │
│  • Hot teams: win_streak >= 3                                   │
│  • Cold teams: matches_without_win >= 5                         │
│  • Top scorers: ORDER BY goals_scored DESC LIMIT 5              │
│  • Defensive walls: ORDER BY clean_sheets DESC LIMIT 5          │
│                                                                 │
│  Step 4: Cache results (TTL: 5 minutes)                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5. Season-based Aggregation

All insight calculations are **strictly scoped to the selected season**:

```java
// No cross-season data contamination
private List<HotTeam> calculateHotTeams(Set<String> teams, 
                                        LocalDate beforeDate, 
                                        String season) {
    for (String team : teams) {
        // Only fetch matches from this season
        List<Match> recentMatches = matchRepository
            .findByTeamAndSeasonBeforeDate(team, season, beforeDate);
        
        // Calculate streak within season
        int winStreak = calculateWinStreak(recentMatches);
        
        if (winStreak >= HOT_FORM_THRESHOLD) {
            hotTeams.add(buildHotTeam(team, recentMatches));
        }
    }
    return hotTeams;
}
```

---

## 🚀 Setup Instructions

### Prerequisites

| Requirement | Version | Purpose |
|-------------|---------|---------|
| Java | 21+ | Runtime |
| Maven | 3.8+ | Build tool |
| Docker | Latest | Containerization (optional) |
| RAM | 8GB+ | ML training |

### Option 1: Docker Compose (Recommended)

```bash
# Clone repository
git clone <repository-url>
cd football-prediction

# Set environment variable (optional - for live data)
export FOOTBALL_API_KEY=your_api_key_here

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Access application
# Web UI: http://localhost:8080
# Training API: http://localhost:8081
```

### Option 2: Local Development

```bash
# Clone repository
git clone <repository-url>
cd football-prediction

# Build all modules
mvn clean install -DskipTests

# Terminal 1: Start main application
cd football-prediction-app
mvn spring-boot:run

# Terminal 2: Start training service
cd model-training-service
mvn spring-boot:run
```

### Option 3: Quick Start Script

```bash
# Make script executable
chmod +x scripts/start-services.sh

# Start services
./scripts/start-services.sh

# Test APIs
./scripts/test-apis.sh
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FOOTBALL_API_KEY` | - | API key for football-data.org |
| `SPRING_PROFILES_ACTIVE` | default | Profile: default, docker |
| `ADMIN_USERNAME` | admin | Admin panel username |
| `ADMIN_PASSWORD` | - | Admin panel password (set your own) |

### First-time Startup Behavior

On first run, the application will:
1. **Ingest CSV data** (~8,000 matches) into H2 database
2. **Seed team logos** for Premier League teams
3. **Train ML model** (~30-60 seconds)
4. **Initialize default league** (Premier League)

### ⚠️ Security Note

Before deploying to production:
1. **Set a strong admin password** via `ADMIN_PASSWORD` environment variable
2. **Never commit** `.env` files or API keys to version control
3. **Use HTTPS** in production environments
4. The default password is `changeme` - always change it!

---

## 🏭 Production Considerations

### Query Optimization

**Indexed Columns:**
```sql
-- matches table
CREATE INDEX idx_match_date ON matches(match_date);
CREATE INDEX idx_match_teams ON matches(home_team, away_team);
CREATE INDEX idx_match_season ON matches(season);

-- predictions table
CREATE INDEX idx_prediction_team ON predictions(teamId);
CREATE INDEX idx_prediction_season ON predictions(season);
CREATE INDEX idx_prediction_match ON predictions(matchId);
CREATE INDEX idx_prediction_date ON predictions(predictionDate);

-- league_standings table
CREATE INDEX idx_standings_league_season ON league_standings(league_id, season);
CREATE INDEX idx_standings_points ON league_standings(points DESC, goal_difference DESC);
```

### N+1 Prevention

Services use batch queries and DTOs to prevent N+1:

```java
// Bad: N+1 problem
for (Match match : matches) {
    Team homeTeam = teamRepository.findByName(match.getHomeTeam()); // N queries
}

// Good: Batch fetch
Map<String, String> teamLogos = teamRepository.findAll().stream()
    .collect(Collectors.toMap(Team::getName, Team::getLogoUrl));

for (Match match : matches) {
    String logo = teamLogos.get(match.getHomeTeam()); // O(1) lookup
}
```

### Performance Targets

| Operation | Target | Strategy |
|-----------|--------|----------|
| Dashboard endpoints | <300ms | Caffeine caching |
| Prediction requests | <200ms | Pre-loaded model |
| Insights queries | <500ms | Season-filtered queries |
| Model training | <60s | Background execution |

### Scalability Considerations

- **Stateless Services**: Both apps are stateless, ready for horizontal scaling
- **Shared Storage**: Model file on shared volume for multi-instance deployment
- **Cache Invalidation**: Manual cache clear endpoint available
- **Database**: H2 for development; recommend PostgreSQL for production

---

## 🔮 Future Roadmap

| Feature | Description | Priority |
|---------|-------------|----------|
| **Elo Rating System** | Dynamic team strength ratings | High |
| **xG Integration** | Expected goals model | High |
| **Real-time Retraining** | Model updates after each match | Medium |
| **Player-level Analytics** | Individual player impact | Medium |
| **Multi-league Support** | La Liga, Bundesliga, Serie A | Medium |
| **Deep Learning Models** | LSTM for sequence prediction | Low |
| **WebSocket Updates** | Live prediction updates | Low |

---

## 📁 Project Structure

```
football-prediction/
├── README.md                          # This file
├── LICENSE                            # MIT License
├── pom.xml                            # Parent POM
├── docker-compose.yml                 # Multi-service orchestration
├── Dockerfile                         # Main app Dockerfile
│
├── football-prediction-app/           # Main Application
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/app/footballprediction/
│       │   ├── controller/            # REST endpoints
│       │   │   ├── PredictionController.java
│       │   │   ├── AnalyticsController.java
│       │   │   ├── DashboardController.java
│       │   │   ├── TeamStatsController.java
│       │   │   └── ExternalApiController.java
│       │   ├── service/               # Business logic
│       │   │   ├── PreMatchInsightsService.java
│       │   │   ├── TrendingInsightsService.java
│       │   │   ├── LeagueStandingService.java
│       │   │   ├── H2HInsightsService.java
│       │   │   └── DashboardService.java
│       │   ├── dto/                   # Data transfer objects
│       │   ├── config/                # Configuration
│       │   │   └── CacheConfig.java   # Caffeine cache
│       │   └── scheduler/             # Scheduled tasks
│       └── resources/
│           ├── application.properties
│           ├── static/                # Web UI (HTML/CSS/JS)
│           └── data/                  # CSV files (22 seasons)
│
├── football-prediction-common/        # Shared Library
│   ├── pom.xml
│   └── src/main/java/com/app/common/
│       ├── model/                     # JPA Entities
│       │   ├── Match.java
│       │   ├── Team.java
│       │   ├── League.java
│       │   ├── LeagueStanding.java
│       │   └── Prediction.java
│       ├── repository/                # Spring Data JPA
│       └── service/                   # Shared services
│           └── FeatureEngineeringService.java
│
├── model-training-service/            # ML Training Service
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/app/modeltraining/
│       ├── controller/
│       │   └── ModelTrainingController.java
│       ├── service/
│       │   └── ModelTrainingService.java
│       └── scheduler/
│           └── TrainingScheduler.java
│
├── data/                              # Shared data volume
│   ├── footballdb.mv.db              # H2 database file
│   ├── match_predictor.model         # Trained ML model
│   └── model_backups/                # Model version backups
│
├── logs/                              # Application logs
│   ├── api/
│   ├── model/
│   └── error/
│
└── scripts/
    ├── start-services.sh             # Startup script
    └── test-apis.sh                  # API test script
```

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Weka Team** - Machine learning library
- **Spring Community** - Framework and documentation
- **Football-Data.co.uk** - Historical match data
- **Football-Data.org** - Live match API

---

<p align="center">
  <strong>⚽ Built for Football Analytics Enthusiasts</strong><br>
  <em>Season-aware • AI-powered • Production-ready</em>
</p>


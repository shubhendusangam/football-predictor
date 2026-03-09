# ⚽ Football Match Prediction & Insights Platform

A **Season-aware AI-powered Football Match Prediction & Insights Platform** built with Spring Boot and Machine Learning. This production-ready application predicts Premier League match outcomes using advanced ensemble learning while providing comprehensive pre-match insights, season-based trending analytics, expected goals (xG) modeling, referee analysis, fixture congestion tracking, and historical performance analysis.

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
- [System Architecture](#-system-architecture)
- [Module Documentation](#-module-documentation)
- [Quick Start](#-quick-start)
- [API Overview](#-api-overview)
- [Configuration](#-configuration)
- [Future Roadmap](#-future-roadmap)

---

## 🎯 Project Overview

This platform is designed for **football analytics enthusiasts** who want data-driven match predictions and comprehensive team insights. The system combines:

- **Machine Learning Predictions**: Stacked ensemble model (RandomForest + AdaBoostM1 + Logistic Regression)
- **Expected Goals (xG) Modeling**: Shots-on-target proxy model with team-specific conversion rates
- **Season-aware Analytics**: All insights are computed within season boundaries—no cross-season data mixing
- **Real-time Data Integration**: Live data from football-data.org API and ESPN for upcoming matches and standings
- **SSE Live Updates**: Server-Sent Events for real-time match completion notifications
- **Historical Analysis**: 33 seasons of Premier League data (1993/94 - 2025/26)

### Key Differentiators

| Feature | Description |
|---------|-------------|
| **Season Isolation** | Hot teams, cold teams, and all metrics are strictly per-season |
| **25 ML Features** | Form, goals, H2H, shots, corners, streaks, rest days |
| **Pre-Match Insights** | Goal threat, fatigue warnings, BTTS probability |
| **Elo Rating System** | Dynamic team strength tracking with upset detection |
| **Expected Goals (xG)** | Shot-based xG model with over/underperformance tracking |
| **Referee Analytics** | Referee tendencies for cards, fouls, and results |
| **Fixture Congestion** | Fatigue index and rest-day impact analysis |
| **Kickoff Time Analysis** | Performance breakdown by time-of-day slots |
| **Smart Data Ingestion** | Idempotent upsert pipeline with shadow validation |
| **Real-time SSE** | Server-Sent Events for live match completion updates |
| **Multi-module Architecture** | Separate prediction app and training service |

---

## ✨ Core Features

### 🤖 Match Prediction Engine

The prediction engine uses a **Stacked Ensemble ML Model** enhanced with **Elo Ratings**:

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
│  Elo Adjustment Layer:                      │
│  └── Dynamic Elo-based probability tuning   │
│                                             │
│  Output: H (Home Win) / D (Draw) / A (Away) │
└─────────────────────────────────────────────┘
```

- **25 Engineered Features** across 3 phases
- **Elo Rating Integration** for team strength comparison
- **Confidence Levels**: HIGH / MEDIUM / LOW based on probability distribution
- **Prediction Explainability**: Breakdown of factors influencing the prediction
- **Automatic Retraining**: Scheduled bi-monthly (1st & 15th @ 3 AM) + smart retrain on new data

### 📈 Elo Rating System

| Feature | Description |
|---------|-------------|
| **Live Elo Ratings** | Updated after each match result |
| **Elo Rankings** | Season-wise team rankings by Elo |
| **Upset Detection** | Alerts when lower-rated team is favored |
| **Probability Adjustment** | Elo difference impacts win probabilities |

### 🧠 Prediction Explainability

Every prediction includes a breakdown of contributing factors:

| Factor | Description |
|--------|-------------|
| **Elo Impact** | How Elo rating difference affects the prediction |
| **Form Impact** | Recent form influence |
| **Goal Trend Impact** | Scoring/conceding trends effect |
| **H2H Impact** | Head-to-head history influence |
| **Home Advantage** | Home field advantage boost |

### 📊 Pre-Match Insights

| Insight | Description |
|---------|-------------|
| **Form Comparison** | Home vs Away form points (last 5 matches) |
| **Streak Indicators** | 🔥 Win streaks, unbeaten streaks |
| **Rest Days Warning** | ⚠️ Fatigue indicator (<4 days rest) |
| **Goal Threat Meter** | Offensive threat percentage (0-100%) |
| **Over/Under 2.5** | Probability based on goal averages |
| **BTTS %** | Both teams to score probability |

### 🔥 Season-wise Trending Insights

| Insight | Criteria |
|---------|----------|
| **🔥 Hot Teams** | Teams on 3+ match winning streaks |
| **❄️ Cold Teams** | Teams without a win in 5+ matches |
| **⚽ Top Scorers** | Highest-scoring teams (season aggregate) |
| **🧱 Defensive Walls** | Most clean sheets in the season |
| **🎯 Upset Alerts** | Away team favored (>50% win probability) |
| **🎉 Goal Fest** | Matches with highest expected total goals |

### 🆚 Head-to-Head Analytics

- **Historical Record**: Complete H2H statistics
- **Last 5 Meetings**: Recent scores and outcomes
- **H2H Goal Stats**: Average goals when teams meet
- **Common Results**: Most frequent scoreline
- **Venue Advantage**: H2H win % at home vs away

### 🎯 Shot Quality Analytics

| Metric | Description |
|--------|-------------|
| **Quality Score** | Composite score (0-100) based on shot accuracy and conversion |
| **Shot Accuracy** | Percentage of shots on target |
| **Conversion Rate** | Goals per shot ratio |
| **Shots Trend** | Last 10 matches sparkline visualization |
| **Home/Away Split** | Separate metrics for home and away performance |
| **League Comparison** | Above/Near/Below league average indicators |

### ⚽ Expected Goals (xG)

| Metric | Description |
|--------|-------------|
| **Team xG** | Expected goals based on shots on target × league conversion rate |
| **xG Over/Underperformance** | Actual goals vs expected goals comparison |
| **Match xG Prediction** | Predicted xG for both teams in a matchup |
| **Over/Under Probabilities** | Goal probability distributions (Over 1.5, 2.5, 3.5) |
| **Home/Away Split** | Separate xG metrics for home and away |
| **Recency-Weighted** | Exponential decay weighting for recent matches |

### 🟨 Fouls & Discipline Analysis

| Metric | Description |
|--------|-------------|
| **Discipline Score** | 0-10 scale rating of team discipline |
| **Fouls Committed Avg** | Average fouls committed per match |
| **Fouls Drawn Avg** | Average fouls won per match |
| **Fouls Differential** | Net fouls (committed - drawn) |
| **Win Rate by Foul Count** | Win % when committing low/controlled/high fouls |
| **Discipline Badge** | Color-coded rating (Excellent/Good/Average/Poor) |

### ⚑ Corner Stats & Predictions

| Metric | Description |
|--------|-------------|
| **Avg Corners Won** | Average corners won per match (home/away split) |
| **Avg Corners Against** | Average corners conceded per match |
| **Corner Dominance** | Percentage of total corners won vs conceded |
| **Success Rate** | Win rate correlation with corner performance |
| **Weighted Avg Corners** | Recency-weighted average using exponential decay |
| **Match Corner Prediction** | Expected total corners with probability distribution |
| **Over/Under Probabilities** | Probability for Over 9.5, 10.5, 11.5 corners |

### 🟡 Cards Prediction & Team Discipline

| Metric | Description |
|--------|-------------|
| **Expected Yellow Cards** | Predicted yellow cards for home/away teams |
| **Expected Red Cards** | Predicted red card probability |
| **Referee Influence** | Card rates adjusted by referee history |
| **Team Discipline Rating** | 0-10 discipline score per team |
| **Recent Bookings** | Last 5 matches booking summary |
| **Card Risk Level** | High/Medium/Low risk classification |

### ⏱️ Half-Time Analysis

| Metric | Description |
|--------|-------------|
| **First Half Goals %** | Percentage of goals scored in first half |
| **Second Half Goals %** | Percentage of goals scored in second half |
| **Pattern Classification** | Fast Starter / Strong Finisher / Balanced |
| **Win Rate from Winning HT** | Win rate when leading at half-time |
| **Win Rate from Drawing HT** | Win rate when drawing at half-time |
| **Comeback Rate** | Percentage of wins after losing at half-time |
| **Confidence Level** | Based on matches analyzed |

### 👨‍⚖️ Referee Analytics

| Metric | Description |
|--------|-------------|
| **Matches Officiated** | Total matches per referee |
| **Avg Yellow Cards** | Average yellows per match (vs league average) |
| **Avg Red Cards** | Average reds per match |
| **Strictness Rating** | Relative to league-wide card averages |
| **Result Distribution** | Home win / Draw / Away win breakdown |
| **Strictest/Lenient** | Rankings of referees by card tendencies |

### 🏋️ Fixture Congestion & Fatigue

| Metric | Description |
|--------|-------------|
| **Fatigue Index** | 0-100 scale (100 = very congested, 0 = well rested) |
| **Average Rest Days** | Mean days between recent matches |
| **Win Rate by Rest** | Performance breakdown by short/normal/long rest |
| **Congestion Comparison** | Head-to-head fatigue comparison for a matchup |
| **Advantage Detection** | Identifies which team has a rest advantage |

### ⏰ Kickoff Time Analysis

| Metric | Description |
|--------|-------------|
| **Time Slot Breakdown** | Early / Afternoon / Late / Evening performance |
| **Win/Draw/Loss per Slot** | Results breakdown by kickoff time |
| **Goal Averages per Slot** | Scoring patterns by time of day |
| **Performance Classification** | Strong / Average / Weak per slot |
| **Best/Worst Slot** | Optimal and weakest kickoff times |

### 🏆 Additional Features

| Feature | Description |
|---------|-------------|
| **League Standings** | Real-time standings with zone indicators |
| **Upcoming Matches** | Match-day predictions with date filters |
| **Model Accuracy Tracking** | Performance metrics, sliding accuracy, temporal CV |
| **Football News Feed** | Aggregated RSS news (BBC, Sky Sports, ESPN) |
| **Admin Panel** | Secure dashboard for system management |
| **Season Team Stats** | Per-season team statistics with Elo/form rankings |
| **Smart Polling** | Automated match data polling with retrain triggers |
| **SSE Live Events** | Server-Sent Events for match completion notifications |
| **Data Ingestion Pipeline** | Idempotent upsert with shadow validation |
| **ESPN Integration** | Additional data source for match enrichment |

---

## 🏗️ System Architecture

### High-Level Architecture

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
│  │  │  • TeamStatsController        │  │   │  │  POST /test          │  │   │
│  │  │  • SeasonTeamStatsController  │  │   │  │  GET  /model-info    │  │   │
│  │  │  • SeasonsController          │  │   │  └──────────────────────┘  │   │
│  │  │  • RefereeController          │  │   │                            │   │
│  │  └───────────────────────────────┘  │   │  ┌──────────────────────┐  │   │
│  │               │                     │   │  │  Scheduled Tasks     │  │   │
│  │               ▼                     │   │  │  • Bi-monthly train  │  │   │
│  │  ┌───────────────────────────────┐  │   │  └──────────────────────┘  │   │
│  │  │      Service Layer            │  │   └────────────────┬───────────┘   │
│  │  │  • PreMatchInsightsService    │  │                    │               │
│  │  │  • TrendingInsightsService    │  │   ┌────────────────▼───────────┐   │
│  │  │  • ExpectedGoalsService       │  │   │     Shared Storage         │   │
│  │  │  • ShotQualityService         │  │   │                            │   │
│  │  │  • FoulsAnalysisService       │  │   │  ┌──────────────────────┐  │   │
│  │  │  • CornerStatsService         │  │   │  │   H2 Database        │  │   │
│  │  │  • CardsPredictionService     │  │   │  │   footballdb.mv.db   │  │   │
│  │  │  • HalfAnalysisService        │  │   │  └──────────────────────┘  │   │
│  │  │  • RefereeStatsService        │  │   │                            │   │
│  │  │  • FixtureCongestionService   │  │   │  ┌──────────────────────┐  │   │
│  │  │  • KickoffTimeService         │  │   │  │   ML Model           │  │   │
│  │  │  • H2HInsightsService         │  │   │  │   predictor.model    │  │   │
│  │  │  • TeamStatsService           │  │   │  └──────────────────────┘  │   │
│  │  └───────────────────────────────┘  │   │                            │   │
│  │               │                     │   └────────────────────────────┘   │
│  │               ▼                     │                                    │
│  │  ┌───────────────────────────────┐  │                                    │
│  │  │     Caffeine Cache Layer      │  │                                    │
│  │  │  • 19 cache definitions       │  │                                    │
│  │  │  • Configurable TTLs          │  │                                    │
│  │  └───────────────────────────────┘  │                                    │
│  │               │                     │                                    │
│  │               ▼                     │                                    │
│  │  ┌───────────────────────────────┐  │                                    │
│  │  │     Polling & SSE Layer       │  │                                    │
│  │  │  • DailyMatchPollingJob       │  │                                    │
│  │  │  • SmartRetrainService        │  │                                    │
│  │  │  • SseController (SSE)        │  │                                    │
│  │  │  • SyncStatusController       │  │                                    │
│  │  └───────────────────────────────┘  │                                    │
│  │               │                     │                                    │
│  │               ▼                     │                                    │
│  │  ┌───────────────────────────────┐  │                                    │
│  │  │     Ingestion Pipeline        │  │                                    │
│  │  │  • IngestionOrchestrator      │  │                                    │
│  │  │  • IdempotentUpsertService    │  │                                    │
│  │  │  • ShadowValidator            │  │                                    │
│  │  │  • ESPN Integration           │  │                                    │
│  │  └───────────────────────────────┘  │                                    │
│  └─────────────────────────────────────┘                                    │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    Common Module (Shared Library)                    │   │
│  │  • Match Entity           • MatchFeatures DTO     • ShotQualityDTO   │   │
│  │  • Team Entity            • FeatureEngineeringService                │   │
│  │  • League Entity          • EloRatingService      • LeaguePositionSvc│   │
│  │  • SeasonTeamStats Entity • Ingestion Events & DTOs                  │   │
│  │  • ModelAccuracy Entity   • TeamNameNormalizer    • FeatureDriftMonitor│  │
│  │  • LeagueStanding Entity  • Shared Repositories   • WekaSchemaBuilder│   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                         Frontend (Vanilla JS)                        │   │
│  │  • ShotQualityCard        • FoulsAnalysisCard    • TeamAnalyticsPage │   │
│  │  • CornerStatsCard        • CornerPredictionCard • MatchPreviewPage  │   │
│  │  • ExpectedGoalsCard      • MatchXGCard          • KickoffTimeCard   │   │
│  │  • Static Resources       • Canvas Sparklines                        │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
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
│  • ESPN API                  • SSE Event Stream                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Feature Engineering Pipeline (25 Features)

| Phase | Features |
|-------|----------|
| **Phase 1: Form & Goals** | homeFormPoints, awayFormPoints, goalsScoredAvg, goalsConcededAvg, h2hRates |
| **Phase 2: Match Statistics** | shotsOnTargetAvg, cornersAvg |
| **Phase 3: Momentum & Fatigue** | goalDifference, overallForm, winStreak, unbeatenStreak, daysSinceLastMatch |

### Caching Strategy

| Cache | TTL | Purpose |
|-------|-----|---------|
| `standings` | 5 min | League table data |
| `trendingInsights` | 5 min | Hot/cold teams, alerts |
| `teamStats` | 10 min | Team statistics |
| `shotQuality` | 10 min | Shot quality metrics |
| `foulsAnalysis` | 10 min | Fouls & discipline data |
| `cornerStats` | 10 min | Corner statistics |
| `cornerPrediction` | 10 min | Corner predictions |
| `cardsPrediction` | 10 min | Cards prediction data |
| `teamDiscipline` | 10 min | Team discipline data |
| `halfAnalysis` | 10 min | Half-time analysis data |
| `expectedGoals` | 10 min | xG statistics |
| `xgPrediction` | 10 min | Match xG predictions |
| `kickoffTimeAnalysis` | 10 min | Kickoff time analysis |
| `fixtureCongestion` | 10 min | Fixture congestion data |
| `refereeStats` | 10 min | Referee statistics |
| `h2hInsights` | 10 min | H2H historical data |
| `teamLogos` | 60 min | Team logo URLs |
| `eloRatings` | 10 min | Elo rating data |
| `news` | 15 min | News feed |

---

## 📚 Module Documentation

For detailed design documentation, please refer to the individual module READMEs:

| Module | Description | Documentation |
|--------|-------------|---------------|
| **football-prediction-app** | Main application with REST APIs, services, and web UI | [📖 View Details](./football-prediction-app/README.md) |
| **football-prediction-common** | Shared entities, repositories, and feature engineering | [📖 View Details](./football-prediction-common/README.md) |
| **model-training-service** | ML model training and evaluation microservice | [📖 View Details](./model-training-service/README.md) |
| **frontend** | Frontend components (vanilla JS, no frameworks) | [📖 View Details](./frontend/README.md) |

### Module Dependency Graph

```
                    ┌─────────────────────────┐
                    │   football-prediction   │
                    │         -common         │
                    └───────────┬─────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              ▼                 ▼                 ▼
┌─────────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ football-prediction │ │ model-training  │ │    frontend     │
│        -app         │ │    -service     │ │  (static files) │
└─────────────────────┘ └─────────────────┘ └─────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker (optional)

### Option 1: Quick Start Script

```bash
chmod +x quick-start.sh
./quick-start.sh
```

### Option 2: Manual Setup

```bash
# Clone the repository
git clone https://github.com/yourusername/football-prediction.git
cd football-prediction

# Build all modules
./mvnw clean install -DskipTests

# Start the main application
./mvnw spring-boot:run -pl football-prediction-app

# (Optional) Start the training service in another terminal
./mvnw spring-boot:run -pl model-training-service
```

### Option 3: Multi-Service Script

```bash
chmod +x scripts/start-services.sh
./scripts/start-services.sh
```

### Option 4: Docker

```bash
docker-compose up -d
```

### Access Points

| Service | URL |
|---------|-----|
| **Web UI** | http://localhost:8080 |
| **API** | http://localhost:8080/api |
| **SSE Events** | http://localhost:8080/api/events/match-completion |
| **Training Service** | http://localhost:8081/api/training |
| **H2 Console** | http://localhost:8080/h2-console |

---

## 📡 API Overview

### Core Prediction Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/predict` | POST | Predict match outcome |
| `/api/h2h` | GET | Head-to-head analysis |
| `/api/insights/trending` | GET | Season trending insights |
| `/api/insights/seasons` | GET | Available seasons |

### Analytics Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/analytics/pre-match` | GET | Pre-match insights |
| `/api/analytics/trends` | GET | Season trending insights |
| `/api/analytics/h2h` | GET | Head-to-head analysis |
| `/api/analytics/league/stats` | GET | League-wide statistics |
| `/api/analytics/match` | GET | Full match analysis |

### Team Statistics Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/teams/{name}/stats` | GET | Team statistics |
| `/api/teams/{name}/analytics` | GET | Full team analytics |
| `/api/teams/{name}/shot-quality` | GET | Shot quality metrics |
| `/api/teams/{name}/fouls-analysis` | GET | Fouls & discipline |
| `/api/teams/{name}/corner-stats` | GET | Corner statistics |
| `/api/teams/{name}/half-analysis` | GET | Half-time analysis |
| `/api/teams/{name}/expected-goals` | GET | Expected goals (xG) |
| `/api/teams/{name}/kickoff-analysis` | GET | Kickoff time analysis |
| `/api/teams/{name}/fixture-congestion` | GET | Fixture congestion |
| `/api/teams/{name}/discipline` | GET | Team discipline metrics |

### Match Prediction Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/matches/predict-corners` | GET | Corner prediction for match |
| `/api/matches/predict-cards` | GET | Cards prediction for match |
| `/api/matches/predict-xg` | GET | xG prediction for match |
| `/api/matches/congestion-comparison` | GET | Fatigue comparison for match |

### Referee Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/referees` | GET | All referee names |
| `/api/referees/stats` | GET | All referee statistics |
| `/api/referees/{name}` | GET | Specific referee stats |
| `/api/referees/strictest` | GET | Strictest referees |
| `/api/referees/lenient` | GET | Most lenient referees |

### Model Performance Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/model/accuracy` | GET | Model accuracy metrics |
| `/api/model/performance` | GET | Detailed performance stats |
| `/api/model/feature-importance` | GET | Feature importance ranking |
| `/api/model/sliding-accuracy` | GET | Sliding window accuracy |
| `/api/model/temporal-cv` | GET | Temporal cross-validation |
| `/api/model/retraining-history` | GET | Retraining history log |

### Dashboard Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/dashboard/upcoming-matches` | GET | Upcoming fixtures |
| `/api/dashboard/league-standings` | GET | League table |
| `/api/dashboard/model-accuracy` | GET | Model accuracy stats |
| `/api/dashboard/todays-predictions` | GET | Today's predictions |
| `/api/dashboard/top-teams` | GET | Top performing teams |

### Season & Team Stats Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/seasons` | GET | All available seasons |
| `/api/seasons/{year}/stats` | GET | Season statistics |
| `/api/season/{id}/team/{teamId}/stats` | GET | Season team stats |
| `/api/season/{id}/elo-rankings` | GET | Season Elo rankings |
| `/api/season/{id}/form-rankings` | GET | Season form rankings |
| `/api/season/{id}/winning-streaks` | GET | Season winning streaks |

### Polling & SSE Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/events/match-completion` | GET (SSE) | Real-time match completion events |
| `/api/events/status` | GET | SSE connection status |
| `/api/sync-status` | GET | Data sync status |
| `/api/system-status` | GET | System health status |
| `/api/match-day-status` | GET | Match day information |
| `/api/poll/trigger` | POST | Manual poll trigger |
| `/api/retrain/trigger` | POST | Manual retrain trigger |
| `/api/retrain/status` | GET | Retrain status |

### Example Request

```bash
curl -X POST http://localhost:8080/api/predict \
  -H "Content-Type: application/json" \
  -d '{"homeTeam": "Arsenal", "awayTeam": "Chelsea"}'
```

### Example Response

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
  "homeElo": 1820.5,
  "awayElo": 1780.2,
  "explanation": {
    "summary": "Arsenal favored due to higher Elo rating and strong home form"
  }
}
```

> **Full API documentation**: See [football-prediction-app/README.md](./football-prediction-app/README.md#api-endpoints)

---

## ⚙️ Configuration

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
scheduler.cron=0 0 3 1,15 * ?
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FOOTBALL_API_KEY` | - | football-data.org API key |
| `ADMIN_USERNAME` | admin | Admin panel username |
| `ADMIN_PASSWORD` | - | Admin panel password |
| `MODEL_PATH` | ./data/match_predictor.model | ML model location |

---

## 🗄️ Database Schema

### Core Tables

| Table | Purpose |
|-------|---------|
| `matches` | Historical match data (~12,500 records) |
| `teams` | Team information with logos |
| `leagues` | League metadata |
| `league_standings` | Season standings |
| `predictions` | Prediction tracking |
| `prediction_evaluations` | Prediction evaluation metrics |
| `model_accuracy` | Model accuracy history |
| `model_training_history` | Training run logs |
| `season_team_stats` | Per-season team statistics |
| `admin_audit_logs` | Admin action audit trail |
| `system_settings` | Application configuration |

### Key Indexes

```sql
CREATE INDEX idx_match_date ON matches(match_date);
CREATE INDEX idx_match_season ON matches(season);
CREATE INDEX idx_match_teams ON matches(home_team, away_team);
CREATE INDEX idx_prediction_season ON predictions(season);
```

---

## 🔮 Future Roadmap

| Enhancement | Priority |
|-------------|----------|
| WebSocket real-time updates | High |
| Player-level analytics | Medium |
| Full xG model with event data | Medium |
| Multi-league support (La Liga, Bundesliga) | Medium |
| GraphQL API | Low |
| Mobile app | Low |

---

## 📊 Project Metrics

| Metric | Value |
|--------|-------|
| **Modules** | 4 (app, common, training, frontend) |
| **REST Endpoints** | 70+ |
| **ML Features** | 25 |
| **Cache Definitions** | 19 |
| **Services** | 37+ |
| **Controllers** | 5 (main) + 3 (polling/ingestion/SSE) |
| **JPA Entities** | 12 |
| **Repositories** | 11 |
| **Historical Seasons** | 33 |
| **Historical Matches** | ~12,500 |
| **Frontend Components** | 5 team + 2 match |

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <strong>Built with ❤️ for Football Analytics</strong>
</p>


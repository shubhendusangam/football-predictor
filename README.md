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
- **Season-aware Analytics**: All insights are computed within season boundaries—no cross-season data mixing
- **Real-time Data Integration**: Live data from football-data.org API for upcoming matches and standings
- **Historical Analysis**: 33 seasons of Premier League data (1993/94 - 2025/26)

### Key Differentiators

| Feature | Description |
|---------|-------------|
| **Season Isolation** | Hot teams, cold teams, and all metrics are strictly per-season |
| **25 ML Features** | Form, goals, H2H, shots, corners, streaks, rest days |
| **Pre-Match Insights** | Goal threat, fatigue warnings, BTTS probability |
| **Elo Rating System** | Dynamic team strength tracking with upset detection |
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
- **Automatic Retraining**: Scheduled bi-monthly (1st & 15th @ 3 AM)

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

### 🏆 Additional Features

| Feature | Description |
|---------|-------------|
| **League Standings** | Real-time standings with zone indicators |
| **Upcoming Matches** | Match-day predictions with date filters |
| **Model Accuracy Tracking** | Performance metrics and trends |
| **Football News Feed** | Aggregated RSS news (BBC, Sky Sports, ESPN) |
| **Admin Panel** | Secure dashboard for system management |

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
│  │  │  • AnalyticsController        │  │   │  │  POST /test          │  │   │
│  │  │  • DashboardController        │  │   │  │  GET  /model-info    │  │   │
│  │  │  • TeamStatsController        │  │   │  └──────────────────────┘  │   │
│  │  │  • AdminController            │  │   │                            │   │
│  │  └───────────────────────────────┘  │   │  ┌──────────────────────┐  │   │
│  │               │                     │   │  │  Scheduled Tasks     │  │   │
│  │               ▼                     │   │  │  • Bi-monthly train  │  │   │
│  │  ┌───────────────────────────────┐  │   │  └──────────────────────┘  │   │
│  │  │      Service Layer            │  │   └────────────────┬───────────┘   │
│  │  │  • PreMatchInsightsService    │  │                    │               │
│  │  │  • TrendingInsightsService    │  │   ┌────────────────▼───────────┐   │
│  │  │  • ShotQualityService         │  │   │     Shared Storage         │   │
│  │  │  • FoulsAnalysisService       │  │   │                            │   │
│  │  │  • CornerStatsService (NEW)   │  │   │  ┌──────────────────────┐  │   │
│  │  │  • CardsPredictionService     │  │   │  │   H2 Database        │  │   │
│  │  │  • HalfAnalysisService (NEW)  │  │   │  │   footballdb.mv.db   │  │   │
│  │  │  • H2HInsightsService         │  │   │  └──────────────────────┘  │   │
│  │  │  • TeamStatsService           │  │   │                            │  │
│  │  └───────────────────────────────┘  │   │  ┌──────────────────────┐  │   │
│  │               │                     │   │  └──────────────────────┘  │   │
│  │               ▼                     │   │                            │   │
│  │  ┌───────────────────────────────┐  │   │  ┌──────────────────────┐  │   │
│  │  │     Caffeine Cache Layer      │  │   │  │   ML Model           │  │   │
│  │  │  • 16 cache definitions       │  │   │  │   predictor.model    │  │   │
│  │  │  • Configurable TTLs          │  │   │  └──────────────────────┘  │   │
│  │  └───────────────────────────────┘  │   │                            │   │
│  └─────────────────────────────────────┘   └────────────────────────────┘   │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    Common Module (Shared Library)                    │   │
│  │  • Match Entity           • MatchFeatures DTO     • ShotQualityDTO   │   │
│  │  • Team Entity            • FeatureEngineeringService                │   │
│  │  • League Entity          • Prediction Entity                        │   │
│  │  • LeagueStanding Entity  • Shared Repositories                      │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                         Frontend (Vanilla JS)                        │   │
│  │  • ShotQualityCard        • FoulsAnalysisCard    • TeamAnalyticsPage │   │
│  │  • CornerStatsCard (NEW)  • CornerPredictionCard • MatchPreviewPage  │   │
│  │  • Router.js              • Static Resources                         │   │
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
| `cardsPrediction` | 10 min | Cards prediction data |
| `halfAnalysis` | 10 min | Half-time analysis data |
| `h2hInsights` | 10 min | H2H historical data |
| `teamLogos` | 60 min | Team logo URLs |
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

### Option 3: Docker

```bash
docker-compose up -d
```

### Access Points

| Service | URL |
|---------|-----|
| **Web UI** | http://localhost:8080 |
| **API** | http://localhost:8080/api |
| **Training Service** | http://localhost:8081/api/training |
| **H2 Console** | http://localhost:8080/h2-console |

---

## 📡 API Overview

### Core Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/predict` | POST | Predict match outcome |
| `/api/analytics/pre-match` | GET | Pre-match insights |
| `/api/analytics/trends` | GET | Season trending insights |
| `/api/analytics/h2h` | GET | Head-to-head analysis |
| `/api/dashboard/upcoming-matches` | GET | Upcoming fixtures |
| `/api/dashboard/league-standings` | GET | League table |
| `/api/teams/{name}/stats` | GET | Team statistics |
| `/api/teams/{name}/shot-quality` | GET | Shot quality metrics |
| `/api/teams/{name}/fouls-analysis` | GET | Fouls & discipline |
| `/api/news/premier-league` | GET | News feed |

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
| Expected Goals (xG) integration | Medium |
| Multi-league support (La Liga, Bundesliga) | Medium |
| GraphQL API | Low |
| Mobile app | Low |

---

## 📊 Project Metrics

| Metric | Value |
|--------|-------|
| **Total Lines of Code** | ~25,000 |
| **Modules** | 4 |
| **REST Endpoints** | 45+ |
| **ML Features** | 25 |
| **Cache Definitions** | 16 |
| **Historical Seasons** | 33 |
| **Historical Matches** | ~12,500 |

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <strong>Built with ❤️ for Football Analytics</strong>
</p>


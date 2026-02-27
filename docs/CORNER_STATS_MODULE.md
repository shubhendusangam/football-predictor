# Corner Kick Statistics & Prediction Module

## Overview

This module provides comprehensive corner kick analytics for football match prediction. It includes both team-level statistics and match-level predictions with over/under probabilities.

## Architecture

```
controller → service → repository → DTO
```

### Components

- **CornerStatsController** - REST API endpoints
- **CornerStatsService** - Business logic and calculations
- **CornerStatsDTO** - Team corner statistics data transfer object
- **CornerPredictionDTO** - Match corner prediction data transfer object

## API Endpoints

### 1. Get Team Corner Statistics

```
GET /api/teams/{teamName}/corner-stats
GET /api/teams/{teamName}/corner-stats?isHome=true
GET /api/teams/{teamName}/corner-stats?isHome=false
```

**Parameters:**
- `teamName` (path) - Team name (URL encoded if contains spaces)
- `isHome` (query, optional) - Filter: `true` = home only, `false` = away only, omit = all

**Response:**
```json
{
  "teamName": "Arsenal",
  "isHome": true,
  "avgCornersWon": 6.45,
  "avgCornersAgainst": 4.20,
  "cornerDominance": 0.606,
  "successRate": 0.583,
  "matchesAnalyzed": 20,
  "totalCornersWon": 129,
  "totalCornersAgainst": 84,
  "weightedAvgCorners": 6.78
}
```

### 2. Get Corner Stats Split (Home + Away)

```
GET /api/teams/{teamName}/corner-stats/split
```

**Response:**
```json
{
  "teamName": "Arsenal",
  "home": { ... },
  "away": { ... },
  "overall": { ... }
}
```

### 3. Predict Match Corners

```
GET /api/matches/predict-corners?home={homeTeam}&away={awayTeam}
```

**Parameters:**
- `home` (query) - Home team name
- `away` (query) - Away team name

**Response:**
```json
{
  "homeTeam": "Arsenal",
  "awayTeam": "Chelsea",
  "expectedTotalCorners": 10.45,
  "expectedHomeCorners": 5.72,
  "expectedAwayCorners": 4.73,
  "probOver9_5": 0.623,
  "probOver10_5": 0.458,
  "probOver11_5": 0.302,
  "confidence": 0.85,
  "homeWeightedCorners": 6.78,
  "awayWeightedCorners": 4.92,
  "homeMatchesAnalyzed": 18,
  "awayMatchesAnalyzed": 16
}
```

## Calculation Methodology

### Team Statistics
- **avgCornersWon**: Simple average of corners won per match (full season)
- **avgCornersAgainst**: Simple average of corners conceded per match (full season)
- **cornerDominance**: `cornersWon / (cornersWon + cornersAgainst)` (0 to 1)
- **successRate**: Win rate when team has more corners than opponent
- **weightedAvgCorners**: Exponentially weighted average (recent matches weighted more)

### Match Prediction
- Uses both teams' recent corner averages from the current season
- Applies 15% exponential decay weighting for recency
- Applies 10% home advantage factor
- Uses normal distribution approximation for over/under probabilities
- Confidence based on sample sizes (20+ matches = high confidence)

### Data Constraints
- **Uses full season matches** for the current season
- Matches sorted by date descending (most recent first)
- Filters by home/away based on request
- Excludes matches with missing corner data
- Prevents future data leakage (beforeDate filtering)

## Frontend Components

### CornerStatsCard.js
Renders team corner statistics with:
- Horizontal bar charts for averages
- Corner dominance indicator with color coding
- Success rate display
- Stats grid with totals

### CornerPredictionCard.js
Renders match corner predictions with:
- Animated total corners counter
- Home vs Away comparison bar
- Probability bars for over 9.5/10.5/11.5
- Confidence indicator

## Color Coding

### Corner Dominance
- **Green (>55%)**: Strong corner dominance
- **Yellow (45-55%)**: Average dominance
- **Red (<45%)**: Weak dominance

### Probabilities
- **Green (>60%)**: High probability
- **Yellow (40-60%)**: Medium probability
- **Red (<40%)**: Low probability

## Business Rules

1. `expectedTotalCorners` MUST equal `expectedHomeCorners + expectedAwayCorners`
2. All probabilities must be between 0.0 and 1.0
3. No NaN or negative values allowed
4. High corner teams (e.g., Man City) should show ~7-8 average corners
5. Probabilities must be ordered: over9.5 >= over10.5 >= over11.5

## Error Handling

- Empty team name → 400 Bad Request
- Unknown team → 400 Bad Request with suggestions
- Same home/away team → 400 Bad Request
- Server error → 500 Internal Server Error

## Testing

Run unit tests:
```bash
./mvnw test -pl football-prediction-app -Dtest=CornerStatsServiceTest
```

## Files Structure

```
football-prediction-app/
├── src/main/java/com/app/footballprediction/
│   ├── controller/
│   │   └── CornerStatsController.java
│   ├── service/
│   │   └── CornerStatsService.java
│   └── dto/
│       ├── CornerStatsDTO.java
│       └── CornerPredictionDTO.java
└── src/test/java/com/app/footballprediction/
    └── service/
        └── CornerStatsServiceTest.java

frontend/
├── src/components/
│   ├── team/
│   │   ├── CornerStatsCard.js
│   │   └── corner-stats-card.css
│   └── match/
│       ├── CornerPredictionCard.js
│       └── corner-prediction-card.css
└── src/pages/
    ├── TeamAnalyticsPage.js (updated)
    ├── MatchPreviewPage.js (new)
    └── match-preview-page.css (new)
```


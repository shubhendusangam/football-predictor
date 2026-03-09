# Frontend Components

> **Part of the [Football Prediction Platform](../README.md)** - Vanilla JavaScript frontend components for team analytics and visualizations.

---

## Module Overview

### Purpose
The `frontend` module contains **production-ready UI components** built with vanilla JavaScript, HTML, and CSS. These components provide interactive team analytics visualizations without any framework dependencies (no React, Vue, or Angular).

### Design Philosophy
- **No JSX** - Pure JavaScript DOM manipulation
- **No React** - Framework-free components
- **No External Chart Libraries** - Canvas API for visualizations
- **IIFE Pattern** - No global namespace pollution
- **ES6 Modules** - Modern JavaScript modules support

**Related Documentation:**
- [Main Platform README](../README.md)
- [Main Application](../football-prediction-app/README.md)
- [Common Module](../football-prediction-common/README.md)
- [Model Training Service](../model-training-service/README.md)

---

## Table of Contents

- [Directory Structure](#directory-structure)
- [Components](#components)
- [Pages](#pages)
- [API Integration](#api-integration)
- [Design Specifications](#design-specifications)
- [Usage Examples](#usage-examples)
- [Constraints](#constraints)

---

## Directory Structure

```
frontend/
├── README.md                           # This file
├── src/
│   ├── components/
│   │   ├── team/
│   │   │   ├── README.md               # Component-specific docs
│   │   │   ├── ShotQualityCard.js      # Shot quality component
│   │   │   ├── shot-quality-card.css   # Shot quality styles
│   │   │   ├── FoulsAnalysisCard.js    # Fouls analysis component
│   │   │   ├── fouls-analysis-card.css # Fouls analysis styles
│   │   │   ├── CornerStatsCard.js      # Corner statistics component
│   │   │   ├── corner-stats-card.css   # Corner stats styles
│   │   │   ├── ExpectedGoalsCard.js    # Expected goals (xG) component
│   │   │   ├── expected-goals-card.css # xG styles
│   │   │   ├── KickoffTimeCard.js      # Kickoff time analysis component
│   │   │   └── kickoff-time-card.css   # Kickoff time styles
│   │   │
│   │   └── match/
│   │       ├── CornerPredictionCard.js  # Corner prediction component
│   │       ├── corner-prediction-card.css # Corner prediction styles
│   │       ├── MatchXGCard.js           # Match xG prediction component
│   │       └── match-xg-card.css        # Match xG styles
│   │
│   └── pages/
│       ├── TeamAnalyticsPage.js        # Team analytics page
│       ├── team-analytics-page.css     # Team page styles
│       ├── MatchPreviewPage.js         # Match preview page
│       └── match-preview-page.css      # Match preview styles
```

---

## Components

### Team Components

#### ShotQualityCard

Displays shot efficiency metrics with circular progress indicators and sparkline trends.

##### Features
- **Circular Progress Indicator** - SVG-based with animated stroke
- **Quality Score** - 0-100 scale (converted from backend's 0-10)
- **Shot Accuracy** - Percentage of shots on target
- **Conversion Rate** - Goals per shot ratio
- **Sparkline Chart** - Canvas-based last 10 matches trend
- **Rating Badge** - League average comparison indicator

##### API
```javascript
// ES6 Module
import { 
    renderShotQualityCard,
    renderShotQualityLoading,
    renderShotQualityError,
    fetchAndRenderShotQualityCard 
} from './components/team/ShotQualityCard.js';

// Render a card
renderShotQualityCard(container, teamStats, leagueAverages);

// Fetch and render
fetchAndRenderShotQualityCard(container, 'Arsenal', true);
```

##### IIFE Version (Static Resources)
```javascript
// Available via window.ShotQualityCard
ShotQualityCard.render(container, teamStats, leagueAverages);
ShotQualityCard.fetchAndRender(container, 'Arsenal', true);
ShotQualityCard.renderPair(container, 'Arsenal');  // Home/Away side-by-side
```

##### REST Endpoint
```
GET /api/teams/{teamName}/shot-quality?split=true
```

---

#### FoulsAnalysisCard

Displays fouls statistics and discipline metrics with comparison visualizations.

##### Features
- **Discipline Score** - 0-10 scale rating
- **Fouls Committed/Drawn** - Average per match
- **Fouls Differential** - Visual bar comparison
- **Win Rate by Foul Count** - Low/Controlled/High breakdowns
- **Discipline Badge** - Excellent/Good/Average/Poor indicators

##### API
```javascript
// ES6 Module
import { 
    renderFoulsAnalysisCard,
    renderFoulsAnalysisComparison,
    fetchFoulsAnalysis 
} from './components/team/FoulsAnalysisCard.js';

// Render single card
renderFoulsAnalysisCard(container, foulsData);

// Render comparison (home vs away)
renderFoulsAnalysisComparison(container, homeTeamData, awayTeamData);

// Fetch data
const data = await fetchFoulsAnalysis('Arsenal', true);
```

##### REST Endpoint
```
GET /api/teams/{teamName}/fouls-analysis?isHome=true
```

---

#### CornerStatsCard

Displays corner kick statistics with horizontal bar charts and dominance indicators.

##### Features
- **Average Corners Won/Against** - Horizontal bar visualization
- **Corner Dominance** - Percentage with color coding (Strong/Weak)
- **Success Rate** - Correlation with win rate
- **League Comparison** - Above/Near/Below average indicators
- **Corner Flag Icon** - ⚑ visual element

##### API
```javascript
// ES6 Module
import { 
    renderCornerStatsCard,
    renderCornerStatsLoading,
    renderCornerStatsError,
    fetchAndRenderCornerStatsCard 
} from './components/team/CornerStatsCard.js';

// Render a card
renderCornerStatsCard(container, cornerStats);

// Fetch and render
fetchAndRenderCornerStatsCard(container, 'Arsenal', true);
```

##### REST Endpoint
```
GET /api/teams/{teamName}/corner-stats?isHome=true
```

---

#### ExpectedGoalsCard

Displays expected goals (xG) statistics with over/underperformance indicators.

##### Features
- **xG Per Game** - Expected goals based on shots on target
- **Actual vs Expected** - Over/underperformance visualization
- **Team Conversion Rate** - Compared to league average (0.28)
- **xG Trend** - Recent matches xG progression
- **Home/Away Split** - Separate xG metrics per venue

##### API
```javascript
// ES6 Module
import { ExpectedGoalsCard } from './components/team/ExpectedGoalsCard.js';

// Render example
ExpectedGoalsCard.render(container, teamStats);
```

##### REST Endpoint
```
GET /api/teams/{teamName}/expected-goals
GET /api/teams/{teamName}/expected-goals/split
```

---

#### KickoffTimeCard

Displays team performance broken down by kickoff time slots.

##### Features
- **Time Slot Grid** - Early/Afternoon/Late/Evening performance
- **Win Rate Per Slot** - Visual bars with percentages
- **Performance Classification** - Strong/Average/Weak badges
- **Best/Worst Slot** - Highlighted optimal and weakest times
- **Goal Averages** - Scoring patterns per time slot

##### API
```javascript
// ES6 Module
import { KickoffTimeCard } from './components/team/KickoffTimeCard.js';

// Render example
KickoffTimeCard.render(container, teamStats);
```

##### REST Endpoint
```
GET /api/teams/{teamName}/kickoff-analysis
```

---

### Match Components

#### CornerPredictionCard

Displays match corner predictions with animated counters and probability bars.

##### Features
- **Animated Counter** - Expected total corners with animation
- **Home vs Away Split** - Corner breakdown comparison bars
- **Over/Under Probabilities** - 9.5, 10.5, 11.5 corner thresholds
- **Color-Coded Probability** - Green (>60%), Yellow (40-60%), Red (<40%)
- **Clean Match Preview Layout** - Team names and prediction summary

##### API
```javascript
// ES6 Module
import { 
    renderCornerPredictionCard,
    renderCornerPredictionLoading,
    renderCornerPredictionError,
    fetchAndRenderCornerPredictionCard 
} from './components/match/CornerPredictionCard.js';

// Render a card
renderCornerPredictionCard(container, predictionData);

// Fetch and render
fetchAndRenderCornerPredictionCard(container, 'Arsenal', 'Chelsea');
```

##### REST Endpoint
```
GET /api/matches/predict-corners?home={homeTeam}&away={awayTeam}
```

---

#### MatchXGCard

Displays match-level expected goals (xG) predictions.

##### Features
- **Match xG Prediction** - Expected goals for both teams
- **Over/Under Probabilities** - Goal thresholds (1.5, 2.5, 3.5)
- **Team Comparison** - Side-by-side xG breakdown
- **Confidence Level** - Based on data availability
- **Home Advantage Indicator** - Home team xG boost visualization

##### API
```javascript
// ES6 Module
import { MatchXGCard } from './components/match/MatchXGCard.js';

// Render example
MatchXGCard.render(container, matchData);
```

##### REST Endpoint
```
GET /api/matches/predict-xg?home={homeTeam}&away={awayTeam}
```

---

## Pages

### TeamAnalyticsPage

Unified dashboard combining shot quality, fouls analysis, and other analytics for comprehensive team insights.

#### Features
- **Dual Card Layout** - Home and away statistics side-by-side
- **Tab Integration** - Integrated into team stats modal via router.js
- **Dynamic Loading** - Fetches data based on selected team
- **Error Handling** - Graceful error states with retry options
- **Responsive Design** - CSS Grid with mobile breakpoints

#### API
```javascript
import { 
    TeamAnalyticsPage, 
    createTeamAnalyticsPage 
} from './pages/TeamAnalyticsPage.js';

// Create and render the page
const page = createTeamAnalyticsPage('container-id', 'Arsenal');

// Or use the class directly
const analyticsPage = new TeamAnalyticsPage('container-id', 'Arsenal');
analyticsPage.render();
```

---

### MatchPreviewPage

Page component for displaying match preview analytics with corner and xG predictions.

#### Features
- **Team Selection** - Home and away team inputs
- **Corner Predictions** - Integrated CornerPredictionCard
- **xG Predictions** - Integrated MatchXGCard
- **Loading States** - Skeleton loaders during data fetch
- **Error Handling** - Graceful error states with retry
- **Responsive Layout** - Optimized for all screen sizes

#### API
```javascript
import { 
    MatchPreviewPage, 
    createMatchPreviewPage 
} from './pages/MatchPreviewPage.js';

// Create and render the page
const page = createMatchPreviewPage('container-id');

// Load predictions for a match
page.loadPrediction('Arsenal', 'Chelsea');
```

---

## API Integration

### Base URL
```javascript
const API_BASE = '/api';
```

### Endpoints Used

| Component | Endpoint | Method |
|-----------|----------|--------|
| ShotQualityCard | `/api/teams/{name}/shot-quality` | GET |
| FoulsAnalysisCard | `/api/teams/{name}/fouls-analysis` | GET |
| CornerStatsCard | `/api/teams/{name}/corner-stats` | GET |
| ExpectedGoalsCard | `/api/teams/{name}/expected-goals` | GET |
| KickoffTimeCard | `/api/teams/{name}/kickoff-analysis` | GET |
| CornerPredictionCard | `/api/matches/predict-corners` | GET |
| MatchXGCard | `/api/matches/predict-xg` | GET |

### Error Handling
```javascript
try {
    const response = await fetch(`${API_BASE}/teams/${teamName}/shot-quality`);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    const data = await response.json();
    renderShotQualityCard(container, data);
} catch (error) {
    renderShotQualityError(container, error.message);
}
```

---

## Design Specifications

### Color Palette

| Rating | Color | Hex |
|--------|-------|-----|
| Excellent (80-100) | Green | `#22c55e` |
| Good (60-79) | Green | `#10b981` |
| Average (40-59) | Yellow | `#fbbf24` |
| Poor (0-39) | Red | `#ef4444` |

### Typography
- Font Family: System fonts (sans-serif)
- Headings: 600 weight
- Body: 400 weight

### Layout
- **CSS Grid** - For side-by-side cards
- **Responsive breakpoint** - 768px
- **Soft shadows** - `box-shadow: 0 2px 8px rgba(0,0,0,0.1)`
- **Rounded corners** - `border-radius: 8px`
- **Smooth hover transitions** - `transition: all 0.2s ease`

### League Averages (Constants)
```javascript
const LEAGUE_AVERAGES = {
    shotAccuracy: 32,
    conversionRate: 28
};
const NEAR_AVERAGE_THRESHOLD = 5;  // ±5%
```

---

## Usage Examples

### Basic Usage (IIFE - Static HTML)

```html
<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="/css/shot-quality-card.css">
    <link rel="stylesheet" href="/css/fouls-analysis-card.css">
    <link rel="stylesheet" href="/css/corner-stats-card.css">
    <link rel="stylesheet" href="/css/expected-goals-card.css">
    <link rel="stylesheet" href="/css/kickoff-time-card.css">
</head>
<body>
    <div id="shot-quality-container"></div>
    <div id="fouls-container"></div>
    <div id="corners-container"></div>

    <script src="/js/shot-quality-card.js"></script>
    <script src="/js/fouls-analysis-card.js"></script>
    <script src="/js/corner-stats-card.js"></script>
    <script>
        // Render shot quality cards (home/away pair)
        ShotQualityCard.renderPair(
            document.getElementById('shot-quality-container'),
            'Arsenal'
        );

        // Fetch and render fouls analysis
        FoulsAnalysisCard.fetchAndRender(
            document.getElementById('fouls-container'),
            'Arsenal',
            true  // isHome
        );
    </script>
</body>
</html>
```

### ES6 Module Usage

```javascript
import { renderShotQualityCard } from './components/team/ShotQualityCard.js';
import { renderFoulsAnalysisCard } from './components/team/FoulsAnalysisCard.js';
import { renderCornerStatsCard } from './components/team/CornerStatsCard.js';

const container = document.getElementById('container');
renderShotQualityCard(container, teamStats);
```

---

## Constraints

The frontend components satisfy these requirements:

| Constraint | Status |
|------------|--------|
| ✅ No JSX | Pure JavaScript |
| ✅ No React | Framework-free |
| ✅ No External Chart Libraries | Canvas API |
| ✅ Pure HTML, CSS, vanilla JS | Standards-compliant |
| ✅ Separate CSS files | Modular styles |
| ✅ No global namespace pollution | IIFE wrapper |
| ✅ Production-ready code | Tested and documented |

---

## Metrics

| Metric | Value |
|--------|-------|
| Team Components | 5 (ShotQuality, Fouls, Corners, xG, KickoffTime) |
| Match Components | 2 (CornerPrediction, MatchXG) |
| Pages | 2 (TeamAnalytics, MatchPreview) |
| CSS Files | 7 |
| JavaScript Files | 7 |
| External Dependencies | 0 |

---

**[← Back to Main README](../README.md)**

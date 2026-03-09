# Team Analytics Components - Implementation Summary

> **Part of the [Football Prediction Platform](../../../../README.md)** - Team analytics visualization components.
> 
> For module overview, see [Frontend README](../../README.md).

---

## Overview
Team analytics components for the Football Forecaster application, providing comprehensive statistics with clean, production-ready UIs. This module includes shot quality analysis, fouls & discipline tracking, corner statistics, expected goals (xG), kickoff time analysis, and a unified team analytics page.

### Components
| Component | Description |
|-----------|-------------|
| **ShotQualityCard** | Shot efficiency metrics with circular progress and sparklines |
| **FoulsAnalysisCard** | Fouls statistics and discipline score visualization |
| **CornerStatsCard** | Corner kick statistics with dominance indicators |
| **ExpectedGoalsCard** | Expected goals (xG) with over/underperformance tracking |
| **KickoffTimeCard** | Performance analysis by kickoff time slot |

### Pages
| Page | Location |
|------|----------|
| **TeamAnalyticsPage** | `frontend/src/pages/TeamAnalyticsPage.js` |
| **TeamAnalyticsPage CSS** | `frontend/src/pages/team-analytics-page.css` |

---

## Fouls & Discipline Analysis Card

### Overview
The Fouls & Discipline Analysis Card displays comprehensive fouls statistics and discipline metrics with a clean, production-ready UI. It includes discipline score indicators, fouls comparison bars, and win rate insights.

### Files Created

#### ES6 Module Version (Frontend)
**Location:** `frontend/src/components/team/FoulsAnalysisCard.js`

Exports:
- `renderFoulsAnalysisCard(container, foulsData)` - Main render function
- `renderFoulsAnalysisComparison(container, homeTeamData, awayTeamData)` - Side-by-side comparison
- `fetchFoulsAnalysis(teamName, isHome)` - Fetch data from API

#### CSS Styles
**Locations:**
- `frontend/src/components/team/fouls-analysis-card.css`
- `football-prediction-app/src/main/resources/static/css/fouls-analysis-card.css`

### Backend Service
**Location:** `football-prediction-app/src/main/java/com/app/footballprediction/service/FoulsAnalysisService.java`

### DTO
**Location:** `football-prediction-app/src/main/java/com/app/footballprediction/dto/FoulsAnalysisDTO.java`

### REST Endpoint
```
GET /api/teams/{teamName}/fouls-analysis?isHome=true
```

### Features
- Discipline score (0-10 scale, dynamically normalized)
- Average fouls committed and drawn
- Fouls differential visualization
- Win rate by foul count (low/controlled/high)
- Horizontal bar comparison
- Color-coded discipline badges (Excellent/Good/Average/Poor)
- Prediction section for team comparison

---

## Shot Quality Analytics Card

## Overview
A new Shot Quality Analytics Card module has been implemented for the Football Forecaster application. This feature displays comprehensive shot efficiency metrics with a clean, production-ready UI.

## Files Created

### 1. ES6 Module Version (Frontend)
**Location:** `frontend/src/components/team/ShotQualityCard.js`

Exports:
- `renderShotQualityCard(container, teamStats, leagueAverages)` - Main render function
- `renderShotQualityLoading(container)` - Loading state
- `renderShotQualityError(container, message)` - Error state
- `fetchAndRenderShotQualityCard(container, teamName, isHome)` - Fetch and render
- Constants: `LEAGUE_AVERAGES`, `NEAR_AVERAGE_THRESHOLD`, `RATING_LEVELS`

### 2. IIFE Version (Static Resources)
**Location:** `football-prediction-app/src/main/resources/static/js/shot-quality-card.js`

Exposes via `window.ShotQualityCard`:
- `render(container, teamStats, leagueAverages)`
- `renderLoading(container)`
- `renderError(container, message)`
- `fetchAndRender(container, teamName, isHome)`
- `renderPair(container, teamName)` - Renders home/away side-by-side

### 3. CSS Styles
**Locations:**
- `frontend/src/components/team/shot-quality-card.css`
- `football-prediction-app/src/main/resources/static/css/shot-quality-card.css`

### 4. TeamAnalyticsPage
**Location:** `frontend/src/pages/TeamAnalyticsPage.js`

Exports:
- `TeamAnalyticsPage` class
- `renderShotQualitySection(container, teamName)`
- `createTeamAnalyticsPage(containerId, teamName)`

### 5. Demo Page
**Location:** `football-prediction-app/src/main/resources/static/demo/shot-quality-demo.html`

Interactive demo showcasing all component features.

## Files Modified

### index.html
Added:
- CSS link: `css/shot-quality-card.css`
- Script: `js/shot-quality-card.js`

### router.js
Added:
- New tab: "🎯 Shot Quality" in team stats modal
- Method: `renderShotQualityTab()` - Renders home/away shot quality cards

## Features

### Circular Progress Indicator
- SVG-based with animated stroke-dasharray
- Color-coded by rating level (Excellent/Good/Average/Poor)
- 0-100 scale (converted from backend's 0-10 scale)

### Statistics Display
- Shot Accuracy (%)
- Conversion Rate (%)

### Rating Badge
- Color-coded comparison with league average
- Green: Above league average (>5% above)
- Yellow: Near league average (±5%)
- Red: Below league average (>5% below)

### Sparkline Chart
- Canvas API implementation (no external libraries)
- Shows last 10 matches trend
- Dual lines: Shots (blue) and Goals (green)
- Dots at each data point

## League Averages
- Shot Accuracy: 32%
- Conversion Rate: 28%
- Near-average threshold: ±5%

## API Integration
Uses existing endpoint:
```
GET /api/teams/{teamName}/shot-quality?split=true
```

Response structure:
```json
{
  "teamName": "Arsenal",
  "home": {
    "teamName": "Arsenal",
    "isHome": true,
    "qualityScore": 7.5,
    "shotAccuracy": 38.5,
    "conversionRate": 0.32,
    "shotsTrend": [...]
  },
  "away": { ... }
}
```

## Usage Examples

### Basic Usage (IIFE)
```html
<link rel="stylesheet" href="/css/shot-quality-card.css">
<script src="/js/shot-quality-card.js"></script>

<div id="container"></div>

<script>
// Single card
ShotQualityCard.fetchAndRender(
    document.getElementById('container'),
    'Arsenal',
    null
);

// Home/Away pair
ShotQualityCard.renderPair(
    document.getElementById('container'),
    'Arsenal'
);
</script>
```

### ES6 Module
```javascript
import { renderShotQualityCard } from './components/team/ShotQualityCard.js';

const teamStats = {
    teamName: 'Arsenal',
    isHome: true,
    qualityScore: 7.5,
    shotAccuracy: 38.5,
    conversionRate: 0.32,
    shotsTrend: [{ shots: 15, goals: 3 }, ...]
};

renderShotQualityCard(container, teamStats);
```

## Validation Test Cases

### Arsenal (High Quality - Green Badge)
- Expected: Quality score > 60
- Rating badge: "Above League Average" (green)

### Southampton (Lower Quality - Yellow/Red Badge)
- Expected: Quality score around 40-50
- Rating badge: "Near League Average" (yellow) or "Below League Average" (red)

### Verification Checklist
- [ ] qualityScore calculation matches backend (0-10 → 0-100)
- [ ] League average comparison is accurate (±5% threshold)
- [ ] Sparkline reflects last 10 matches correctly
- [ ] Responsive on mobile devices
- [ ] Animations work smoothly
- [ ] Error states display correctly

## Design Specifications

### Colors
- Excellent (80-100): Green (#22c55e)
- Good (60-79): Green (#10b981)
- Average (40-59): Yellow (#fbbf24)
- Poor (0-39): Red (#ef4444)

### Layout
- Side-by-side cards using CSS Grid
- Responsive breakpoint at 768px
- Soft shadows and rounded corners
- Smooth hover transitions

---

## Team Analytics Page

### Overview
The TeamAnalyticsPage (`frontend/src/pages/TeamAnalyticsPage.js`) is a unified dashboard that combines shot quality and fouls analysis for comprehensive team insights.

### Features
- **Dual Card Layout**: Home and away statistics side-by-side
- **Tab Integration**: Integrated into team stats modal via router.js
- **Dynamic Loading**: Fetches data based on selected team
- **Error Handling**: Graceful error states with retry options

### Usage
```javascript
import { TeamAnalyticsPage, createTeamAnalyticsPage } from './pages/TeamAnalyticsPage.js';

// Create and render the page
const page = createTeamAnalyticsPage('container-id', 'Arsenal');

// Or use the class directly
const analyticsPage = new TeamAnalyticsPage('container-id', 'Arsenal');
analyticsPage.render();
```

### Integration with Router
The router.js has been updated to include a "🎯 Shot Quality" tab in the team stats modal, which renders the shot quality cards using `renderShotQualityTab()`.

---

## Constraints Satisfied
✅ No JSX  
✅ No React  
✅ No external chart libraries  
✅ Pure HTML, CSS, vanilla JS  
✅ Separate CSS file  
✅ No global namespace pollution (IIFE wrapper)  
✅ Production-ready code  

---

**[← Back to Frontend README](../../README.md)** | **[← Back to Main README](../../../../README.md)**  

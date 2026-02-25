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
│   │   └── team/
│   │       ├── README.md               # Component-specific docs
│   │       ├── ShotQualityCard.js      # Shot quality component
│   │       ├── shot-quality-card.css   # Shot quality styles
│   │       ├── FoulsAnalysisCard.js    # Fouls analysis component
│   │       └── fouls-analysis-card.css # Fouls analysis styles
│   │
│   └── pages/
│       ├── TeamAnalyticsPage.js        # Team analytics page
│       └── team-analytics-page.css     # Page styles
```

---

## Components

### ShotQualityCard

Displays shot efficiency metrics with circular progress indicators and sparkline trends.

#### Features
- **Circular Progress Indicator** - SVG-based with animated stroke
- **Quality Score** - 0-100 scale (converted from backend's 0-10)
- **Shot Accuracy** - Percentage of shots on target
- **Conversion Rate** - Goals per shot ratio
- **Sparkline Chart** - Canvas-based last 10 matches trend
- **Rating Badge** - League average comparison indicator

#### API
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

#### IIFE Version (Static Resources)
```javascript
// Available via window.ShotQualityCard
ShotQualityCard.render(container, teamStats, leagueAverages);
ShotQualityCard.fetchAndRender(container, 'Arsenal', true);
ShotQualityCard.renderPair(container, 'Arsenal');  // Home/Away side-by-side
```

#### REST Endpoint
```
GET /api/teams/{teamName}/shot-quality?split=true
```

#### Response Structure
```json
{
  "teamName": "Arsenal",
  "home": {
    "teamName": "Arsenal",
    "isHome": true,
    "qualityScore": 7.5,
    "shotAccuracy": 38.5,
    "conversionRate": 0.32,
    "shotsTrend": [
      { "shots": 15, "goals": 3 },
      { "shots": 12, "goals": 2 }
    ]
  },
  "away": { ... }
}
```

---

### FoulsAnalysisCard

Displays fouls statistics and discipline metrics with comparison visualizations.

#### Features
- **Discipline Score** - 0-10 scale rating
- **Fouls Committed/Drawn** - Average per match
- **Fouls Differential** - Visual bar comparison
- **Win Rate by Foul Count** - Low/Controlled/High breakdowns
- **Discipline Badge** - Excellent/Good/Average/Poor indicators

#### API
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

#### REST Endpoint
```
GET /api/teams/{teamName}/fouls-analysis?isHome=true
```

#### Response Structure
```json
{
  "teamName": "Arsenal",
  "isHome": true,
  "disciplineScore": 7.2,
  "avgFoulsCommitted": 10.5,
  "avgFoulsDrawn": 12.3,
  "foulsDifferential": -1.8,
  "winRateByFoulCount": {
    "low": 65.0,
    "controlled": 52.0,
    "high": 38.0
  },
  "disciplineBadge": "Good"
}
```

---

## Pages

### TeamAnalyticsPage

Unified dashboard combining shot quality and fouls analysis for comprehensive team insights.

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

#### Integration with Router
The `router.js` has been updated to include a "🎯 Shot Quality" tab in the team stats modal:

```javascript
// In router.js
renderShotQualityTab() {
    const container = document.getElementById('shot-quality-container');
    ShotQualityCard.renderPair(container, this.selectedTeam);
}
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
</head>
<body>
    <div id="shot-quality-container"></div>
    <div id="fouls-container"></div>

    <script src="/js/shot-quality-card.js"></script>
    <script src="/js/fouls-analysis-card.js"></script>
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

// Manual data rendering
const teamStats = {
    teamName: 'Arsenal',
    isHome: true,
    qualityScore: 7.5,
    shotAccuracy: 38.5,
    conversionRate: 0.32,
    shotsTrend: [
        { shots: 15, goals: 3 },
        { shots: 12, goals: 2 },
        // ... last 10 matches
    ]
};

const container = document.getElementById('container');
renderShotQualityCard(container, teamStats);
```

### Sparkline Chart (Canvas API)

```javascript
function renderSparkline(canvas, data) {
    const ctx = canvas.getContext('2d');
    const width = canvas.width;
    const height = canvas.height;
    
    // Clear canvas
    ctx.clearRect(0, 0, width, height);
    
    // Draw line
    ctx.beginPath();
    ctx.strokeStyle = '#3b82f6';  // Blue
    ctx.lineWidth = 2;
    
    data.forEach((point, i) => {
        const x = (i / (data.length - 1)) * width;
        const y = height - (point.shots / maxShots) * height;
        
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
    });
    
    ctx.stroke();
    
    // Draw dots
    data.forEach((point, i) => {
        const x = (i / (data.length - 1)) * width;
        const y = height - (point.shots / maxShots) * height;
        
        ctx.beginPath();
        ctx.arc(x, y, 3, 0, 2 * Math.PI);
        ctx.fillStyle = '#3b82f6';
        ctx.fill();
    });
}
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

## Testing

### Validation Test Cases

#### Arsenal (High Quality - Green Badge)
- Expected: Quality score > 60
- Rating badge: "Above League Average" (green)

#### Southampton (Lower Quality - Yellow/Red Badge)
- Expected: Quality score around 40-50
- Rating badge: "Near League Average" (yellow) or "Below League Average" (red)

### Verification Checklist
- [ ] `qualityScore` calculation matches backend (0-10 → 0-100)
- [ ] League average comparison is accurate (±5% threshold)
- [ ] Sparkline reflects last 10 matches correctly
- [ ] Responsive on mobile devices
- [ ] Animations work smoothly
- [ ] Error states display correctly

### Demo Page
A demo page is available at:
```
/static/demo/shot-quality-demo.html
```

---

## Metrics

| Metric | Value |
|--------|-------|
| Components | 2 |
| Pages | 1 |
| CSS Files | 4 |
| JavaScript Files | 3 |
| External Dependencies | 0 |

---

**[← Back to Main README](../README.md)**


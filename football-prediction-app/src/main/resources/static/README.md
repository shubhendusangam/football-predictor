# Football Forecaster - UI Layout Documentation

## Overview
Professional dark-themed dashboard layout for the Football Forecaster application. Built with vanilla CSS (Grid + Flexbox) and JavaScript - no external UI frameworks.

## Structure
```
/ui
├── index.html          # Main HTML structure
├── css/
│   └── layout.css      # Complete styling system
└── js/
    └── layout.js       # Interactive functionality
```

## Features

### 1. Sticky Top Navbar
- **Brand**: "Football Forecaster" with gradient green logo
- **Navigation Links**: Dashboard | Predictions | Historical | Insights | Settings
- **Active State**: Green highlighting with bottom border
- **Responsive**: Collapses to hamburger menu on mobile

### 2. Collapsible Sidebar
- **Desktop**: Toggle between expanded (240px) and collapsed (64px)
- **Mobile**: Slide-in/slide-out drawer
- **Icons + Labels**: Clear navigation with emoji icons
- **State Persistence**: Remembers collapsed state via localStorage
- **Smooth Animations**: 0.3s transitions

### 3. Main Content Area
- **Responsive Grid**: Auto-fit columns (min 300px)
- **Flexible Layout**: Adapts to sidebar state
- **Proper Spacing**: 2rem padding, responsive adjustments

### 4. Styling System

#### Color Palette
```css
--bg-primary: #0f172a      /* Main background */
--bg-secondary: #1e293b    /* Cards, sidebar */
--bg-tertiary: #334155     /* Hover states */
--accent-green: #22c55e    /* Primary actions */
--text-primary: #f1f5f9    /* Main text */
--text-secondary: #cbd5e1  /* Secondary text */
--text-muted: #94a3b8      /* Muted text */
```

#### CSS Variables
All key measurements use CSS custom properties for easy theming:
- `--navbar-height: 64px`
- `--sidebar-width: 240px`
- `--transition-speed: 0.3s`

### 5. Reusable Components

#### Card Container
```html
<div class="card">
    <div class="card-header">
        <h3 class="card-title">Title</h3>
        <span class="badge badge-success">Badge</span>
    </div>
    <div class="card-body">
        <p class="card-text">Content</p>
    </div>
</div>
```

Features:
- Hover effect with elevation
- Border highlighting
- Flexible header/body structure

#### Badge Styles
```html
<span class="badge badge-success">Live</span>
<span class="badge badge-info">85%</span>
<span class="badge badge-warning">Trending</span>
<span class="badge badge-danger">Alert</span>
```

#### Button Styles
```html
<button class="btn btn-primary">Primary Action</button>
<button class="btn btn-secondary">Secondary</button>
<button class="btn btn-outline">Outline</button>
```

#### Section Headers
```html
<div class="section-header">
    <h3 class="section-title">Section Name</h3>
</div>
```

## JavaScript API

### LayoutManager
Handles sidebar toggle, navigation state, and responsive behavior.

**Key Methods:**
- `toggleSidebar()` - Toggle sidebar state
- `setActiveNavigation(href)` - Set active navigation item
- `navigateToSection(href)` - Navigate to section (routing placeholder)

### ComponentManager
Manages interactive card components and effects.

**Key Methods:**
- `createRipple(event, element)` - Add ripple effect to elements

### ThemeManager
Manages theme switching (prepared for future light theme).

**Key Methods:**
- `toggleTheme()` - Switch between light/dark themes

### UIUtils
Utility functions for dynamic component creation.

**Available Functions:**
```javascript
// Create a card
UIUtils.createCard(title, content, badge);

// Create a badge
UIUtils.createBadge(text, type);

// Create a button
UIUtils.createButton(text, type, onClick);

// Inject content into dashboard
UIUtils.injectDashboardContent(cards);

// Loading/Error states
UIUtils.showLoading(container);
UIUtils.showError(container, message);
```

## Usage Examples

### Dynamic Card Injection
```javascript
const cards = [
    {
        title: 'Match Predictions',
        content: '<p>Dynamic content here</p>',
        badge: { type: 'success', text: 'Live' }
    },
    {
        title: 'Statistics',
        content: '<div>Stats content</div>',
        badge: { type: 'info', text: '85%' }
    }
];

UIUtils.injectDashboardContent(cards);
```

### Creating Components Programmatically
```javascript
// Create a new card
const newCard = UIUtils.createCard(
    'Custom Card',
    '<p>Custom content</p>',
    { type: 'warning', text: 'New' }
);

// Add to dashboard
document.querySelector('.dashboard-grid').appendChild(newCard);
```

### Navigation Control
```javascript
// Programmatically navigate
window.layoutManager.setActiveNavigation('#predictions');

// Toggle sidebar
window.layoutManager.toggleSidebar();
```

## Responsive Breakpoints

- **Desktop**: > 1024px - Full sidebar visible
- **Tablet**: 768px - 1024px - Collapsible sidebar, hidden navbar links
- **Mobile**: < 768px - Drawer sidebar, stacked layout
- **Small Mobile**: < 480px - Optimized spacing and sizing

## Browser Support
- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## Integration with Backend

### API Connection Points
The layout is ready to receive data from the Spring Boot backend:

1. **Dashboard**: `/api/v1/dashboard/stats`
2. **Predictions**: `/api/v1/predictions`
3. **Historical**: `/api/v1/matches/history`
4. **Teams**: `/api/v1/teams`

### Example Integration
```javascript
// Fetch and display predictions
fetch('/api/v1/predictions')
    .then(response => response.json())
    .then(data => {
        const cards = data.map(prediction => ({
            title: `${prediction.homeTeam} vs ${prediction.awayTeam}`,
            content: `<div class="prediction-content">
                <p>Predicted Winner: ${prediction.predictedWinner}</p>
                <p>Confidence: ${prediction.confidence}%</p>
            </div>`,
            badge: { type: 'success', text: 'AI' }
        }));
        
        UIUtils.injectDashboardContent(cards);
    });
```

## Customization

### Changing Colors
Edit CSS variables in `layout.css`:
```css
:root {
    --accent-green: #your-color;
    --bg-primary: #your-bg;
}
```

### Adding New Pages
1. Add link to navbar and sidebar
2. Update `titleMap` and `descriptionMap` in `layout.js`
3. Implement section content

### Custom Components
Use the utility classes and component patterns to maintain consistency:
- Follow the card structure
- Use defined badge types
- Apply consistent spacing utilities

## Performance
- **CSS**: Single file, optimized selectors
- **JavaScript**: Event delegation where possible
- **No dependencies**: Zero external libraries
- **Lazy loading ready**: Structure supports code splitting

## Accessibility
- Semantic HTML5 elements
- ARIA labels on interactive elements
- Keyboard navigation support
- High contrast color ratios (WCAG AA compliant)

## Future Enhancements
- [ ] Light theme implementation
- [ ] Advanced routing system
- [ ] Real-time data updates via WebSocket
- [ ] Chart/graph components
- [ ] Modal dialogs
- [ ] Toast notifications
- [ ] Data tables with sorting/filtering

## License
Part of the Football Prediction project.

---

**Ready for Component Injection** ✓  
The layout is fully functional and prepared for dynamic content from the backend API.


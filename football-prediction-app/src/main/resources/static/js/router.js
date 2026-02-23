/**
 * Football Forecaster Router
 * Handles SPA routing and view switching
 *
 * FIXES APPLIED:
 * - Added route-change event dispatch for layout sync
 * - Added defensive null checks
 * - Improved prediction form cleanup on route change
 */

class Router {
    constructor() {
        this.routes = new Map();
        this.currentRoute = null;
        this.mainContent = document.getElementById('mainContent');
        this.initialized = false;
        this.init();
    }

    /**
     * Initialize router
     */
    init() {
        if (this.initialized) return;
        this.initialized = true;

        // Register routes
        this.registerRoutes();

        // Listen for hash changes
        window.addEventListener('hashchange', () => this.handleRouteChange());

        // Listen for API errors to show toast notifications
        window.addEventListener('api-error', (event) => {
            const { message, status, endpoint } = event.detail || {};
            console.warn('[Router] API Error:', { message, status, endpoint });
            if (window.UI && window.UI.showToast) {
                window.UI.showToast(message || 'API request failed', 'error');
            }
        });

        // Handle initial route
        this.handleRouteChange();

        console.log('[Router] Initialized');
    }

    /**
     * Register all application routes
     */
    registerRoutes() {
        this.routes.set('#dashboard', {
            title: 'Dashboard',
            description: 'Overview of match predictions and team statistics',
            render: () => this.renderDashboard()
        });

        this.routes.set('#predictions', {
            title: 'Predictions',
            description: 'AI-powered match outcome predictions',
            render: () => this.renderPredictions()
        });

        this.routes.set('#historical', {
            title: 'Historical Data',
            description: 'Historical match data and trends',
            render: () => this.renderHistorical()
        });

        this.routes.set('#insights', {
            title: 'Insights',
            description: 'Data insights and analytics',
            render: () => this.renderInsights()
        });

        this.routes.set('#teams', {
            title: 'Teams',
            description: 'Team information and statistics',
            render: () => this.renderTeams()
        });

        this.routes.set('#matches', {
            title: 'Matches',
            description: 'Match schedules and results',
            render: () => this.renderMatches()
        });


        this.routes.set('#admin', {
            title: 'Admin Control Panel',
            description: 'System administration and configuration',
            render: () => this.renderAdmin()
        });
    }

    /**
     * Handle route change
     */
    handleRouteChange() {
        const hash = window.location.hash || '#dashboard';
        const route = this.routes.get(hash);

        if (route) {
            this.currentRoute = hash;

            // Add fade-out/fade-in transition
            this.transitionToRoute(route, hash);
        } else {
            // Default to dashboard if route not found
            window.location.hash = '#dashboard';
        }
    }

/**
     * Transition to a new route with animation
     */
    transitionToRoute(route, hash) {
        // Show top loading bar
        if (window.UI && window.UI.showTopLoading) {
            window.UI.showTopLoading();
        }

        // Apply fade-out effect
        this.mainContent.style.opacity = '0';
        this.mainContent.style.transform = 'translateY(10px)';
        this.mainContent.style.transition = 'opacity 0.15s ease-out, transform 0.15s ease-out';

        setTimeout(() => {
            this.updatePageHeader(route);
            route.render();

            // Apply fade-in effect
            requestAnimationFrame(() => {
                this.mainContent.style.opacity = '1';
                this.mainContent.style.transform = 'translateY(0)';

                // Hide top loading bar and clean up inline styles after animation
                setTimeout(() => {
                    this.mainContent.style.transition = '';
                    if (window.UI && window.UI.hideTopLoading) {
                        window.UI.hideTopLoading();
                    }
                }, 300);
            });

            // Dispatch route-change event for other components (like layout)
            window.dispatchEvent(new CustomEvent('route-change', {
                detail: { route: hash, title: route.title }
            }));
        }, 150);
    }

    /**
     * Update page header
     */
    updatePageHeader(route) {
        const pageTitle = document.querySelector('.page-title');
        const pageDescription = document.querySelector('.page-description');

        if (pageTitle) pageTitle.textContent = route.title;
        if (pageDescription) pageDescription.textContent = route.description;
    }

    /**
     * Render Dashboard view
     */
    renderDashboard() {
        this.mainContent.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">Dashboard</h2>
                <p class="page-description">Overview of match predictions and team statistics</p>
            </div>
            <div class="dashboard-container" id="dashboardContainer">
                <!-- Row 1: Upcoming Matches + League Standings -->
                <div class="dashboard-row-1">
                    <div class="dashboard-card" id="upcomingMatchesCard">
                        <div class="dashboard-card-loading">
                            <div class="dashboard-skeleton title"></div>
                            <div class="dashboard-skeleton card"></div>
                            <div class="dashboard-skeleton card"></div>
                        </div>
                    </div>
                    <div class="dashboard-card" id="leagueStandingsCard">
                        <div class="dashboard-card-loading">
                            <div class="dashboard-skeleton title"></div>
                            <div class="dashboard-skeleton card"></div>
                            <div class="dashboard-skeleton card"></div>
                        </div>
                    </div>
                </div>

                <!-- Row 2: Today's Predictions + Top Teams + Model Accuracy -->
                <div class="dashboard-row-2">
                    <div class="dashboard-card" id="todaysPredictionsCard">
                        <div class="dashboard-card-loading">
                            <div class="dashboard-skeleton title"></div>
                            <div class="dashboard-skeleton card"></div>
                        </div>
                    </div>
                    <div class="dashboard-card" id="topTeamsCard">
                        <div class="dashboard-card-loading">
                            <div class="dashboard-skeleton title"></div>
                            <div class="dashboard-skeleton card"></div>
                        </div>
                    </div>
                    <div class="dashboard-card" id="modelAccuracyCard">
                        <div class="dashboard-card-loading">
                            <div class="dashboard-skeleton title"></div>
                            <div class="dashboard-skeleton card"></div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Initialize dashboard manager
        if (window.dashboardManager) {
            window.dashboardManager.loadDashboard();
        }
    }

    /**
     * Render Predictions view
     */
    renderPredictions() {
        // Get pre-filled team names from sessionStorage
        const homeTeam = sessionStorage.getItem('predictHomeTeam') || '';
        const awayTeam = sessionStorage.getItem('predictAwayTeam') || '';

        this.mainContent.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">Match Predictions</h2>
                <p class="page-description">AI-powered match outcome predictions</p>
            </div>

            <div class="card" style="max-width: 800px; margin: 0 auto;">
                <div class="card-header">
                    <h3 class="card-title">Predict Match Outcome</h3>
                    <span class="badge badge-info">AI Powered</span>
                </div>
                <div class="card-body">
                    <form id="predictionForm" style="display: flex; flex-direction: column; gap: 1.5rem;">
                        <div>
                            <label style="display: block; margin-bottom: 0.5rem; font-weight: 500;">Home Team</label>
                            <input
                                type="text"
                                id="homeTeamInput"
                                class="form-input"
                                placeholder="Enter home team name (e.g., Arsenal)"
                                value="${homeTeam}"
                                required
                                style="width: 100%; padding: 0.75rem; border: 1px solid var(--border-color); border-radius: 0.5rem; background: var(--bg-secondary); color: var(--text-primary); font-size: 1rem;"
                            />
                        </div>

                        <div>
                            <label style="display: block; margin-bottom: 0.5rem; font-weight: 500;">Away Team</label>
                            <input
                                type="text"
                                id="awayTeamInput"
                                class="form-input"
                                placeholder="Enter away team name (e.g., Chelsea)"
                                value="${awayTeam}"
                                required
                                style="width: 100%; padding: 0.75rem; border: 1px solid var(--border-color); border-radius: 0.5rem; background: var(--bg-secondary); color: var(--text-primary); font-size: 1rem;"
                            />
                        </div>

                        <div>
                            <button
                                type="submit"
                                class="btn btn-primary"
                                style="width: 100%; padding: 0.875rem; font-size: 1rem; font-weight: 600;"
                            >
                                🎯 Predict Match Outcome
                            </button>
                        </div>
                    </form>

                    <div id="predictionResults" style="margin-top: 2rem; display: none;"></div>
                </div>
            </div>

            <div style="max-width: 800px; margin: 2rem auto;">
                <div class="card">
                    <div class="card-header">
                        <h3 class="card-title">💡 How It Works</h3>
                    </div>
                    <div class="card-body">
                        <ul style="list-style: none; padding: 0; margin: 0;">
                            <li style="padding: 0.75rem 0; border-bottom: 1px solid var(--border-color);">
                                <strong>📊 Historical Analysis</strong> - Analyzes past match performance
                            </li>
                            <li style="padding: 0.75rem 0; border-bottom: 1px solid var(--border-color);">
                                <strong>🔥 Current Form</strong> - Considers recent team performance
                            </li>
                            <li style="padding: 0.75rem 0; border-bottom: 1px solid var(--border-color);">
                                <strong>🎯 Head-to-Head</strong> - Reviews previous encounters
                            </li>
                            <li style="padding: 0.75rem 0;">
                                <strong>🤖 AI Model</strong> - Machine learning prediction engine
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        `;

        // Clear stored values after using them
        if (homeTeam || awayTeam) {
            sessionStorage.removeItem('predictHomeTeam');
            sessionStorage.removeItem('predictAwayTeam');
        }

        // Setup form submission
        this.setupPredictionForm();
    }

    /**
     * Setup prediction form submission
     */
    setupPredictionForm() {
        const form = document.getElementById('predictionForm');
        if (!form) return;

        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            const homeTeam = document.getElementById('homeTeamInput').value.trim();
            const awayTeam = document.getElementById('awayTeamInput').value.trim();
            const resultsDiv = document.getElementById('predictionResults');

            if (!homeTeam || !awayTeam) {
                this.showError(resultsDiv, 'Please enter both team names');
                return;
            }

            try {
                // Show loading state with skeleton
                resultsDiv.style.display = 'block';
                resultsDiv.innerHTML = `
                    <div class="fade-in" style="padding: 1.5rem; background: var(--bg-tertiary); border-radius: 0.75rem;">
                        <div class="flex items-center gap-3" style="margin-bottom: 1rem;">
                            <div class="loading-dots">
                                <span></span>
                                <span></span>
                                <span></span>
                            </div>
                            <span style="color: var(--text-muted);">Analyzing match data...</span>
                        </div>
                        <div class="skeleton skeleton-text" style="width: 80%; margin-bottom: 0.5rem;"></div>
                        <div class="skeleton skeleton-text" style="width: 60%; margin-bottom: 0.5rem;"></div>
                        <div class="skeleton skeleton-text" style="width: 70%;"></div>
                    </div>
                `;

                // Call prediction API - use centralized API if available, fallback to legacy apiClient
                let prediction;
                if (window.api && typeof window.api.predict === 'function') {
                    prediction = await window.api.predict(homeTeam, awayTeam);
                } else if (window.apiClient && typeof window.apiClient.predict === 'function') {
                    prediction = await window.apiClient.predict(homeTeam, awayTeam);
                } else {
                    throw new Error('No API client available');
                }

                // Display results - use team names from prediction response if available
                const normalizedHome = prediction.homeTeam || homeTeam;
                const normalizedAway = prediction.awayTeam || awayTeam;
                this.displayPredictionResults(resultsDiv, prediction, normalizedHome, normalizedAway);

                // Also update form inputs with normalized names for consistency
                document.getElementById('homeTeamInput').value = normalizedHome;
                document.getElementById('awayTeamInput').value = normalizedAway;

            } catch (error) {
                console.error('Prediction error:', error);
                this.showError(resultsDiv, error.message || 'Failed to generate prediction');
            }
        });
    }

    /**
     * Display prediction results
     */
    displayPredictionResults(container, prediction, homeTeam, awayTeam) {
        const predictionClass = prediction.prediction?.toLowerCase() || 'draw';
        const predictionColor = {
            'home_win': 'var(--accent-green)',
            'away_win': 'var(--accent-blue)',
            'draw': 'var(--accent-yellow)'
        };

        container.innerHTML = `
            <div style="background: var(--bg-tertiary); border-radius: 0.75rem; padding: 2rem; border: 2px solid ${predictionColor[predictionClass] || 'var(--border-color)'};">
                <div style="text-align: center; margin-bottom: 2rem;">
                    <h3 style="font-size: 1.5rem; margin-bottom: 0.5rem;">Match Prediction</h3>
                    <p style="color: var(--text-muted);">${this.escapeHtml(homeTeam)} vs ${this.escapeHtml(awayTeam)}</p>
                </div>

                <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin-bottom: 2rem;">
                    <div style="text-align: center; padding: 1.5rem; background: var(--bg-secondary); border-radius: 0.5rem;">
                        <div style="font-size: 0.875rem; color: var(--text-muted); margin-bottom: 0.5rem;">Home Win</div>
                        <div style="font-size: 2rem; font-weight: bold; color: var(--accent-green);">
                            ${Math.round((prediction.probHomeWin || 0) * 100)}%
                        </div>
                    </div>
                    <div style="text-align: center; padding: 1.5rem; background: var(--bg-secondary); border-radius: 0.5rem;">
                        <div style="font-size: 0.875rem; color: var(--text-muted); margin-bottom: 0.5rem;">Draw</div>
                        <div style="font-size: 2rem; font-weight: bold; color: var(--accent-yellow);">
                            ${Math.round((prediction.probDraw || 0) * 100)}%
                        </div>
                    </div>
                    <div style="text-align: center; padding: 1.5rem; background: var(--bg-secondary); border-radius: 0.5rem;">
                        <div style="font-size: 0.875rem; color: var(--text-muted); margin-bottom: 0.5rem;">Away Win</div>
                        <div style="font-size: 2rem; font-weight: bold; color: var(--accent-blue);">
                            ${Math.round((prediction.probAwayWin || 0) * 100)}%
                        </div>
                    </div>
                </div>

                <div style="text-align: center; padding: 1.5rem; background: ${predictionColor[predictionClass] || 'var(--bg-secondary)'}20; border-radius: 0.5rem;">
                    <div style="font-size: 0.875rem; margin-bottom: 0.5rem;">Predicted Outcome</div>
                    <div style="font-size: 1.5rem; font-weight: bold; color: ${predictionColor[predictionClass] || 'var(--text-primary)'};">
                        ${prediction.prediction?.replace('_', ' ') || 'N/A'}
                    </div>
                    <div style="font-size: 0.875rem; color: var(--text-muted); margin-top: 0.5rem;">
                        Confidence: ${prediction.confidence || 'Medium'}
                    </div>
                </div>

                ${prediction.insights ? `
                    <div style="margin-top: 1.5rem; padding: 1rem; background: var(--bg-secondary); border-radius: 0.5rem;">
                        <strong>📊 Key Insights:</strong>
                        <ul style="margin: 0.5rem 0 0 1.5rem; color: var(--text-muted);">
                            ${prediction.insights.map(insight => `<li>${this.escapeHtml(insight)}</li>`).join('')}
                        </ul>
                    </div>
                ` : ''}
            </div>

            <!-- Pre-Match Insights Section -->
            <div class="pre-match-insights" id="preMatchInsightsSection">
                <div class="pre-match-insights-container">
                    <div class="pre-match-insights-header">
                        <span class="header-icon">📊</span>
                        <h3 class="header-title">Pre-Match Insights</h3>
                        <span class="header-badge">AI Analysis</span>
                    </div>
                    <div class="pre-match-insights-content" id="preMatchInsightsContent">
                        <div class="pre-match-insights-loading">
                            <div class="loading-spinner"></div>
                            <p class="loading-text">Loading pre-match insights...</p>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Load Pre-Match Insights asynchronously (homeTeam/awayTeam are already normalized from caller)
        this.loadPreMatchInsights(homeTeam, awayTeam);
    }

    /**
     * Load Pre-Match Insights from API
     */
    async loadPreMatchInsights(homeTeam, awayTeam) {
        const contentEl = document.getElementById('preMatchInsightsContent');
        if (!contentEl) return;

        try {
            // Fetch match analysis which includes pre-match and H2H insights
            let data;
            if (window.api && typeof window.api.getMatchAnalysis === 'function') {
                data = await window.api.getMatchAnalysis(homeTeam, awayTeam);
            } else {
                const response = await fetch(`/api/analytics/match?homeTeam=${encodeURIComponent(homeTeam)}&awayTeam=${encodeURIComponent(awayTeam)}`);
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }
                data = await response.json();
            }

            // Render the Pre-Match Insights UI - use team names from API response for consistency
            const normalizedHomeTeam = data.homeTeam || data.preMatchInsights?.homeTeam || homeTeam;
            const normalizedAwayTeam = data.awayTeam || data.preMatchInsights?.awayTeam || awayTeam;
            contentEl.innerHTML = this.renderPreMatchInsightsUI(data, normalizedHomeTeam, normalizedAwayTeam);

        } catch (error) {
            console.error('[Router] Failed to load pre-match insights:', error);
            contentEl.innerHTML = `
                <div class="pre-match-insights-error">
                    <span class="error-icon">⚠️</span>
                    <h4 class="error-title">Unable to Load Insights</h4>
                    <p class="error-message">${this.escapeHtml(error.message || 'Please try again later')}</p>
                    <button class="retry-btn" onclick="window.router.loadPreMatchInsights('${this.escapeHtml(homeTeam)}', '${this.escapeHtml(awayTeam)}')">
                        Retry
                    </button>
                </div>
            `;
        }
    }

    /**
     * Render Pre-Match Insights UI
     */
    renderPreMatchInsightsUI(data, homeTeam, awayTeam) {
        const preMatch = data.preMatchInsights || {};
        const h2h = data.h2hInsights || {};

        // Extract data with null-safe access
        const formComparison = preMatch.formComparison || {};
        const streakIndicators = preMatch.streakIndicators || [];
        const restAnalysis = preMatch.restAnalysis || {};
        const goalThreatMeter = preMatch.goalThreatMeter || {};
        const keyInsights = preMatch.keyInsights || [];

        const historicalRecord = h2h.historicalRecord || {};
        const recentMeetings = h2h.recentMeetings || [];
        const goalStats = h2h.goalStats || {};
        const commonResults = h2h.commonResults || {};
        const venueAdvantage = h2h.venueAdvantage || {};

        return `
            <!-- SECTION 1: Metrics Comparison Table -->
            ${this.renderMetricsComparisonTable(homeTeam, awayTeam, formComparison, streakIndicators, restAnalysis, goalThreatMeter)}

            <!-- SECTION 2: Form Comparison Bar -->
            ${this.renderFormComparisonBar(homeTeam, awayTeam, formComparison)}

            <!-- SECTION 3: Head-to-Head Summary -->
            ${this.renderH2HSummary(homeTeam, awayTeam, historicalRecord)}

            <!-- SECTION 4: Last 5 Meetings Table -->
            ${this.renderRecentMeetingsTable(recentMeetings)}

            <!-- SECTION 5: Advanced Match Stats Cards -->
            ${this.renderAdvancedStatsCards(goalStats, commonResults, venueAdvantage, homeTeam, awayTeam)}

            <!-- Key Insights -->
            ${this.renderKeyInsights(keyInsights)}
        `;
    }

    /**
     * SECTION 1: Metrics Comparison Table
     */
    renderMetricsComparisonTable(homeTeam, awayTeam, form, streaks, rest, goalThreat) {
        // Find streaks for each team
        const homeStreaks = streaks.filter(s => s.isHomeTeam);
        const awayStreaks = streaks.filter(s => !s.isHomeTeam);

        const getStreakDisplay = (teamStreaks) => {
            if (!teamStreaks || teamStreaks.length === 0) return '-';
            const streak = teamStreaks[0];
            const emoji = streak.emoji || '';
            const type = streak.streakType || '';
            const len = streak.streakLength || 0;

            let badgeClass = 'unbeaten-streak';
            if (type === 'WIN') badgeClass = 'win-streak';
            else if (type === 'LOSS' || type === 'WINLESS') badgeClass = 'loss-streak';

            return `<span class="streak-badge ${badgeClass}">${emoji} ${len} ${type}</span>`;
        };

        // Calculate form points per game
        const homeFormPPG = form.homeMaxPoints > 0
            ? ((form.homeFormPoints || 0) / (form.homeMaxPoints / 3)).toFixed(1)
            : (form.homeFormRating || 0).toFixed(1);
        const awayFormPPG = form.awayMaxPoints > 0
            ? ((form.awayFormPoints || 0) / (form.awayMaxPoints / 3)).toFixed(1)
            : (form.awayFormRating || 0).toFixed(1);

        return `
            <div class="metrics-comparison">
                <div class="metrics-comparison-title">
                    <span class="title-icon">📈</span>
                    Team Metrics Comparison
                </div>
                <table class="metrics-table">
                    <thead>
                        <tr>
                            <th class="metric-col">Metric</th>
                            <th class="home-col">${this.escapeHtml(homeTeam)}</th>
                            <th class="away-col">${this.escapeHtml(awayTeam)}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td class="metric-name"><span class="metric-icon">📊</span> Form (PPG)</td>
                            <td class="home-value">${homeFormPPG}</td>
                            <td class="away-value">${awayFormPPG}</td>
                        </tr>
                        <tr>
                            <td class="metric-name"><span class="metric-icon">🔥</span> Current Streak</td>
                            <td class="home-value">${getStreakDisplay(homeStreaks)}</td>
                            <td class="away-value">${getStreakDisplay(awayStreaks)}</td>
                        </tr>
                        <tr>
                            <td class="metric-name"><span class="metric-icon">⏱️</span> Rest Days</td>
                            <td class="home-value">
                                <span class="value-highlight ${(rest.homeTeamRestDays || 0) < 3 ? 'negative' : 'positive'}">
                                    ${rest.homeTeamRestDays || '-'} days
                                </span>
                            </td>
                            <td class="away-value">
                                <span class="value-highlight ${(rest.awayTeamRestDays || 0) < 3 ? 'negative' : 'positive'}">
                                    ${rest.awayTeamRestDays || '-'} days
                                </span>
                            </td>
                        </tr>
                        <tr>
                            <td class="metric-name"><span class="metric-icon">⚡</span> Goal Threat Index</td>
                            <td class="home-value">${(goalThreat.homeThreatRating || 0).toFixed(0)}%</td>
                            <td class="away-value">${(goalThreat.awayThreatRating || 0).toFixed(0)}%</td>
                        </tr>
                        <tr>
                            <td class="metric-name"><span class="metric-icon">⚽</span> Goals Scored (Season Avg)</td>
                            <td class="home-value">${(goalThreat.homeTeamAvgScored || 0).toFixed(2)}</td>
                            <td class="away-value">${(goalThreat.awayTeamAvgScored || 0).toFixed(2)}</td>
                        </tr>
                        <tr>
                            <td class="metric-name"><span class="metric-icon">🛡️</span> Goals Conceded (Season Avg)</td>
                            <td class="home-value">${(goalThreat.homeTeamAvgConceded || 0).toFixed(2)}</td>
                            <td class="away-value">${(goalThreat.awayTeamAvgConceded || 0).toFixed(2)}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        `;
    }

    /**
     * SECTION 2: Form Comparison Bar
     */
    renderFormComparisonBar(homeTeam, awayTeam, form) {
        const homeFormPoints = form.homeFormPoints || 0;
        const awayFormPoints = form.awayFormPoints || 0;
        const totalPoints = homeFormPoints + awayFormPoints;

        const homeWidth = totalPoints > 0 ? Math.round((homeFormPoints / totalPoints) * 100) : 50;
        const awayWidth = 100 - homeWidth;

        const homeFormString = form.homeFormString || '';
        const awayFormString = form.awayFormString || '';

        const renderFormBadges = (formStr) => {
            if (!formStr) return '';
            return formStr.split('').map(r => `<span class="form-result-badge ${r}">${r}</span>`).join('');
        };

        return `
            <div class="form-comparison-section">
                <div class="form-comparison-title">
                    <span class="title-icon">📉</span>
                    Form Comparison (Last 5 Matches)
                </div>

                <div class="form-comparison-bar-container">
                    <div class="form-comparison-labels">
                        <span class="team-label home">${this.escapeHtml(homeTeam)}</span>
                        <span class="team-label away">${this.escapeHtml(awayTeam)}</span>
                    </div>
                    <div class="form-dual-bar">
                        <div class="form-bar home" style="width: ${homeWidth}%;">${homeFormPoints} pts</div>
                        <div class="form-bar away" style="width: ${awayWidth}%;">${awayFormPoints} pts</div>
                    </div>
                </div>

                <div class="form-comparison-details">
                    <div class="form-detail-item home">
                        <div class="detail-value">${homeFormPoints}/${form.homeMaxPoints || 15}</div>
                        <div class="detail-label">Points</div>
                        <div class="form-string-container">
                            ${renderFormBadges(homeFormString)}
                        </div>
                    </div>
                    <div class="form-detail-item">
                        <div class="detail-value">${form.formAdvantage || 'Even'}</div>
                        <div class="detail-label">Advantage</div>
                    </div>
                    <div class="form-detail-item away">
                        <div class="detail-value">${awayFormPoints}/${form.awayMaxPoints || 15}</div>
                        <div class="detail-label">Points</div>
                        <div class="form-string-container">
                            ${renderFormBadges(awayFormString)}
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * SECTION 3: Head-to-Head Summary
     */
    renderH2HSummary(homeTeam, awayTeam, record) {
        const totalMatches = record.totalMatches || 0;
        const homeWins = record.homeTeamWins || 0;
        const draws = record.draws || 0;
        const awayWins = record.awayTeamWins || 0;
        const summary = record.summary || `${homeWins}-${draws}-${awayWins}`;

        if (totalMatches === 0) {
            return `
                <div class="h2h-summary-section">
                    <div class="h2h-summary-title">
                        <span class="title-icon">⚔️</span>
                        Head-to-Head Record
                    </div>
                    <div class="no-data-message">
                        <div class="no-data-icon">📭</div>
                        <p class="no-data-text">No previous meetings found between these teams</p>
                    </div>
                </div>
            `;
        }

        return `
            <div class="h2h-summary-section">
                <div class="h2h-summary-title">
                    <span class="title-icon">⚔️</span>
                    Head-to-Head Record
                </div>
                <div class="h2h-stats-grid">
                    <div class="h2h-stat-card total">
                        <div class="h2h-stat-value">${totalMatches}</div>
                        <div class="h2h-stat-label">Total Meetings</div>
                    </div>
                    <div class="h2h-stat-card home-wins">
                        <div class="h2h-stat-value">${homeWins}</div>
                        <div class="h2h-stat-label">${this.escapeHtml(homeTeam)} Wins</div>
                    </div>
                    <div class="h2h-stat-card draws">
                        <div class="h2h-stat-value">${draws}</div>
                        <div class="h2h-stat-label">Draws</div>
                    </div>
                    <div class="h2h-stat-card away-wins">
                        <div class="h2h-stat-value">${awayWins}</div>
                        <div class="h2h-stat-label">${this.escapeHtml(awayTeam)} Wins</div>
                    </div>
                </div>
                <div class="h2h-summary-text">
                    ${this.escapeHtml(summary)}
                </div>
            </div>
        `;
    }

    /**
     * SECTION 4: Last 5 Meetings Table
     */
    renderRecentMeetingsTable(meetings) {
        if (!meetings || meetings.length === 0) {
            return `
                <div class="recent-meetings-section">
                    <div class="recent-meetings-title">
                        <span class="title-icon">📅</span>
                        Last 5 Meetings
                    </div>
                    <div class="no-data-message">
                        <div class="no-data-icon">📭</div>
                        <p class="no-data-text">No recent meeting data available</p>
                    </div>
                </div>
            `;
        }

        const rows = meetings.slice(0, 5).map(match => {
            const isHomeWin = match.result === 'H' || match.winner === match.homeTeamInMatch;
            const isAwayWin = match.result === 'A' || match.winner === match.awayTeamInMatch;

            return `
                <tr>
                    <td class="date-col">${this.escapeHtml(match.date || '-')}</td>
                    <td class="team-col ${isHomeWin ? 'winner' : ''}">${this.escapeHtml(match.homeTeamInMatch || '-')}</td>
                    <td class="score-col">
                        <span class="score-badge">${this.escapeHtml(match.score || `${match.homeGoals || 0}-${match.awayGoals || 0}`)}</span>
                    </td>
                    <td class="team-col ${isAwayWin ? 'winner' : ''}">${this.escapeHtml(match.awayTeamInMatch || '-')}</td>
                </tr>
            `;
        }).join('');

        return `
            <div class="recent-meetings-section">
                <div class="recent-meetings-title">
                    <span class="title-icon">📅</span>
                    Last 5 Meetings
                </div>
                <div class="recent-meetings-table-wrapper">
                    <table class="recent-meetings-table">
                        <thead>
                            <tr>
                                <th>Date</th>
                                <th>Home</th>
                                <th>Score</th>
                                <th>Away</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rows}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    }

    /**
     * SECTION 5: Advanced Match Stats Cards
     */
    renderAdvancedStatsCards(goalStats, commonResults, venueAdvantage, homeTeam, awayTeam) {
        const avgGoals = (goalStats.avgTotalGoals || 0).toFixed(2);
        const commonScore = commonResults.mostCommonResult || '-';
        const homeVenueWinPct = (venueAdvantage.homeTeamHomeWinPercentage || 0).toFixed(0);
        const awayVenueWinPct = (venueAdvantage.awayTeamHomeWinPercentage || 0).toFixed(0);

        return `
            <div class="advanced-stats-section">
                <div class="advanced-stats-title">
                    <span class="title-icon">🎯</span>
                    Advanced Match Stats
                </div>
                <div class="advanced-stats-grid">
                    <div class="advanced-stat-card">
                        <div class="advanced-stat-icon">⚽</div>
                        <div class="advanced-stat-value">${avgGoals}</div>
                        <div class="advanced-stat-label">Avg Goals</div>
                        <div class="advanced-stat-sublabel">Per H2H Match</div>
                    </div>
                    <div class="advanced-stat-card">
                        <div class="advanced-stat-icon">🎲</div>
                        <div class="advanced-stat-value">${this.escapeHtml(commonScore)}</div>
                        <div class="advanced-stat-label">Common Score</div>
                        <div class="advanced-stat-sublabel">${commonResults.mostCommonResultCount || 0}x occurred</div>
                    </div>
                    <div class="advanced-stat-card">
                        <div class="advanced-stat-icon">🏠</div>
                        <div class="advanced-stat-value">${homeVenueWinPct}%</div>
                        <div class="advanced-stat-label">${this.truncateTeamName(homeTeam)}</div>
                        <div class="advanced-stat-sublabel">Home Win %</div>
                    </div>
                    <div class="advanced-stat-card">
                        <div class="advanced-stat-icon">✈️</div>
                        <div class="advanced-stat-value">${awayVenueWinPct}%</div>
                        <div class="advanced-stat-label">${this.truncateTeamName(awayTeam)}</div>
                        <div class="advanced-stat-sublabel">Home Win %</div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render Key Insights section
     */
    renderKeyInsights(insights) {
        if (!insights || insights.length === 0) {
            return '';
        }

        const insightItems = insights.map(insight => `
            <div class="key-insight-item">
                <span class="insight-icon">💡</span>
                <span>${this.escapeHtml(insight)}</span>
            </div>
        `).join('');

        return `
            <div class="key-insights-section">
                <div class="key-insights-title">
                    <span class="title-icon">💡</span>
                    Key Insights
                </div>
                <div class="key-insights-list">
                    ${insightItems}
                </div>
            </div>
        `;
    }

    /**
     * Truncate team name for display
     */
    truncateTeamName(name, maxLen = 12) {
        if (!name) return '-';
        return name.length > maxLen ? name.substring(0, maxLen - 1) + '…' : name;
    }

    /**
     * Show error message
     */
    showError(container, message) {
        container.style.display = 'block';
        container.innerHTML = `
            <div style="background: rgba(248, 113, 113, 0.1); border: 1px solid #f87171; border-radius: 0.5rem; padding: 1rem; color: #f87171;">
                <strong>⚠️ Error:</strong> ${message}
            </div>
        `;
    }

    /**
     * Render Historical view
     */
    renderHistorical() {
        // Set up container for historical explorer
        this.mainContent.innerHTML = `
            <div id="historicalContent"></div>
        `;

        // Initialize historical explorer
        if (window.historicalExplorer) {
            window.historicalExplorer.init();
        }
    }

    /**
     * Render Insights view with real data
     */
    renderInsights() {
        this.mainContent.innerHTML = `
            <div class="content-header">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem;">
                    <div>
                        <h2 class="page-title">Insights</h2>
                        <p class="page-description">Trending teams, hot streaks, and match analysis</p>
                    </div>
                    <div class="season-selector-container" style="display: flex; align-items: center; gap: 0.75rem;">
                        <label for="insightsSeasonSelect" style="color: var(--text-secondary); font-weight: 500; font-size: 0.875rem;">Season:</label>
                        <select id="insightsSeasonSelect" class="season-select" style="padding: 0.5rem 1rem; border-radius: 0.5rem; border: 1px solid var(--border-color); background: var(--bg-secondary); color: var(--text-primary); font-size: 0.875rem; cursor: pointer; min-width: 120px;">
                            <option value="">Loading...</option>
                        </select>
                    </div>
                </div>
                <div id="currentSeasonLabel" class="season-label" style="margin-top: 0.75rem; padding: 0.5rem 1rem; background: var(--bg-tertiary); border-radius: 0.5rem; display: inline-flex; align-items: center; gap: 0.5rem; font-size: 0.875rem; color: var(--text-secondary);">
                    <span>📅</span>
                    <span id="seasonLabelText">Loading season data...</span>
                </div>
            </div>
            <div id="insightsContent">
                <div class="insights-loading" style="text-align: center; padding: 3rem;">
                    <div class="loading-spinner" style="width: 40px; height: 40px; border: 3px solid var(--border-color); border-top-color: var(--accent-blue); border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 1rem;"></div>
                    <p style="color: var(--text-muted);">Loading insights...</p>
                </div>
            </div>
            <style>
                @keyframes spin { to { transform: rotate(360deg); } }
                .insights-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1.5rem; }
                .insight-card { background: var(--bg-secondary); border-radius: 0.75rem; padding: 1.5rem; border: 1px solid var(--border-color); }
                .insight-card-header { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 1px solid var(--border-color); }
                .insight-card-icon { font-size: 1.5rem; }
                .insight-card-title { font-size: 1.125rem; font-weight: 600; color: var(--text-primary); }
                .insight-item { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem 0; border-bottom: 1px solid var(--border-color); color: var(--text-primary); }
                .insight-item:last-child { border-bottom: none; }
                .insight-rank { width: 24px; height: 24px; border-radius: 50%; background: var(--bg-tertiary); display: flex; align-items: center; justify-content: center; font-size: 0.75rem; font-weight: 600; }
                .insight-team { flex: 1; font-weight: 500; }
                .insight-value { font-weight: 600; color: var(--accent-green); }
                .insight-form { display: flex; gap: 2px; }
                .form-badge { width: 18px; height: 18px; border-radius: 3px; display: flex; align-items: center; justify-content: center; font-size: 0.625rem; font-weight: bold; color: white; }
                .form-W { background: #22c55e; }
                .form-D { background: #eab308; }
                .form-L { background: #ef4444; }
                .empty-state { text-align: center; padding: 2rem; color: var(--text-muted); }
                .empty-state-icon { font-size: 3rem; margin-bottom: 1rem; opacity: 0.5; }
                .season-select:focus { outline: none; border-color: var(--accent-blue); box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2); }
                .season-select:hover { border-color: var(--accent-blue); }
                .insights-footer { text-align: center; margin-top: 1.5rem; padding: 1rem; background: var(--bg-tertiary); border-radius: 0.5rem; color: var(--text-muted); font-size: 0.875rem; }
                .no-data-state { text-align: center; padding: 4rem 2rem; background: var(--bg-secondary); border-radius: 0.75rem; border: 1px solid var(--border-color); }
                .no-data-state-icon { font-size: 4rem; margin-bottom: 1.5rem; opacity: 0.5; }
                .no-data-state h3 { color: var(--text-primary); margin-bottom: 0.5rem; }
                .no-data-state p { color: var(--text-muted); }
            </style>
        `;
        this.loadInsightsSeasons();
    }

    /**
     * Load available seasons and initialize the season selector
     */
    async loadInsightsSeasons() {
        const seasonSelect = document.getElementById('insightsSeasonSelect');
        const seasonLabelText = document.getElementById('seasonLabelText');

        try {
            let seasonsData;
            if (window.api && typeof window.api.getInsightsSeasons === 'function') {
                seasonsData = await window.api.getInsightsSeasons();
            } else if (window.api && typeof window.api.get === 'function') {
                seasonsData = await window.api.get('/insights/seasons');
            } else {
                const fetchResponse = await fetch('/api/insights/seasons');
                if (!fetchResponse.ok) {
                    throw new Error(`HTTP error! status: ${fetchResponse.status}`);
                }
                seasonsData = await fetchResponse.json();
            }

            const seasons = seasonsData.seasons || [];
            const currentSeason = seasonsData.currentSeason || (seasons.length > 0 ? seasons[0] : '');

            if (seasons.length === 0) {
                seasonSelect.innerHTML = '<option value="">No seasons available</option>';
                seasonLabelText.textContent = 'No season data available';
                this.showNoDataState();
                return;
            }

            // Populate season dropdown
            seasonSelect.innerHTML = seasons.map(season =>
                `<option value="${this.escapeHtml(season)}" ${season === currentSeason ? 'selected' : ''}>${this.escapeHtml(season)}</option>`
            ).join('');

            // Update season label
            seasonLabelText.textContent = `Showing data for ${currentSeason} season`;

            // Store current season for reference
            this.currentInsightsSeason = currentSeason;

            // Add change event listener
            seasonSelect.addEventListener('change', (e) => {
                const selectedSeason = e.target.value;
                this.currentInsightsSeason = selectedSeason;
                seasonLabelText.textContent = `Showing data for ${selectedSeason} season`;
                this.loadInsightsData(selectedSeason);
            });

            // Load data for current season
            this.loadInsightsData(currentSeason);

        } catch (error) {
            console.error('[Router] Failed to load seasons:', error);
            seasonSelect.innerHTML = '<option value="">Error loading seasons</option>';
            seasonLabelText.textContent = 'Failed to load season data';
            this.loadInsightsData(); // Try loading without season filter
        }
    }

    /**
     * Show empty state when no completed matches exist
     */
    showNoDataState() {
        const container = document.getElementById('insightsContent');
        if (!container) return;

        container.innerHTML = `
            <div class="no-data-state">
                <div class="no-data-state-icon">📊</div>
                <h3>No Completed Matches</h3>
                <p>There are no completed matches in the database yet.</p>
                <p style="margin-top: 0.5rem;">Insights will be available once match data is imported.</p>
            </div>
        `;
    }

    /**
     * Load insights data from API
     * @param {string} season - Optional season filter (e.g., "2024-25")
     */
    async loadInsightsData(season = null) {
        const container = document.getElementById('insightsContent');
        if (!container) {
            console.error('[Router] insightsContent container not found!');
            return;
        }

        // Show loading state
        container.innerHTML = `
            <div class="insights-loading" style="text-align: center; padding: 3rem;">
                <div class="loading-spinner" style="width: 40px; height: 40px; border: 3px solid var(--border-color); border-top-color: var(--accent-blue); border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 1rem;"></div>
                <p style="color: var(--text-muted);">Loading insights${season ? ` for ${season}` : ''}...</p>
            </div>
        `;

        try {
            console.log('[Router] Fetching insights data...', season ? `for season: ${season}` : '');
            console.log('[Router] window.api available:', !!window.api);

            let response;
            const seasonParam = season ? `?season=${encodeURIComponent(season)}` : '';

            if (window.api && typeof window.api.getTrendingInsights === 'function') {
                response = await window.api.getTrendingInsights(season);
            } else if (window.api && typeof window.api.get === 'function') {
                response = await window.api.get(`/insights/trending${seasonParam}`);
            } else {
                console.log('[Router] Falling back to native fetch...');
                const fetchResponse = await fetch(`/api/insights/trending${seasonParam}`);
                if (!fetchResponse.ok) {
                    throw new Error(`HTTP error! status: ${fetchResponse.status}`);
                }
                response = await fetchResponse.json();
            }

            console.log('[Router] Insights response received:', response ? 'yes' : 'no');
            console.log('[Router] Response keys:', response ? Object.keys(response) : 'null');

            if (!response || typeof response !== 'object') {
                throw new Error('Invalid response from insights API');
            }

            // Check if there's any data
            const hasData = (response.hotTeams?.length > 0) ||
                           (response.coldTeams?.length > 0) ||
                           (response.topScorers?.length > 0) ||
                           (response.defensiveWalls?.length > 0) ||
                           (response.totalTeamsAnalyzed > 0);

            if (!hasData) {
                this.showNoDataState();
                return;
            }

            container.innerHTML = this.buildInsightsHTML(response);
            console.log('[Router] Insights loaded and rendered successfully');

        } catch (error) {
            console.error('[Router] Failed to load insights:', error);
            container.innerHTML = `
                <div class="card" style="max-width: 600px; margin: 2rem auto;">
                    <div class="card-body text-center" style="padding: 2rem;">
                        <div class="empty-state-icon">⚠️</div>
                        <h3 style="margin-bottom: 0.5rem;">Unable to Load Insights</h3>
                        <p style="color: var(--text-muted); margin-bottom: 1rem;">${error.message || 'Please try again later'}</p>
                        <button class="btn btn-primary" onclick="window.router.loadInsightsData('${season || ''}')">Retry</button>
                    </div>
                </div>
            `;
        }
    }

    /**
     * Build insights HTML from data
     */
    buildInsightsHTML(data) {
        const renderFormBadges = (form) => {
            if (!form) return '';
            return form.slice(-5).split('').map(r => `<span class="form-badge form-${r}">${r}</span>`).join('');
        };

        // Format season for display (e.g., "2024-25" -> "2024/25")
        const season = data.season || this.currentInsightsSeason || '';
        const seasonDisplay = season.replace('-', '/');

        const hotTeamsHTML = (data.hotTeams || []).length > 0 ?
            data.hotTeams.map((team, i) => `
                <div class="insight-item">
                    <span class="insight-rank">${i + 1}</span>
                    <span class="insight-team">${this.escapeHtml(team.teamName)}</span>
                    <span class="insight-value">🔥 ${team.winStreak}W</span>
                    <span class="insight-form">${renderFormBadges(team.recentForm)}</span>
                </div>
            `).join('') :
            '<div class="empty-state"><div class="empty-state-icon">🔥</div><p>No teams on winning streaks this season</p></div>';

        const coldTeamsHTML = (data.coldTeams || []).length > 0 ?
            data.coldTeams.slice(0, 5).map((team, i) => `
                <div class="insight-item">
                    <span class="insight-rank">${i + 1}</span>
                    <span class="insight-team">${this.escapeHtml(team.teamName)}</span>
                    <span class="insight-value" style="color: #60a5fa;">❄️ ${team.matchesWithoutWin} games</span>
                    <span class="insight-form">${renderFormBadges(team.recentForm)}</span>
                </div>
            `).join('') :
            '<div class="empty-state"><div class="empty-state-icon">❄️</div><p>No teams on losing streaks this season</p></div>';

        const topScorersHTML = (data.topScorers || []).length > 0 ?
            data.topScorers.slice(0, 5).map((team, i) => `
                <div class="insight-item">
                    <span class="insight-rank">${i + 1}</span>
                    <span class="insight-team">${this.escapeHtml(team.teamName)}</span>
                    <span class="insight-value">⚽ ${team.goalsScored} goals</span>
                    <span style="color: var(--text-muted); font-size: 0.875rem;">${team.avgGoalsPerMatch?.toFixed(1) || '0'}/match</span>
                </div>
            `).join('') :
            '<div class="empty-state"><div class="empty-state-icon">⚽</div><p>No scoring data available this season</p></div>';

        const defensiveWallsHTML = (data.defensiveWalls || []).length > 0 ?
            data.defensiveWalls.slice(0, 5).map((team, i) => `
                <div class="insight-item">
                    <span class="insight-rank">${i + 1}</span>
                    <span class="insight-team">${this.escapeHtml(team.teamName)}</span>
                    <span class="insight-value">🧱 ${team.cleanSheets} CS</span>
                    <span style="color: var(--text-muted); font-size: 0.875rem;">${team.cleanSheetPercentage?.toFixed(0) || '0'}%</span>
                </div>
            `).join('') :
            '<div class="empty-state"><div class="empty-state-icon">🧱</div><p>No defensive data available this season</p></div>';

        const upsetAlertsHTML = (data.upsetAlerts || []).length > 0 ?
            data.upsetAlerts.slice(0, 5).map(alert => `
                <div class="insight-item" style="flex-direction: column; align-items: flex-start;">
                    <div style="display: flex; align-items: center; gap: 0.5rem; width: 100%;">
                        <span style="font-weight: 600; color: var(--text-primary);">${this.escapeHtml(alert.homeTeam)} vs ${this.escapeHtml(alert.awayTeam)}</span>
                        <span class="badge" style="background: ${alert.confidence === 'HIGH' ? '#22c55e' : '#eab308'}; font-size: 0.625rem; color: white; padding: 0.125rem 0.375rem; border-radius: 0.25rem;">${alert.confidence}</span>
                    </div>
                    <div style="font-size: 0.875rem; color: var(--text-secondary); margin-top: 0.25rem;">
                        <span style="color: var(--accent-green); font-weight: 600;">Away win: ${alert.awayWinProbability}%</span>
                    </div>
                    ${alert.reason ? `<div style="font-size: 0.8rem; color: var(--text-muted); margin-top: 0.25rem; line-height: 1.4;">${this.escapeHtml(alert.reason)}</div>` : ''}
                </div>
            `).join('') :
            '<div class="empty-state"><div class="empty-state-icon">🎯</div><p>No upset alerts at this time</p></div>';

        return `
            <div class="insights-grid">
                <div class="insight-card">
                    <div class="insight-card-header">
                        <span class="insight-card-icon">🔥</span>
                        <span class="insight-card-title">Hot Teams (This Season)</span>
                        <span class="badge badge-success" style="margin-left: auto;">Winning Streaks</span>
                    </div>
                    <div class="insight-card-body">${hotTeamsHTML}</div>
                </div>

                <div class="insight-card">
                    <div class="insight-card-header">
                        <span class="insight-card-icon">❄️</span>
                        <span class="insight-card-title">Cold Teams (This Season)</span>
                        <span class="badge badge-info" style="margin-left: auto;">Struggling</span>
                    </div>
                    <div class="insight-card-body">${coldTeamsHTML}</div>
                </div>

                <div class="insight-card">
                    <div class="insight-card-header">
                        <span class="insight-card-icon">⚽</span>
                        <span class="insight-card-title">Top Scorers – ${seasonDisplay || 'Season'}</span>
                        <span class="badge badge-warning" style="margin-left: auto;">Season Stats</span>
                    </div>
                    <div class="insight-card-body">${topScorersHTML}</div>
                </div>

                <div class="insight-card">
                    <div class="insight-card-header">
                        <span class="insight-card-icon">🧱</span>
                        <span class="insight-card-title">Defensive Wall – Season Stats</span>
                        <span class="badge badge-info" style="margin-left: auto;">Clean Sheets</span>
                    </div>
                    <div class="insight-card-body">${defensiveWallsHTML}</div>
                </div>

                <div class="insight-card" style="grid-column: span 2;">
                    <div class="insight-card-header">
                        <span class="insight-card-icon">🎯</span>
                        <span class="insight-card-title">Upset Alerts</span>
                        <span class="badge badge-danger" style="margin-left: auto;">Away Favorites</span>
                    </div>
                    <div class="insight-card-body">${upsetAlertsHTML}</div>
                </div>
            </div>

            <div class="insights-footer">
                <div style="display: flex; align-items: center; justify-content: center; gap: 1rem; flex-wrap: wrap;">
                    <span>📊 Analyzing ${data.totalTeamsAnalyzed || 0} teams</span>
                    ${data.generatedAt ? `<span>⏰ Updated: ${data.generatedAt}</span>` : ''}
                </div>
                <div style="margin-top: 0.5rem; font-style: italic; color: var(--text-muted);">
                    ℹ️ Data based on current season only. Streaks and rankings reset each season.
                </div>
            </div>
        `;
    }

    /**
     * Render Teams view with real data
     */
    renderTeams() {
        this.mainContent.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">Teams</h2>
                <p class="page-description">All teams and their statistics</p>
            </div>
            <div id="teamsContent">
                <div class="teams-loading" style="text-align: center; padding: 3rem;">
                    <div class="loading-spinner" style="width: 40px; height: 40px; border: 3px solid var(--border-color); border-top-color: var(--accent-blue); border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 1rem;"></div>
                    <p style="color: var(--text-muted);">Loading teams...</p>
                </div>
            </div>
            <style>
                .teams-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; }
                .team-card { background: var(--bg-secondary); border-radius: 0.75rem; padding: 1.25rem; border: 1px solid var(--border-color); transition: transform 0.2s, box-shadow 0.2s; cursor: pointer; position: relative; }
                .team-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); }
                .team-card-header { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1rem; }
                .team-logo { width: 40px; height: 40px; border-radius: 50%; background: var(--bg-tertiary); display: flex; align-items: center; justify-content: center; font-size: 1.25rem; }
                .team-name { font-size: 1.125rem; font-weight: 600; color: var(--text-primary); }
                .team-name-container { display: flex; flex-direction: column; gap: 0.25rem; }
                .team-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.5rem; text-align: center; }
                .team-stat { padding: 0.5rem; background: var(--bg-tertiary); border-radius: 0.375rem; }
                .team-stat-value { font-size: 1.25rem; font-weight: 700; }
                .team-stat-label { font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; }
                .teams-filter-bar { display: flex; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; align-items: center; }
                .teams-filter-bar input { flex: 1; min-width: 200px; padding: 0.75rem 1rem; border-radius: 0.5rem; border: 1px solid var(--border-color); background: var(--bg-secondary); color: var(--text-primary); }
                .teams-filter-bar input:focus { outline: none; border-color: var(--accent-blue); }
                .teams-filter-bar select { padding: 0.75rem 1rem; border-radius: 0.5rem; border: 1px solid var(--border-color); background: var(--bg-secondary); color: var(--text-primary); min-width: 150px; cursor: pointer; }
                .teams-filter-bar select:focus { outline: none; border-color: var(--accent-blue); }
                .teams-count { color: var(--text-muted); font-size: 0.875rem; }
                .season-badge { background: var(--accent-blue); color: white; padding: 0.25rem 0.75rem; border-radius: 1rem; font-size: 0.75rem; font-weight: 600; }

                /* Promoted team styling - Green */
                .team-card--promoted {
                    border-left: 4px solid #22c55e;
                    background: linear-gradient(to right, rgba(34, 197, 94, 0.08), var(--bg-secondary));
                }
                .team-card--promoted:hover { box-shadow: 0 4px 12px rgba(34, 197, 94, 0.2); }

                /* Relegation/Relegated styling - Red */
                .team-card--relegation, .team-card--relegated {
                    border-left: 4px solid #ef4444;
                    background: linear-gradient(to right, rgba(239, 68, 68, 0.08), var(--bg-secondary));
                }
                .team-card--relegation:hover, .team-card--relegated:hover { box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2); }

                /* Champions League styling - Blue */
                .team-card--champions-league, .team-card--qualified-ucl {
                    border-left: 4px solid #3b82f6;
                    background: linear-gradient(to right, rgba(59, 130, 246, 0.08), var(--bg-secondary));
                }
                .team-card--champions-league:hover, .team-card--qualified-ucl:hover { box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2); }

                /* Europa League styling - Orange */
                .team-card--europa-league, .team-card--qualified-uel {
                    border-left: 4px solid #f97316;
                    background: linear-gradient(to right, rgba(249, 115, 22, 0.08), var(--bg-secondary));
                }
                .team-card--europa-league:hover, .team-card--qualified-uel:hover { box-shadow: 0 4px 12px rgba(249, 115, 22, 0.2); }

                /* Conference League styling - Teal */
                .team-card--conference-league, .team-card--qualified-uecl {
                    border-left: 4px solid #14b8a6;
                    background: linear-gradient(to right, rgba(20, 184, 166, 0.08), var(--bg-secondary));
                }
                .team-card--conference-league:hover, .team-card--qualified-uecl:hover { box-shadow: 0 4px 12px rgba(20, 184, 166, 0.2); }

                /* Status badges */
                .team-status-badge {
                    font-size: 0.65rem;
                    padding: 0.15rem 0.5rem;
                    border-radius: 0.25rem;
                    font-weight: 600;
                    display: inline-flex;
                    align-items: center;
                    gap: 0.25rem;
                    white-space: nowrap;
                }
                .team-status-badge.promoted { background: rgba(34, 197, 94, 0.15); color: #22c55e; }
                .team-status-badge.relegation, .team-status-badge.relegated { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
                .team-status-badge.champions-league, .team-status-badge.qualified-ucl { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
                .team-status-badge.europa-league, .team-status-badge.qualified-uel { background: rgba(249, 115, 22, 0.15); color: #f97316; }
                .team-status-badge.conference-league, .team-status-badge.qualified-uecl { background: rgba(20, 184, 166, 0.15); color: #14b8a6; }

                /* Position badge */
                .team-position-badge {
                    width: 28px;
                    height: 28px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 0.75rem;
                    font-weight: 700;
                    color: white;
                    flex-shrink: 0;
                }
                .team-position-badge.zone-champions { background: linear-gradient(135deg, #3b82f6, #1d4ed8); }
                .team-position-badge.zone-europa { background: linear-gradient(135deg, #f97316, #ea580c); }
                .team-position-badge.zone-conference { background: linear-gradient(135deg, #14b8a6, #0d9488); }
                .team-position-badge.zone-mid { background: var(--bg-tertiary); color: var(--text-secondary); }
                .team-position-badge.zone-relegation { background: linear-gradient(135deg, #ef4444, #dc2626); }

                /* Legend */
                .teams-legend {
                    display: flex;
                    gap: 1rem;
                    flex-wrap: wrap;
                    padding: 0.75rem 1rem;
                    background: var(--bg-tertiary);
                    border-radius: 0.5rem;
                    margin-bottom: 1rem;
                    font-size: 0.75rem;
                }
                .teams-legend-item {
                    display: flex;
                    align-items: center;
                    gap: 0.4rem;
                    color: var(--text-secondary);
                }
                .legend-indicator {
                    width: 12px;
                    height: 12px;
                    border-radius: 2px;
                }
                .legend-indicator.promoted { background: #22c55e; }
                .legend-indicator.relegation, .legend-indicator.relegated { background: #ef4444; }
                .legend-indicator.champions { background: #3b82f6; }
                .legend-indicator.europa { background: #f97316; }
                .legend-indicator.conference { background: #14b8a6; }
            </style>
        `;
        this._currentTeamsSeason = null; // Track current season
        this.loadSeasonsAndTeams();
    }

    /**
     * Load seasons first, then load teams
     */
    async loadSeasonsAndTeams() {
        try {
            // Fetch available seasons
            const seasonsResponse = await fetch('/api/teams/seasons');
            if (seasonsResponse.ok) {
                const seasonsData = await seasonsResponse.json();
                this._availableSeasons = seasonsData.seasons || [];

                // Default to the most recent season (first in the list)
                if (this._availableSeasons.length > 0) {
                    this._currentTeamsSeason = this._availableSeasons[0];
                }
            }
        } catch (error) {
            console.warn('[Router] Failed to load seasons, will show all teams:', error);
            this._availableSeasons = [];
        }

        // Now load teams
        this.loadTeamsData();
    }

    /**
     * Load teams data from API
     */
    async loadTeamsData(season = null) {
        const container = document.getElementById('teamsContent');
        if (!container) {
            console.error('[Router] teamsContent container not found!');
            return;
        }

        // Use provided season or current selection
        const selectedSeason = season || this._currentTeamsSeason;

        // Show loading state
        container.innerHTML = `
            <div class="teams-loading" style="text-align: center; padding: 3rem;">
                <div class="loading-spinner" style="width: 40px; height: 40px; border: 3px solid var(--border-color); border-top-color: var(--accent-blue); border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 1rem;"></div>
                <p style="color: var(--text-muted);">Loading teams${selectedSeason ? ' for ' + selectedSeason : ''}...</p>
            </div>
        `;

        try {
            console.log('[Router] Fetching teams data for season:', selectedSeason || 'all');

            // Build URL with season parameter
            let url = '/api/teams';
            if (selectedSeason) {
                url += `?season=${encodeURIComponent(selectedSeason)}`;
            }

            const fetchResponse = await fetch(url);
            if (!fetchResponse.ok) {
                throw new Error(`HTTP error! status: ${fetchResponse.status}`);
            }

            const response = await fetchResponse.json();

            // Handle both array (legacy) and object (new with season) response formats
            let teams;
            if (Array.isArray(response)) {
                teams = response;
            } else if (response.teams) {
                teams = response.teams;
            } else {
                teams = [];
            }

            console.log('[Router] Teams response received, count:', teams.length);

            if (teams.length === 0) {
                container.innerHTML = this.buildNoTeamsHTML(selectedSeason);
                return;
            }

            container.innerHTML = this.buildTeamsHTML(teams, selectedSeason);
            this.initTeamsSearch(teams);
            this.initSeasonSelector();
            console.log('[Router] Teams loaded and rendered successfully:', teams.length);

        } catch (error) {
            console.error('[Router] Failed to load teams:', error);
            container.innerHTML = `
                <div class="card" style="max-width: 600px; margin: 2rem auto;">
                    <div class="card-body text-center" style="padding: 2rem;">
                        <div style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;">🏆</div>
                        <h3 style="margin-bottom: 0.5rem;">Unable to Load Teams</h3>
                        <p style="color: var(--text-muted); margin-bottom: 1rem;">${error.message || 'Please try again later'}</p>
                        <button class="btn btn-primary" onclick="window.router.loadTeamsData()">Retry</button>
                    </div>
                </div>
            `;
        }
    }

    /**
     * Build teams HTML from data
     */
    buildTeamsHTML(teams) {
        console.log('[Router] Building teams HTML for', teams.length, 'teams');
        console.log('[Router] First team:', teams[0]);

        // Check if any teams have status info
        const hasStatusInfo = teams.some(t => typeof t === 'object' && t.status);

        const teamCards = teams.map(team => {
            // Handle both string (legacy) and object (new with logoUrl) formats
            const isObject = typeof team === 'object';
            const teamName = isObject ? (team.name || team.teamName || 'Unknown') : team;
            const logoUrl = isObject ? (team.logoUrl || team.logo) : null;
            const status = isObject ? team.status : null;
            const position = isObject ? team.position : null;
            const zone = isObject ? team.zone : null;

            // Debug first few teams
            if (teams.indexOf(team) < 3) {
                console.log('[Router] Team:', teamName, 'Status:', status, 'Zone:', zone, 'Position:', position);
            }

            // Determine status class and badge based on status
            const { statusClass, statusBadge } = this.getTeamStatusDisplay(status, zone);

            // Position badge
            const positionBadge = position ? `<span class="team-position-badge zone-${zone || 'mid'}">${position}</span>` : '';

            // Use TeamLogos utility if available, otherwise fallback
            let logoHtml;
            if (window.TeamLogos && window.TeamLogos.createLogoHTML) {
                logoHtml = window.TeamLogos.createLogoHTML(teamName, 'lg', logoUrl);
            } else {
                const logoSrc = logoUrl || 'https://cdn-icons-png.flaticon.com/512/861/861512.png';
                logoHtml = `
                    <div class="team-logo team-logo--lg">
                        <img src="${logoSrc}"
                             alt="${this.escapeHtml(teamName)} logo"
                             crossorigin="anonymous"
                             onerror="this.onerror=null; this.removeAttribute('crossorigin'); this.src='https://cdn-icons-png.flaticon.com/512/861/861512.png';"
                             loading="lazy">
                    </div>
                `;
            }

            return `
                <div class="team-card ${statusClass}" data-team="${this.escapeHtml(teamName)}" data-status="${status || ''}" data-zone="${zone || ''}" onclick="window.router.showTeamDetails('${this.escapeHtml(teamName)}')">
                    <div class="team-card-header">
                        ${positionBadge}
                        ${logoHtml}
                        <div class="team-name-container">
                            <div class="team-name">${this.escapeHtml(teamName)}</div>
                            ${statusBadge}
                        </div>
                    </div>
                    <div style="display: flex; justify-content: center;">
                        <button class="btn btn-outline btn-sm" onclick="event.stopPropagation(); window.router.showTeamDetails('${this.escapeHtml(teamName)}')">
                            View Stats
                        </button>
                    </div>
                </div>
            `;
        }).join('');

        // Build season selector options
        const seasonOptions = (this._availableSeasons || []).map(s => {
            const selected = s === this._currentTeamsSeason ? 'selected' : '';
            return `<option value="${this.escapeHtml(s)}" ${selected}>${this.escapeHtml(s)}</option>`;
        }).join('');

        const seasonSelector = this._availableSeasons && this._availableSeasons.length > 0 ? `
            <select id="teamsSeasonSelect" title="Filter teams by season">
                <option value="">All Seasons</option>
                ${seasonOptions}
            </select>
        ` : '';

        const seasonBadge = this._currentTeamsSeason ?
            `<span class="season-badge">📅 ${this.escapeHtml(this._currentTeamsSeason)}</span>` : '';

        // Build legend using helper function
        const legend = this.buildTeamsLegend(teams, hasStatusInfo);

        return `
            <div class="teams-filter-bar">
                <input type="text" id="teamsSearchInput" placeholder="🔍 Search teams..." />
                ${seasonSelector}
                <span class="teams-count">${teams.length} teams ${seasonBadge}</span>
            </div>
            ${legend}
            <div class="teams-grid" id="teamsGrid">
                ${teamCards}
            </div>
        `;
    }

    /**
     * Get status display (class and badge) for a team based on their status
     */
    getTeamStatusDisplay(status, zone) {
        const statusConfig = {
            // Current season statuses
            'promoted': {
                class: 'team-card--promoted',
                badge: '<span class="team-status-badge promoted" title="Newly promoted to the league">⬆️ Promoted</span>'
            },
            'relegation': {
                class: 'team-card--relegation',
                badge: '<span class="team-status-badge relegation" title="Currently in relegation zone">⬇️ Relegation Zone</span>'
            },
            'champions-league': {
                class: 'team-card--champions-league',
                badge: '<span class="team-status-badge champions-league" title="Champions League qualification position">🏆 UCL</span>'
            },
            'europa-league': {
                class: 'team-card--europa-league',
                badge: '<span class="team-status-badge europa-league" title="Europa League qualification position">🥈 UEL</span>'
            },
            'conference-league': {
                class: 'team-card--conference-league',
                badge: '<span class="team-status-badge conference-league" title="Conference League qualification position">🥉 UECL</span>'
            },
            // Previous season statuses (final)
            'relegated': {
                class: 'team-card--relegated',
                badge: '<span class="team-status-badge relegated" title="Relegated at end of season">⬇️ Relegated</span>'
            },
            'qualified-ucl': {
                class: 'team-card--qualified-ucl',
                badge: '<span class="team-status-badge qualified-ucl" title="Qualified for Champions League">🏆 UCL Qualified</span>'
            },
            'qualified-uel': {
                class: 'team-card--qualified-uel',
                badge: '<span class="team-status-badge qualified-uel" title="Qualified for Europa League">🥈 UEL Qualified</span>'
            },
            'qualified-uecl': {
                class: 'team-card--qualified-uecl',
                badge: '<span class="team-status-badge qualified-uecl" title="Qualified for Conference League">🥉 UECL Qualified</span>'
            },
            'mid-table': {
                class: '',
                badge: ''
            }
        };

        const config = statusConfig[status] || { class: '', badge: '' };
        return { statusClass: config.class, statusBadge: config.badge };
    }

    /**
     * Build legend HTML based on teams data
     */
    buildTeamsLegend(teams, hasStatusInfo) {
        if (!hasStatusInfo) return '';

        // Count teams by status
        const statusCounts = {};
        teams.forEach(t => {
            if (typeof t === 'object' && t.status) {
                statusCounts[t.status] = (statusCounts[t.status] || 0) + 1;
            }
        });

        // Build legend items
        let legendItems = '';

        // Champions League
        if (statusCounts['champions-league'] || statusCounts['qualified-ucl']) {
            const count = statusCounts['champions-league'] || statusCounts['qualified-ucl'];
            const label = statusCounts['qualified-ucl'] ? 'UCL Qualified' : 'Champions League';
            legendItems += `<div class="teams-legend-item"><span class="legend-indicator champions"></span><span>🏆 ${label} (${count})</span></div>`;
        }

        // Europa League
        if (statusCounts['europa-league'] || statusCounts['qualified-uel']) {
            const count = statusCounts['europa-league'] || statusCounts['qualified-uel'];
            const label = statusCounts['qualified-uel'] ? 'UEL Qualified' : 'Europa League';
            legendItems += `<div class="teams-legend-item"><span class="legend-indicator europa"></span><span>🥈 ${label} (${count})</span></div>`;
        }

        // Conference League
        if (statusCounts['conference-league'] || statusCounts['qualified-uecl']) {
            const count = statusCounts['conference-league'] || statusCounts['qualified-uecl'];
            const label = statusCounts['qualified-uecl'] ? 'UECL Qualified' : 'Conference League';
            legendItems += `<div class="teams-legend-item"><span class="legend-indicator conference"></span><span>🥉 ${label} (${count})</span></div>`;
        }

        // Promoted
        if (statusCounts['promoted']) {
            legendItems += `<div class="teams-legend-item"><span class="legend-indicator promoted"></span><span>⬆️ Promoted (${statusCounts['promoted']})</span></div>`;
        }

        // Relegation / Relegated
        if (statusCounts['relegation'] || statusCounts['relegated']) {
            const count = statusCounts['relegation'] || statusCounts['relegated'];
            const label = statusCounts['relegated'] ? 'Relegated' : 'Relegation Zone';
            legendItems += `<div class="teams-legend-item"><span class="legend-indicator relegation"></span><span>⬇️ ${label} (${count})</span></div>`;
        }

        if (!legendItems) return '';

        return `<div class="teams-legend">${legendItems}</div>`;
    }

    /**
     * Build HTML for no teams found state
     */
    buildNoTeamsHTML(season) {
        const seasonText = season ? ` for season ${season}` : '';
        return `
            <div class="card" style="max-width: 600px; margin: 2rem auto;">
                <div class="card-body text-center" style="padding: 2rem;">
                    <div style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;">🏟️</div>
                    <h3 style="margin-bottom: 0.5rem;">No Teams Found${seasonText}</h3>
                    <p style="color: var(--text-muted); margin-bottom: 1rem;">
                        ${season ? `There are no teams recorded for the ${season} season.` : 'No teams data available.'}
                    </p>
                    ${season ? `<button class="btn btn-outline" onclick="window.router._currentTeamsSeason = null; window.router.loadTeamsData();">Show All Teams</button>` : ''}
                </div>
            </div>
        `;
    }

    /**
     * Initialize teams search functionality
     */
    initTeamsSearch(teams) {
        const searchInput = document.getElementById('teamsSearchInput');
        const teamsGrid = document.getElementById('teamsGrid');
        if (!searchInput || !teamsGrid) return;

        searchInput.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase().trim();
            const cards = teamsGrid.querySelectorAll('.team-card');

            cards.forEach(card => {
                const teamName = card.dataset.team?.toLowerCase() || '';
                card.style.display = teamName.includes(query) ? '' : 'none';
            });
        });
    }

    /**
     * Initialize season selector event listener
     */
    initSeasonSelector() {
        const seasonSelect = document.getElementById('teamsSeasonSelect');
        if (!seasonSelect) return;

        seasonSelect.addEventListener('change', (e) => {
            const selectedSeason = e.target.value;
            this._currentTeamsSeason = selectedSeason || null;
            console.log('[Router] Season changed to:', selectedSeason || 'All');
            this.loadTeamsData(selectedSeason || null);
        });
    }

    /**
     * Show team details modal/panel
     */
    async showTeamDetails(teamName) {
        console.log('[Router] Loading details for team:', teamName);

        // Show team stats modal instead of navigating to predictions
        this.showTeamStatsModal(teamName);
    }

    /**
     * Show team statistics modal with comprehensive data
     */
    async showTeamStatsModal(teamName) {
        // Create modal overlay
        const existingModal = document.getElementById('teamStatsModal');
        if (existingModal) {
            existingModal.remove();
        }

        const modal = document.createElement('div');
        modal.id = 'teamStatsModal';
        modal.className = 'team-stats-modal-overlay';
        modal.innerHTML = `
            <div class="team-stats-modal team-stats-modal--large">
                <div class="team-stats-modal-header">
                    <h2 class="team-stats-modal-title">
                        <span class="team-logo-icon">⚽</span>
                        ${this.escapeHtml(teamName)} - Analytics Dashboard
                    </h2>
                    <button class="team-stats-modal-close" id="closeTeamStatsModal">&times;</button>
                </div>
                <div class="team-stats-modal-body" id="teamStatsModalBody">
                    <div class="team-stats-loading">
                        <div class="loading-spinner"></div>
                        <p>Loading team analytics...</p>
                    </div>
                </div>
            </div>
        `;

        // Add modal styles
        this.addTeamStatsModalStyles();
        this.addTeamAnalyticsStyles();

        document.body.appendChild(modal);

        // Close modal handlers
        const closeBtn = document.getElementById('closeTeamStatsModal');
        closeBtn.addEventListener('click', () => modal.remove());
        modal.addEventListener('click', (e) => {
            if (e.target === modal) modal.remove();
        });

        // Escape key to close
        const escHandler = (e) => {
            if (e.key === 'Escape') {
                modal.remove();
                document.removeEventListener('keydown', escHandler);
            }
        };
        document.addEventListener('keydown', escHandler);

        // Fetch both stats and analytics
        try {
            const [statsResponse, analyticsResponse] = await Promise.all([
                fetch(`/api/teams/${encodeURIComponent(teamName)}/stats`),
                fetch(`/api/teams/${encodeURIComponent(teamName)}/analytics`)
            ]);

            if (!statsResponse.ok) {
                throw new Error(`HTTP ${statsResponse.status}: ${statsResponse.statusText}`);
            }

            const stats = await statsResponse.json();
            const analytics = analyticsResponse.ok ? await analyticsResponse.json() : null;

            this.renderTeamStatsContent(stats, analytics, teamName);
        } catch (error) {
            console.error('[Router] Failed to load team stats:', error);
            const modalBody = document.getElementById('teamStatsModalBody');
            if (modalBody) {
                modalBody.innerHTML = `
                    <div class="team-stats-error">
                        <span class="error-icon">⚠️</span>
                        <h3>Failed to Load Statistics</h3>
                        <p>${error.message}</p>
                        <button class="btn btn-primary" onclick="window.router.showTeamStatsModal('${this.escapeHtml(teamName)}')">Retry</button>
                    </div>
                `;
            }
        }
    }

    /**
     * Render team stats content in modal with analytics
     */
    renderTeamStatsContent(stats, analytics, teamName) {
        const modalBody = document.getElementById('teamStatsModalBody');
        if (!modalBody) return;

        const { overall, homeStats, awayStats, goalStats, formStats, recentMatches, currentSeason, topRivals } = stats;

        // Store analytics for tab rendering
        this._currentAnalytics = analytics;
        this._currentTeamName = teamName;

        modalBody.innerHTML = `
            <div class="team-stats-tabs">
                <button class="team-stats-tab active" data-tab="overview">📊 Overview</button>
                <button class="team-stats-tab" data-tab="analytics">🎯 Model Accuracy</button>
                <button class="team-stats-tab" data-tab="homeaway">🏠 Home vs Away</button>
                <button class="team-stats-tab" data-tab="seasons">📅 Season History</button>
                <button class="team-stats-tab" data-tab="upcoming">⏭️ Upcoming</button>
                <button class="team-stats-tab" data-tab="goals">⚽ Goals</button>
                <button class="team-stats-tab" data-tab="form">📈 Form</button>
                <button class="team-stats-tab" data-tab="matches">🏟️ Matches</button>
                <button class="team-stats-tab" data-tab="rivals">⚔️ Rivals</button>
            </div>

            <div class="team-stats-tab-content" id="teamStatsTabContent">
                ${this.renderOverviewTab(overall, homeStats, awayStats, currentSeason)}
            </div>
        `;

        // Tab switching
        const tabs = modalBody.querySelectorAll('.team-stats-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                tabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                const tabType = tab.dataset.tab;
                const content = document.getElementById('teamStatsTabContent');
                switch(tabType) {
                    case 'overview':
                        content.innerHTML = this.renderOverviewTab(overall, homeStats, awayStats, currentSeason);
                        break;
                    case 'analytics':
                        content.innerHTML = this.renderAnalyticsTab(this._currentAnalytics);
                        break;
                    case 'homeaway':
                        content.innerHTML = this.renderHomeAwayTab(this._currentAnalytics);
                        break;
                    case 'seasons':
                        content.innerHTML = this.renderSeasonHistoryTab(this._currentAnalytics);
                        break;
                    case 'upcoming':
                        content.innerHTML = this.renderUpcomingMatchesTab(this._currentAnalytics);
                        break;
                    case 'goals':
                        content.innerHTML = this.renderGoalsTab(goalStats);
                        break;
                    case 'form':
                        content.innerHTML = this.renderFormTab(formStats);
                        break;
                    case 'matches':
                        content.innerHTML = this.renderMatchesTab(recentMatches);
                        break;
                    case 'rivals':
                        content.innerHTML = this.renderRivalsTab(topRivals);
                        break;
                }
            });
        });
    }

    /**
     * Render Overview tab
     */
    renderOverviewTab(overall, homeStats, awayStats, currentSeason) {
        return `
            <div class="stats-section">
                <h3 class="stats-section-title">Overall Performance</h3>
                <div class="stats-grid">
                    <div class="stat-card">
                        <span class="stat-value">${overall?.totalMatches || 0}</span>
                        <span class="stat-label">Matches</span>
                    </div>
                    <div class="stat-card stat-win">
                        <span class="stat-value">${overall?.wins || 0}</span>
                        <span class="stat-label">Wins</span>
                    </div>
                    <div class="stat-card stat-draw">
                        <span class="stat-value">${overall?.draws || 0}</span>
                        <span class="stat-label">Draws</span>
                    </div>
                    <div class="stat-card stat-loss">
                        <span class="stat-value">${overall?.losses || 0}</span>
                        <span class="stat-label">Losses</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${overall?.points || 0}</span>
                        <span class="stat-label">Points</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${(overall?.winPercentage || 0).toFixed(0)}%</span>
                        <span class="stat-label">Win Rate</span>
                    </div>
                </div>
            </div>

            <div class="stats-section">
                <h3 class="stats-section-title">Home vs Away</h3>
                <div class="home-away-comparison">
                    <div class="home-stats">
                        <h4>🏠 Home</h4>
                        <div class="mini-stats">
                            <span><span class="wdl-win">${homeStats?.wins || 0}W</span> / <span class="wdl-draw">${homeStats?.draws || 0}D</span> / <span class="wdl-loss">${homeStats?.losses || 0}L</span></span>
                            <span>Goals: ${homeStats?.goalsScored || 0} - ${homeStats?.goalsConceded || 0}</span>
                            <span>Win Rate: ${(homeStats?.winPercentage || 0).toFixed(0)}%</span>
                        </div>
                    </div>
                    <div class="away-stats">
                        <h4>✈️ Away</h4>
                        <div class="mini-stats">
                            <span><span class="wdl-win">${awayStats?.wins || 0}W</span> / <span class="wdl-draw">${awayStats?.draws || 0}D</span> / <span class="wdl-loss">${awayStats?.losses || 0}L</span></span>
                            <span>Goals: ${awayStats?.goalsScored || 0} - ${awayStats?.goalsConceded || 0}</span>
                            <span>Win Rate: ${(awayStats?.winPercentage || 0).toFixed(0)}%</span>
                        </div>
                    </div>
                </div>
            </div>

            ${currentSeason ? `
            <div class="stats-section">
                <h3 class="stats-section-title">Current Season (${currentSeason.season || ''})</h3>
                <div class="stats-grid">
                    <div class="stat-card">
                        <span class="stat-value">${currentSeason.matchesPlayed || 0}</span>
                        <span class="stat-label">Played</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${currentSeason.points || 0}</span>
                        <span class="stat-label">Points</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${currentSeason.goalDifference >= 0 ? '+' : ''}${currentSeason.goalDifference || 0}</span>
                        <span class="stat-label">Goal Diff</span>
                    </div>
                </div>
            </div>
            ` : ''}
        `;
    }

    /**
     * Render Goals tab
     */
    renderGoalsTab(goalStats) {
        if (!goalStats) return '<p class="no-data">No goal statistics available</p>';
        return `
            <div class="stats-section">
                <h3 class="stats-section-title">Scoring & Conceding</h3>
                <div class="stats-grid">
                    <div class="stat-card">
                        <span class="stat-value">${(goalStats.avgGoalsScored || 0).toFixed(2)}</span>
                        <span class="stat-label">Avg Goals/Match</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${(goalStats.avgGoalsConceded || 0).toFixed(2)}</span>
                        <span class="stat-label">Avg Conceded/Match</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${(goalStats.avgTotalGoalsPerMatch || 0).toFixed(2)}</span>
                        <span class="stat-label">Avg Total/Match</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${goalStats.cleanSheets || 0}</span>
                        <span class="stat-label">Clean Sheets</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${(goalStats.cleanSheetPercentage || 0).toFixed(0)}%</span>
                        <span class="stat-label">Clean Sheet %</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${goalStats.failedToScore || 0}</span>
                        <span class="stat-label">Failed to Score</span>
                    </div>
                </div>
            </div>

            <div class="stats-section">
                <h3 class="stats-section-title">Goals by Half</h3>
                <div class="half-goals">
                    <div class="half-stat">
                        <h4>1st Half</h4>
                        <span class="goals-scored">⚽ ${goalStats.firstHalfGoals || 0} scored (${(goalStats.firstHalfScoringRate || 0).toFixed(0)}%)</span>
                        <span class="goals-conceded">🥅 ${goalStats.firstHalfConceded || 0} conceded</span>
                    </div>
                    <div class="half-stat">
                        <h4>2nd Half</h4>
                        <span class="goals-scored">⚽ ${goalStats.secondHalfGoals || 0} scored (${(goalStats.secondHalfScoringRate || 0).toFixed(0)}%)</span>
                        <span class="goals-conceded">🥅 ${goalStats.secondHalfConceded || 0} conceded</span>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render Form tab
     */
    renderFormTab(formStats) {
        if (!formStats) return '<p class="no-data">No form statistics available</p>';

        const renderFormBadges = (form) => {
            if (!form) return '';
            return form.split('').map(r => {
                const cls = r === 'W' ? 'badge-win' : r === 'D' ? 'badge-draw' : 'badge-loss';
                return `<span class="form-badge ${cls}">${r}</span>`;
            }).join('');
        };

        return `
            <div class="stats-section">
                <h3 class="stats-section-title">Recent Form</h3>
                <div class="form-display">
                    <div class="form-row">
                        <span class="form-label">Last 5:</span>
                        <div class="form-badges">${renderFormBadges(formStats.last5Form)}</div>
                        <span class="form-points">${(formStats.last5FormPoints || 0).toFixed(1)} avg pts</span>
                    </div>
                    <div class="form-row">
                        <span class="form-label">Last 10:</span>
                        <div class="form-badges">${renderFormBadges(formStats.last10Form)}</div>
                        <span class="form-points">${(formStats.last10FormPoints || 0).toFixed(1)} avg pts</span>
                    </div>
                </div>
            </div>

            <div class="stats-section">
                <h3 class="stats-section-title">Streaks</h3>
                <div class="stats-grid">
                    <div class="stat-card">
                        <span class="stat-value">${formStats.currentWinStreak || 0}</span>
                        <span class="stat-label">Current Win Streak</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${formStats.currentUnbeatenStreak || 0}</span>
                        <span class="stat-label">Unbeaten Streak</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${formStats.longestWinStreak || 0}</span>
                        <span class="stat-label">Longest Win Streak</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${formStats.longestUnbeatenStreak || 0}</span>
                        <span class="stat-label">Longest Unbeaten</span>
                    </div>
                </div>
            </div>

            <div class="stats-section">
                <h3 class="stats-section-title">Match Stats</h3>
                <div class="stats-grid">
                    <div class="stat-card">
                        <span class="stat-value">${(formStats.avgShotsOnTarget || 0).toFixed(1)}</span>
                        <span class="stat-label">Avg Shots on Target</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${(formStats.avgCorners || 0).toFixed(1)}</span>
                        <span class="stat-label">Avg Corners</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value">${(formStats.shotConversionRate || 0).toFixed(0)}%</span>
                        <span class="stat-label">Shot Conversion</span>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render Matches tab
     */
    renderMatchesTab(recentMatches) {
        if (!recentMatches || recentMatches.length === 0) {
            return '<p class="no-data">No recent matches available</p>';
        }

        const matchRows = recentMatches.map(m => {
            const resultClass = m.result === 'W' ? 'result-win' : m.result === 'D' ? 'result-draw' : 'result-loss';
            const venueIcon = m.isHome ? '🏠' : '✈️';
            return `
                <tr>
                    <td>${m.date || ''}</td>
                    <td>${venueIcon}</td>
                    <td>${this.escapeHtml(m.opponent || '')}</td>
                    <td class="${resultClass}">${m.score || ''}</td>
                    <td><span class="result-badge ${resultClass}">${m.result || ''}</span></td>
                </tr>
            `;
        }).join('');

        return `
            <div class="stats-section">
                <h3 class="stats-section-title">Recent Matches</h3>
                <table class="matches-table">
                    <thead>
                        <tr>
                            <th>Date</th>
                            <th>Venue</th>
                            <th>Opponent</th>
                            <th>Score</th>
                            <th>Result</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${matchRows}
                    </tbody>
                </table>
            </div>
        `;
    }

    /**
     * Render Rivals tab
     */
    renderRivalsTab(topRivals) {
        if (!topRivals || topRivals.length === 0) {
            return '<p class="no-data">No head-to-head data available</p>';
        }

        const rivalRows = topRivals.map(r => `
            <tr>
                <td>${this.escapeHtml(r.opponent || '')}</td>
                <td>${r.totalMatches || 0}</td>
                <td class="result-win">${r.wins || 0}</td>
                <td class="result-draw">${r.draws || 0}</td>
                <td class="result-loss">${r.losses || 0}</td>
                <td>${r.goalsFor || 0} - ${r.goalsAgainst || 0}</td>
                <td>${(r.winPercentage || 0).toFixed(0)}%</td>
            </tr>
        `).join('');

        return `
            <div class="stats-section">
                <h3 class="stats-section-title">Head-to-Head Records</h3>
                <table class="matches-table">
                    <thead>
                        <tr>
                            <th>Opponent</th>
                            <th>Matches</th>
                            <th>W</th>
                            <th>D</th>
                            <th>L</th>
                            <th>Goals</th>
                            <th>Win %</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rivalRows}
                    </tbody>
                </table>
            </div>
        `;
    }

    /**
     * Render Model Accuracy analytics tab
     */
    renderAnalyticsTab(analytics) {
        if (!analytics || !analytics.modelAccuracy) {
            return `
                <div class="analytics-empty-state">
                    <span class="empty-icon">📊</span>
                    <h3>No Prediction Data Available</h3>
                    <p>Model accuracy data will appear here once predictions have been made and resolved for this team.</p>
                </div>
            `;
        }

        const acc = analytics.modelAccuracy;
        const byResult = acc.accuracyByResult || {};

        const trendIcon = acc.accuracyTrend === 'IMPROVING' ? '📈' :
                          acc.accuracyTrend === 'DECLINING' ? '📉' : '➡️';
        const trendClass = acc.accuracyTrend === 'IMPROVING' ? 'trend-up' :
                          acc.accuracyTrend === 'DECLINING' ? 'trend-down' : 'trend-stable';

        return `
            <div class="analytics-grid">
                <!-- Main Accuracy Card -->
                <div class="analytics-card analytics-card--primary">
                    <div class="analytics-card-header">
                        <h3>🎯 Overall Model Accuracy</h3>
                        <span class="accuracy-trend ${trendClass}">${trendIcon} ${acc.accuracyTrend || 'N/A'}</span>
                    </div>
                    <div class="accuracy-circle-container">
                        <div class="accuracy-circle" style="--accuracy: ${acc.overallAccuracy || 0}">
                            <span class="accuracy-value">${(acc.overallAccuracy || 0).toFixed(1)}%</span>
                            <span class="accuracy-label">Accuracy</span>
                        </div>
                    </div>
                    <div class="accuracy-stats">
                        <div class="accuracy-stat">
                            <span class="stat-number">${acc.totalPredictions || 0}</span>
                            <span class="stat-desc">Total Predictions</span>
                        </div>
                        <div class="accuracy-stat stat-correct">
                            <span class="stat-number">${acc.correctPredictions || 0}</span>
                            <span class="stat-desc">Correct</span>
                        </div>
                        <div class="accuracy-stat">
                            <span class="stat-number">${(acc.averageConfidence || 0).toFixed(1)}%</span>
                            <span class="stat-desc">Avg Confidence</span>
                        </div>
                    </div>
                </div>

                <!-- High Confidence Accuracy -->
                <div class="analytics-card">
                    <div class="analytics-card-header">
                        <h3>🔥 High Confidence Predictions</h3>
                        <span class="badge badge-warning">≥60% confidence</span>
                    </div>
                    <div class="high-conf-stats">
                        <div class="high-conf-accuracy">
                            <span class="big-number">${(acc.highConfidenceAccuracy || 0).toFixed(1)}%</span>
                            <span class="label">Accuracy</span>
                        </div>
                        <div class="high-conf-details">
                            <p><strong>${acc.correctHighConfidencePredictions || 0}</strong> / ${acc.highConfidencePredictions || 0} correct</p>
                            <div class="progress-bar">
                                <div class="progress-fill" style="width: ${acc.highConfidenceAccuracy || 0}%"></div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Home vs Away Accuracy -->
                <div class="analytics-card">
                    <div class="analytics-card-header">
                        <h3>🏠 Home vs Away Accuracy</h3>
                    </div>
                    <div class="home-away-accuracy">
                        <div class="venue-accuracy home">
                            <span class="venue-icon">🏠</span>
                            <div class="venue-stats">
                                <span class="venue-percent">${(acc.homeAccuracy || 0).toFixed(1)}%</span>
                                <span class="venue-count">${acc.correctHomePredictions || 0}/${acc.homePredictions || 0}</span>
                            </div>
                            <span class="venue-label">Home</span>
                        </div>
                        <div class="vs-divider">VS</div>
                        <div class="venue-accuracy away">
                            <span class="venue-icon">✈️</span>
                            <div class="venue-stats">
                                <span class="venue-percent">${(acc.awayAccuracy || 0).toFixed(1)}%</span>
                                <span class="venue-count">${acc.correctAwayPredictions || 0}/${acc.awayPredictions || 0}</span>
                            </div>
                            <span class="venue-label">Away</span>
                        </div>
                    </div>
                </div>

                <!-- Accuracy by Prediction Type -->
                <div class="analytics-card">
                    <div class="analytics-card-header">
                        <h3>📊 Accuracy by Prediction Type</h3>
                    </div>
                    <div class="prediction-type-accuracy">
                        <div class="type-row">
                            <span class="type-label result-win">WIN</span>
                            <div class="type-bar-container">
                                <div class="type-bar type-bar-win" style="width: ${byResult.winPredictionAccuracy || 0}%"></div>
                                <span class="type-percent">${(byResult.winPredictionAccuracy || 0).toFixed(1)}%</span>
                            </div>
                            <span class="type-count">${byResult.correctWinPredictions || 0}/${byResult.winPredictions || 0}</span>
                        </div>
                        <div class="type-row">
                            <span class="type-label result-draw">DRAW</span>
                            <div class="type-bar-container">
                                <div class="type-bar type-bar-draw" style="width: ${byResult.drawPredictionAccuracy || 0}%"></div>
                                <span class="type-percent">${(byResult.drawPredictionAccuracy || 0).toFixed(1)}%</span>
                            </div>
                            <span class="type-count">${byResult.correctDrawPredictions || 0}/${byResult.drawPredictions || 0}</span>
                        </div>
                        <div class="type-row">
                            <span class="type-label result-loss">LOSS</span>
                            <div class="type-bar-container">
                                <div class="type-bar type-bar-loss" style="width: ${byResult.lossPredictionAccuracy || 0}%"></div>
                                <span class="type-percent">${(byResult.lossPredictionAccuracy || 0).toFixed(1)}%</span>
                            </div>
                            <span class="type-count">${byResult.correctLossPredictions || 0}/${byResult.lossPredictions || 0}</span>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render Home vs Away trends tab
     */
    renderHomeAwayTab(analytics) {
        if (!analytics || !analytics.homeAwayTrend) {
            return '<p class="no-data">No home/away trend data available</p>';
        }

        const trend = analytics.homeAwayTrend;
        const home = trend.homeTrend || {};
        const away = trend.awayTrend || {};

        const strongerClass = trend.strongerVenue === 'HOME' ? 'home-stronger' :
                             trend.strongerVenue === 'AWAY' ? 'away-stronger' : 'balanced';

        const renderRecentForm = (results) => {
            if (!results || results.length === 0) return '<span class="no-data-inline">No data</span>';
            return results.slice(0, 5).map(r => {
                const cls = r.result === 'W' ? 'badge-win' : r.result === 'D' ? 'badge-draw' : 'badge-loss';
                return `<span class="form-badge ${cls}" title="${r.opponent}: ${r.goalsScored}-${r.goalsConceded}">${r.result}</span>`;
            }).join('');
        };

        return `
            <div class="home-away-dashboard">
                <!-- Venue Strength Indicator -->
                <div class="venue-strength-card ${strongerClass}">
                    <h3>Venue Strength Analysis</h3>
                    <div class="strength-indicator">
                        <div class="strength-bar">
                            <div class="strength-home" style="width: ${50 + (trend.homeAdvantage / 2)}%">
                                <span>🏠 ${(home.winRate || 0).toFixed(1)}%</span>
                            </div>
                            <div class="strength-away" style="width: ${50 - (trend.homeAdvantage / 2)}%">
                                <span>✈️ ${(away.winRate || 0).toFixed(1)}%</span>
                            </div>
                        </div>
                        <p class="strength-summary">
                            ${trend.strongerVenue === 'HOME' ? '🏠 Stronger at Home' :
                              trend.strongerVenue === 'AWAY' ? '✈️ Stronger Away' :
                              '⚖️ Balanced Performance'}
                            ${trend.homeAdvantage !== 0 ? ` (${Math.abs(trend.homeAdvantage).toFixed(1)}% difference)` : ''}
                        </p>
                    </div>
                </div>

                <!-- Home Performance -->
                <div class="venue-performance-card home-card">
                    <div class="venue-header">
                        <span class="venue-emoji">🏠</span>
                        <h3>Home Performance</h3>
                    </div>
                    <div class="venue-stats-grid">
                        <div class="venue-stat">
                            <span class="stat-value">${home.totalMatches || 0}</span>
                            <span class="stat-label">Matches</span>
                        </div>
                        <div class="venue-stat stat-win">
                            <span class="stat-value">${home.wins || 0}</span>
                            <span class="stat-label">Wins</span>
                        </div>
                        <div class="venue-stat stat-draw">
                            <span class="stat-value">${home.draws || 0}</span>
                            <span class="stat-label">Draws</span>
                        </div>
                        <div class="venue-stat stat-loss">
                            <span class="stat-value">${home.losses || 0}</span>
                            <span class="stat-label">Losses</span>
                        </div>
                        <div class="venue-stat">
                            <span class="stat-value">${(home.winRate || 0).toFixed(1)}%</span>
                            <span class="stat-label">Win Rate</span>
                        </div>
                        <div class="venue-stat">
                            <span class="stat-value">${home.cleanSheets || 0}</span>
                            <span class="stat-label">Clean Sheets</span>
                        </div>
                    </div>
                    <div class="venue-goals">
                        <div class="goal-stat">
                            <span class="goal-icon">⚽</span>
                            <span class="goal-avg">${(home.avgGoalsScored || 0).toFixed(2)}</span>
                            <span class="goal-label">Avg Scored</span>
                        </div>
                        <div class="goal-stat">
                            <span class="goal-icon">🥅</span>
                            <span class="goal-avg">${(home.avgGoalsConceded || 0).toFixed(2)}</span>
                            <span class="goal-label">Avg Conceded</span>
                        </div>
                    </div>
                    <div class="venue-form">
                        <span class="form-label">Recent:</span>
                        <div class="form-badges">${renderRecentForm(home.recentResults)}</div>
                    </div>
                    ${home.currentStreak ? `
                        <div class="current-streak">
                            Current: <strong>${home.currentStreak} ${home.streakType}</strong> streak
                        </div>
                    ` : ''}
                </div>

                <!-- Away Performance -->
                <div class="venue-performance-card away-card">
                    <div class="venue-header">
                        <span class="venue-emoji">✈️</span>
                        <h3>Away Performance</h3>
                    </div>
                    <div class="venue-stats-grid">
                        <div class="venue-stat">
                            <span class="stat-value">${away.totalMatches || 0}</span>
                            <span class="stat-label">Matches</span>
                        </div>
                        <div class="venue-stat stat-win">
                            <span class="stat-value">${away.wins || 0}</span>
                            <span class="stat-label">Wins</span>
                        </div>
                        <div class="venue-stat stat-draw">
                            <span class="stat-value">${away.draws || 0}</span>
                            <span class="stat-label">Draws</span>
                        </div>
                        <div class="venue-stat stat-loss">
                            <span class="stat-value">${away.losses || 0}</span>
                            <span class="stat-label">Losses</span>
                        </div>
                        <div class="venue-stat">
                            <span class="stat-value">${(away.winRate || 0).toFixed(1)}%</span>
                            <span class="stat-label">Win Rate</span>
                        </div>
                        <div class="venue-stat">
                            <span class="stat-value">${away.cleanSheets || 0}</span>
                            <span class="stat-label">Clean Sheets</span>
                        </div>
                    </div>
                    <div class="venue-goals">
                        <div class="goal-stat">
                            <span class="goal-icon">⚽</span>
                            <span class="goal-avg">${(away.avgGoalsScored || 0).toFixed(2)}</span>
                            <span class="goal-label">Avg Scored</span>
                        </div>
                        <div class="goal-stat">
                            <span class="goal-icon">🥅</span>
                            <span class="goal-avg">${(away.avgGoalsConceded || 0).toFixed(2)}</span>
                            <span class="goal-label">Avg Conceded</span>
                        </div>
                    </div>
                    <div class="venue-form">
                        <span class="form-label">Recent:</span>
                        <div class="form-badges">${renderRecentForm(away.recentResults)}</div>
                    </div>
                    ${away.currentStreak ? `
                        <div class="current-streak">
                            Current: <strong>${away.currentStreak} ${away.streakType}</strong> streak
                        </div>
                    ` : ''}
                </div>
            </div>
        `;
    }

    /**
     * Render Season History tab
     */
    renderSeasonHistoryTab(analytics) {
        if (!analytics || !analytics.seasonHistory || analytics.seasonHistory.length === 0) {
            return '<p class="no-data">No season history data available</p>';
        }

        const seasons = analytics.seasonHistory;

        // Build comparison chart data (for visual representation)
        const maxPoints = Math.max(...seasons.map(s => s.points || 0));

        const seasonRows = seasons.map(s => `
            <tr>
                <td class="season-cell"><strong>${s.season}</strong></td>
                <td>${s.matchesPlayed || 0}</td>
                <td class="result-win">${s.wins || 0}</td>
                <td class="result-draw">${s.draws || 0}</td>
                <td class="result-loss">${s.losses || 0}</td>
                <td>${s.goalsScored || 0}</td>
                <td>${s.goalsConceded || 0}</td>
                <td class="${s.goalDifference >= 0 ? 'result-win' : 'result-loss'}">${s.goalDifference >= 0 ? '+' : ''}${s.goalDifference || 0}</td>
                <td><strong>${s.points || 0}</strong></td>
                <td>${(s.winRate || 0).toFixed(1)}%</td>
            </tr>
        `).join('');

        // Season comparison cards
        const seasonCards = seasons.slice(0, 4).map(s => {
            const barWidth = maxPoints > 0 ? ((s.points || 0) / maxPoints * 100) : 0;
            return `
                <div class="season-card">
                    <div class="season-card-header">
                        <span class="season-name">${s.season}</span>
                        <span class="season-points">${s.points || 0} pts</span>
                    </div>
                    <div class="season-bar-container">
                        <div class="season-bar" style="width: ${barWidth}%"></div>
                    </div>
                    <div class="season-quick-stats">
                        <span class="wdl"><span class="wdl-win">${s.wins}W</span> <span class="wdl-draw">${s.draws}D</span> <span class="wdl-loss">${s.losses}L</span></span>
                        <span class="goals">${s.goalsScored}-${s.goalsConceded}</span>
                    </div>
                </div>
            `;
        }).join('');

        return `
            <div class="season-history-dashboard">
                <!-- Season Overview Cards -->
                <div class="stats-section">
                    <h3 class="stats-section-title">Season Performance Overview</h3>
                    <div class="season-cards-grid">
                        ${seasonCards}
                    </div>
                </div>

                <!-- Detailed Season Table -->
                <div class="stats-section">
                    <h3 class="stats-section-title">Detailed Season Statistics</h3>
                    <div class="table-scroll-container">
                        <table class="matches-table season-table">
                            <thead>
                                <tr>
                                    <th>Season</th>
                                    <th>MP</th>
                                    <th>W</th>
                                    <th>D</th>
                                    <th>L</th>
                                    <th>GF</th>
                                    <th>GA</th>
                                    <th>GD</th>
                                    <th>Pts</th>
                                    <th>Win %</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${seasonRows}
                            </tbody>
                        </table>
                    </div>
                </div>

                ${analytics.predictionComparison && analytics.predictionComparison.length > 0 ? `
                <!-- Prediction vs Actual -->
                <div class="stats-section">
                    <h3 class="stats-section-title">Predicted vs Actual Performance</h3>
                    ${this.renderPredictionComparisonChart(analytics.predictionComparison)}
                </div>
                ` : ''}
            </div>
        `;
    }

    /**
     * Render Prediction vs Actual comparison
     */
    renderPredictionComparisonChart(comparisons) {
        if (!comparisons || comparisons.length === 0) return '';

        const rows = comparisons.map(c => `
            <tr>
                <td><strong>${c.season}</strong></td>
                <td>${c.totalMatches || 0}</td>
                <td class="result-win">${c.predictedWins || 0} → ${c.actualWins || 0}</td>
                <td class="result-draw">${c.predictedDraws || 0} → ${c.actualDraws || 0}</td>
                <td class="result-loss">${c.predictedLosses || 0} → ${c.actualLosses || 0}</td>
                <td>${c.predictedPoints || 0} → ${c.actualPoints || 0}</td>
                <td>${c.correctPredictions || 0}</td>
                <td><span class="accuracy-badge">${(c.seasonAccuracy || 0).toFixed(1)}%</span></td>
            </tr>
        `).join('');

        return `
            <div class="table-scroll-container">
                <table class="matches-table prediction-table">
                    <thead>
                        <tr>
                            <th>Season</th>
                            <th>Matches</th>
                            <th>Wins (P→A)</th>
                            <th>Draws (P→A)</th>
                            <th>Losses (P→A)</th>
                            <th>Points (P→A)</th>
                            <th>Correct</th>
                            <th>Accuracy</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rows}
                    </tbody>
                </table>
            </div>
        `;
    }

    /**
     * Render Upcoming Matches tab
     */
    renderUpcomingMatchesTab(analytics) {
        if (!analytics || !analytics.upcomingMatches || analytics.upcomingMatches.length === 0) {
            const teamName = this._currentTeamName || 'this team';
            return `
                <div class="upcoming-empty-state">
                    <span class="empty-icon">📅</span>
                    <h3>No Upcoming Fixtures</h3>
                    <p>No scheduled matches found for ${this.escapeHtml(teamName)}.</p>
                    <p class="empty-state-hint">
                        This team may not be in the Premier League, or the season has ended.
                        You can still make manual predictions using the prediction page.
                    </p>
                    <div class="empty-state-actions">
                        <a href="#predictions" class="btn btn-primary btn-sm">
                            <span>🎯</span> Make a Prediction
                        </a>
                        <a href="#dashboard" class="btn btn-outline btn-sm">
                            <span>📊</span> View Dashboard
                        </a>
                    </div>
                </div>
            `;
        }

        const matches = analytics.upcomingMatches;
        const hasSimulated = matches.some(m => m.simulated);

        const matchCards = matches.map(m => {
            const confClass = (m.confidence || 0) >= 0.6 ? 'high-conf' :
                             (m.confidence || 0) >= 0.4 ? 'med-conf' : 'low-conf';
            const predClass = m.predictedResult === 'WIN' ? 'pred-win' :
                             m.predictedResult === 'DRAW' ? 'pred-draw' : 'pred-loss';
            const simulatedBadge = m.simulated ? '<span class="simulated-badge" title="Projected fixture based on historical data">📊 Projected</span>' : '';

            return `
                <div class="upcoming-match-card ${m.simulated ? 'simulated' : ''}">
                    <div class="match-date">
                        <span class="date-day">${m.matchDate ? new Date(m.matchDate).toLocaleDateString('en-US', {weekday: 'short'}) : ''}</span>
                        <span class="date-full">${m.matchDate ? new Date(m.matchDate).toLocaleDateString('en-US', {month: 'short', day: 'numeric'}) : 'TBD'}</span>
                        ${simulatedBadge}
                    </div>
                    <div class="match-details">
                        <div class="match-opponent">
                            <span class="venue-badge ${m.isHome ? 'home' : 'away'}">${m.isHome ? '🏠 HOME' : '✈️ AWAY'}</span>
                            <span class="opponent-name">vs ${this.escapeHtml(m.opponent || 'Unknown')}</span>
                        </div>
                        <div class="match-prediction ${predClass}">
                            <span class="pred-result">${m.predictedResult || 'N/A'}</span>
                            <span class="pred-confidence ${confClass}">${((m.confidence || 0) * 100).toFixed(0)}% confidence</span>
                        </div>
                    </div>
                    <div class="match-probabilities">
                        <div class="prob-bar">
                            <div class="prob-segment prob-home" style="width: ${(m.probHomeWin || 0) * 100}%" title="Home: ${((m.probHomeWin || 0) * 100).toFixed(1)}%"></div>
                            <div class="prob-segment prob-draw" style="width: ${(m.probDraw || 0) * 100}%" title="Draw: ${((m.probDraw || 0) * 100).toFixed(1)}%"></div>
                            <div class="prob-segment prob-away" style="width: ${(m.probAwayWin || 0) * 100}%" title="Away: ${((m.probAwayWin || 0) * 100).toFixed(1)}%"></div>
                        </div>
                        <div class="prob-labels">
                            <span>H: ${((m.probHomeWin || 0) * 100).toFixed(0)}%</span>
                            <span>D: ${((m.probDraw || 0) * 100).toFixed(0)}%</span>
                            <span>A: ${((m.probAwayWin || 0) * 100).toFixed(0)}%</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        const simulatedNote = hasSimulated ? `
            <div class="simulated-fixtures-note">
                <span class="note-icon">ℹ️</span>
                <span>Some fixtures are projected based on historical league patterns. Actual fixture dates may vary.</span>
            </div>
        ` : '';

        return `
            <div class="upcoming-matches-container">
                <div class="stats-section">
                    <h3 class="stats-section-title">Upcoming Fixtures & Predictions</h3>
                    ${simulatedNote}
                    <div class="upcoming-matches-grid">
                        ${matchCards}
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Add team analytics styles
     */
    addTeamAnalyticsStyles() {
        if (document.getElementById('teamAnalyticsStyles')) return;

        const style = document.createElement('style');
        style.id = 'teamAnalyticsStyles';
        style.textContent = `
            /* Modal sizing for analytics */
            .team-stats-modal--large {
                max-width: 1100px;
            }

            /* Analytics Grid */
            .analytics-grid {
                display: grid;
                grid-template-columns: repeat(2, 1fr);
                gap: 1.5rem;
            }

            .analytics-card {
                background: var(--bg-tertiary, #16213e);
                border-radius: 0.75rem;
                padding: 1.5rem;
                border: 1px solid var(--border-color, #333);
            }

            .analytics-card--primary {
                grid-column: 1 / -1;
                background: linear-gradient(135deg, var(--bg-tertiary) 0%, rgba(52, 152, 219, 0.1) 100%);
                border-color: var(--accent-blue, #3498db);
            }

            .analytics-card-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 1rem;
            }

            .analytics-card-header h3 {
                margin: 0;
                font-size: 1.1rem;
            }

            /* Accuracy Circle */
            .accuracy-circle-container {
                display: flex;
                justify-content: center;
                margin: 1.5rem 0;
            }

            .accuracy-circle {
                width: 140px;
                height: 140px;
                border-radius: 50%;
                background: conic-gradient(
                    var(--accent-green, #2ecc71) calc(var(--accuracy) * 1%),
                    var(--bg-secondary, #1a1a2e) calc(var(--accuracy) * 1%)
                );
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                position: relative;
            }

            .accuracy-circle::before {
                content: '';
                position: absolute;
                width: 110px;
                height: 110px;
                border-radius: 50%;
                background: var(--bg-tertiary, #16213e);
            }

            .accuracy-circle .accuracy-value,
            .accuracy-circle .accuracy-label {
                position: relative;
                z-index: 1;
            }

            .accuracy-circle .accuracy-value {
                font-size: 2rem;
                font-weight: 700;
                color: var(--accent-green, #2ecc71);
            }

            .accuracy-circle .accuracy-label {
                font-size: 0.75rem;
                color: var(--text-muted, #888);
            }

            /* Accuracy Stats */
            .accuracy-stats {
                display: flex;
                justify-content: center;
                gap: 2rem;
                margin-top: 1rem;
            }

            .accuracy-stat {
                text-align: center;
            }

            .accuracy-stat .stat-number {
                font-size: 1.5rem;
                font-weight: 700;
                display: block;
            }

            .accuracy-stat.stat-correct .stat-number {
                color: var(--accent-green, #2ecc71);
            }

            .accuracy-stat .stat-desc {
                font-size: 0.75rem;
                color: var(--text-muted, #888);
            }

            /* Trend indicator */
            .accuracy-trend {
                padding: 0.25rem 0.75rem;
                border-radius: 1rem;
                font-size: 0.85rem;
            }

            .trend-up {
                background: rgba(46, 204, 113, 0.2);
                color: var(--accent-green, #2ecc71);
            }

            .trend-down {
                background: rgba(231, 76, 60, 0.2);
                color: var(--accent-red, #e74c3c);
            }

            .trend-stable {
                background: rgba(241, 196, 15, 0.2);
                color: var(--accent-yellow, #f1c40f);
            }

            /* High Confidence Stats */
            .high-conf-stats {
                display: flex;
                align-items: center;
                gap: 1.5rem;
            }

            .high-conf-accuracy {
                text-align: center;
            }

            .high-conf-accuracy .big-number {
                font-size: 2.5rem;
                font-weight: 700;
                color: var(--accent-blue, #3498db);
            }

            .high-conf-accuracy .label {
                display: block;
                font-size: 0.75rem;
                color: var(--text-muted);
            }

            .high-conf-details {
                flex: 1;
            }

            .progress-bar {
                height: 8px;
                background: var(--bg-secondary);
                border-radius: 4px;
                overflow: hidden;
                margin-top: 0.5rem;
            }

            .progress-fill {
                height: 100%;
                background: var(--accent-blue, #3498db);
                transition: width 0.3s ease;
            }

            /* Home vs Away Accuracy */
            .home-away-accuracy {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 1rem;
            }

            .venue-accuracy {
                text-align: center;
                flex: 1;
            }

            .venue-accuracy .venue-icon {
                font-size: 2rem;
                display: block;
                margin-bottom: 0.5rem;
            }

            .venue-accuracy .venue-percent {
                font-size: 1.75rem;
                font-weight: 700;
                display: block;
            }

            .venue-accuracy.home .venue-percent {
                color: var(--accent-green, #2ecc71);
            }

            .venue-accuracy.away .venue-percent {
                color: var(--accent-blue, #3498db);
            }

            .venue-accuracy .venue-count {
                font-size: 0.8rem;
                color: var(--text-muted);
                display: block;
            }

            .vs-divider {
                font-weight: 700;
                color: var(--text-muted);
                font-size: 1.25rem;
            }

            /* Prediction Type Accuracy */
            .prediction-type-accuracy {
                display: flex;
                flex-direction: column;
                gap: 1rem;
            }

            .type-row {
                display: flex;
                align-items: center;
                gap: 1rem;
            }

            .type-label {
                width: 60px;
                font-weight: 600;
            }

            .type-bar-container {
                flex: 1;
                height: 24px;
                background: var(--bg-secondary);
                border-radius: 4px;
                position: relative;
                overflow: hidden;
            }

            .type-bar {
                height: 100%;
                border-radius: 4px;
            }

            .type-bar-win { background: var(--accent-green, #2ecc71); }
            .type-bar-draw { background: var(--accent-yellow, #f1c40f); }
            .type-bar-loss { background: var(--accent-red, #e74c3c); }

            .type-percent {
                position: absolute;
                right: 8px;
                top: 50%;
                transform: translateY(-50%);
                font-size: 0.8rem;
                font-weight: 600;
            }

            .type-count {
                width: 60px;
                text-align: right;
                font-size: 0.85rem;
                color: var(--text-muted);
            }

            /* Home Away Dashboard */
            .home-away-dashboard {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 1.5rem;
            }

            .venue-strength-card {
                grid-column: 1 / -1;
                background: var(--bg-tertiary);
                padding: 1.5rem;
                border-radius: 0.75rem;
                text-align: center;
                border: 1px solid var(--border-color);
            }

            .strength-bar {
                display: flex;
                height: 40px;
                border-radius: 0.5rem;
                overflow: hidden;
                margin: 1rem 0;
            }

            .strength-home, .strength-away {
                display: flex;
                align-items: center;
                justify-content: center;
                font-weight: 600;
                color: #fff;
            }

            .strength-home {
                background: var(--accent-green, #2ecc71);
            }

            .strength-away {
                background: var(--accent-blue, #3498db);
            }

            .strength-summary {
                margin: 0;
                color: var(--text-muted);
            }

            .venue-performance-card {
                background: var(--bg-tertiary);
                padding: 1.5rem;
                border-radius: 0.75rem;
                border: 1px solid var(--border-color);
            }

            .home-card {
                border-left: 4px solid var(--accent-green, #2ecc71);
            }

            .away-card {
                border-left: 4px solid var(--accent-blue, #3498db);
            }

            .venue-header {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                margin-bottom: 1rem;
            }

            .venue-emoji {
                font-size: 1.5rem;
            }

            .venue-stats-grid {
                display: grid;
                grid-template-columns: repeat(3, 1fr);
                gap: 0.75rem;
                margin-bottom: 1rem;
            }

            .venue-stat {
                text-align: center;
                padding: 0.5rem;
                background: var(--bg-secondary);
                border-radius: 0.5rem;
            }

            .venue-goals {
                display: flex;
                justify-content: space-around;
                margin: 1rem 0;
                padding: 0.75rem;
                background: var(--bg-secondary);
                border-radius: 0.5rem;
            }

            .goal-stat {
                text-align: center;
            }

            .goal-icon {
                display: block;
                font-size: 1.25rem;
            }

            .goal-avg {
                font-size: 1.25rem;
                font-weight: 700;
                display: block;
            }

            .goal-label {
                font-size: 0.7rem;
                color: var(--text-muted);
            }

            .venue-form {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                margin-top: 1rem;
            }

            .current-streak {
                margin-top: 0.75rem;
                padding: 0.5rem;
                background: var(--bg-secondary);
                border-radius: 0.5rem;
                text-align: center;
                font-size: 0.9rem;
            }

            /* Season History */
            .season-cards-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 1rem;
            }

            .season-card {
                background: var(--bg-tertiary);
                padding: 1rem;
                border-radius: 0.5rem;
                border: 1px solid var(--border-color);
            }

            .season-card-header {
                display: flex;
                justify-content: space-between;
                margin-bottom: 0.75rem;
            }

            .season-name {
                font-weight: 600;
            }

            .season-points {
                font-weight: 700;
                color: var(--accent-blue);
            }

            .season-bar-container {
                height: 8px;
                background: var(--bg-secondary);
                border-radius: 4px;
                overflow: hidden;
                margin-bottom: 0.75rem;
            }

            .season-bar {
                height: 100%;
                background: var(--accent-blue);
            }

            .season-quick-stats {
                display: flex;
                justify-content: space-between;
                font-size: 0.85rem;
            }

            .table-scroll-container {
                overflow-x: auto;
            }

            .season-table th, .season-table td {
                padding: 0.5rem 0.75rem;
                white-space: nowrap;
            }

            .accuracy-badge {
                background: var(--accent-blue);
                color: #fff;
                padding: 0.25rem 0.5rem;
                border-radius: 0.25rem;
                font-weight: 600;
            }

            /* Upcoming Matches */
            .upcoming-matches-grid {
                display: flex;
                flex-direction: column;
                gap: 1rem;
            }

            .upcoming-match-card {
                display: flex;
                align-items: center;
                gap: 1.5rem;
                background: var(--bg-tertiary);
                padding: 1.25rem;
                border-radius: 0.75rem;
                border: 1px solid var(--border-color);
            }

            .match-date {
                text-align: center;
                min-width: 60px;
            }

            .date-day {
                font-size: 0.75rem;
                color: var(--text-muted);
                display: block;
            }

            .date-full {
                font-weight: 600;
            }

            .match-details {
                flex: 1;
            }

            .match-opponent {
                margin-bottom: 0.5rem;
            }

            .venue-badge {
                font-size: 0.7rem;
                padding: 0.2rem 0.5rem;
                border-radius: 0.25rem;
                margin-right: 0.5rem;
            }

            .venue-badge.home {
                background: rgba(46, 204, 113, 0.2);
                color: var(--accent-green);
            }

            .venue-badge.away {
                background: rgba(52, 152, 219, 0.2);
                color: var(--accent-blue);
            }

            .opponent-name {
                font-weight: 600;
            }

            .match-prediction {
                display: flex;
                align-items: center;
                gap: 0.75rem;
            }

            .pred-result {
                font-weight: 700;
                font-size: 1.1rem;
            }

            .pred-win .pred-result { color: var(--accent-green); }
            .pred-draw .pred-result { color: var(--accent-yellow); }
            .pred-loss .pred-result { color: var(--accent-red); }

            .pred-confidence {
                font-size: 0.8rem;
                padding: 0.2rem 0.5rem;
                border-radius: 0.25rem;
            }

            .high-conf {
                background: rgba(46, 204, 113, 0.2);
                color: var(--accent-green);
            }

            .med-conf {
                background: rgba(241, 196, 15, 0.2);
                color: var(--accent-yellow);
            }

            .low-conf {
                background: rgba(231, 76, 60, 0.2);
                color: var(--accent-red);
            }

            .match-probabilities {
                min-width: 150px;
            }

            .prob-bar {
                display: flex;
                height: 24px;
                border-radius: 4px;
                overflow: hidden;
            }

            .prob-segment {
                height: 100%;
            }

            .prob-home { background: var(--accent-green); }
            .prob-draw { background: var(--accent-yellow); }
            .prob-away { background: var(--accent-blue); }

            .prob-labels {
                display: flex;
                justify-content: space-between;
                font-size: 0.7rem;
                color: var(--text-muted);
                margin-top: 0.25rem;
            }

            /* Empty States */
            .analytics-empty-state, .upcoming-empty-state {
                text-align: center;
                padding: 3rem;
            }

            .empty-icon {
                font-size: 3rem;
                display: block;
                margin-bottom: 1rem;
                opacity: 0.5;
            }

            .no-data-inline {
                color: var(--text-muted);
                font-style: italic;
            }

            /* Responsive */
            @media (max-width: 768px) {
                .analytics-grid {
                    grid-template-columns: 1fr;
                }

                .home-away-dashboard {
                    grid-template-columns: 1fr;
                }

                .upcoming-match-card {
                    flex-direction: column;
                    text-align: center;
                }

                .match-probabilities {
                    width: 100%;
                }
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Add modal styles
     */
    addTeamStatsModalStyles() {
        if (document.getElementById('teamStatsModalStyles')) return;

        const style = document.createElement('style');
        style.id = 'teamStatsModalStyles';
        style.textContent = `
            .team-stats-modal-overlay {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.7);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
                animation: fadeIn 0.2s ease;
            }
            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            .team-stats-modal {
                background: var(--bg-secondary, #1a1a2e);
                border-radius: 1rem;
                width: 90%;
                max-width: 900px;
                max-height: 85vh;
                overflow: hidden;
                display: flex;
                flex-direction: column;
                box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
                animation: slideUp 0.3s ease;
            }
            @keyframes slideUp {
                from { transform: translateY(20px); opacity: 0; }
                to { transform: translateY(0); opacity: 1; }
            }
            .team-stats-modal-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 1.5rem;
                border-bottom: 1px solid var(--border-color, #333);
                background: var(--bg-tertiary, #16213e);
            }
            .team-stats-modal-title {
                font-size: 1.5rem;
                font-weight: 700;
                color: var(--text-primary, #fff);
                display: flex;
                align-items: center;
                gap: 0.75rem;
                margin: 0;
            }
            .team-logo-icon {
                font-size: 2rem;
            }
            .team-stats-modal-close {
                background: none;
                border: none;
                font-size: 2rem;
                color: var(--text-muted, #888);
                cursor: pointer;
                padding: 0.5rem;
                line-height: 1;
                transition: color 0.2s;
            }
            .team-stats-modal-close:hover {
                color: var(--accent-red, #ff4757);
            }
            .team-stats-modal-body {
                padding: 1.5rem;
                overflow-y: auto;
                flex: 1;
            }
            .team-stats-loading, .team-stats-error {
                text-align: center;
                padding: 3rem;
            }
            .team-stats-loading .loading-spinner {
                width: 50px;
                height: 50px;
                border: 4px solid var(--border-color, #333);
                border-top-color: var(--accent-blue, #3498db);
                border-radius: 50%;
                animation: spin 1s linear infinite;
                margin: 0 auto 1rem;
            }
            @keyframes spin {
                to { transform: rotate(360deg); }
            }
            .team-stats-error .error-icon {
                font-size: 3rem;
                display: block;
                margin-bottom: 1rem;
            }
            .team-stats-tabs {
                display: flex;
                gap: 0.5rem;
                margin-bottom: 1.5rem;
                flex-wrap: wrap;
                border-bottom: 1px solid var(--border-color, #333);
                padding-bottom: 1rem;
            }
            .team-stats-tab {
                padding: 0.75rem 1.25rem;
                border: 1px solid var(--border-color, #333);
                background: var(--bg-tertiary, #16213e);
                color: var(--text-primary, #fff);
                border-radius: 0.5rem;
                cursor: pointer;
                transition: all 0.2s;
                font-size: 0.9rem;
            }
            .team-stats-tab:hover {
                background: var(--bg-secondary, #1a1a2e);
                border-color: var(--accent-blue, #3498db);
            }
            .team-stats-tab.active {
                background: var(--accent-blue, #3498db);
                border-color: var(--accent-blue, #3498db);
                color: #fff;
            }
            .stats-section {
                margin-bottom: 2rem;
            }
            .stats-section-title {
                font-size: 1.1rem;
                font-weight: 600;
                margin-bottom: 1rem;
                color: var(--text-primary, #fff);
                border-left: 3px solid var(--accent-blue, #3498db);
                padding-left: 0.75rem;
            }
            .stats-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
                gap: 1rem;
            }
            .stat-card {
                background: var(--bg-tertiary, #16213e);
                padding: 1rem;
                border-radius: 0.5rem;
                text-align: center;
                border: 1px solid var(--border-color, #333);
            }
            .stat-card .stat-value {
                font-size: 1.75rem;
                font-weight: 700;
                display: block;
                color: var(--text-primary, #fff);
            }
            .stat-card .stat-label {
                font-size: 0.75rem;
                color: var(--text-muted, #888);
                text-transform: uppercase;
                margin-top: 0.25rem;
            }
            .stat-card.stat-win .stat-value { color: var(--accent-green, #2ecc71); }
            .stat-card.stat-draw .stat-value { color: var(--accent-yellow, #f1c40f); }
            .stat-card.stat-loss .stat-value { color: var(--accent-red, #e74c3c); }
            .home-away-comparison {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 1.5rem;
            }
            .home-stats, .away-stats {
                background: var(--bg-tertiary, #16213e);
                padding: 1.25rem;
                border-radius: 0.5rem;
                border: 1px solid var(--border-color, #333);
            }
            .home-stats h4, .away-stats h4 {
                margin: 0 0 0.75rem 0;
                font-size: 1rem;
            }
            .mini-stats {
                display: flex;
                flex-direction: column;
                gap: 0.5rem;
                font-size: 0.9rem;
                color: var(--text-muted, #888);
            }
            .form-display {
                background: var(--bg-tertiary, #16213e);
                padding: 1.25rem;
                border-radius: 0.5rem;
            }
            .form-row {
                display: flex;
                align-items: center;
                gap: 1rem;
                margin-bottom: 0.75rem;
            }
            .form-label {
                font-weight: 600;
                min-width: 60px;
            }
            .form-badges {
                display: flex;
                gap: 0.25rem;
            }
            .form-badge {
                width: 28px;
                height: 28px;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 4px;
                font-weight: 700;
                font-size: 0.8rem;
            }
            .badge-win { background: var(--accent-green, #2ecc71); color: #fff; }
            .badge-draw { background: var(--accent-yellow, #f1c40f); color: #000; }
            .badge-loss { background: var(--accent-red, #e74c3c); color: #fff; }
            .form-points {
                color: var(--text-muted, #888);
                font-size: 0.85rem;
                margin-left: auto;
            }
            .half-goals {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 1rem;
            }
            .half-stat {
                background: var(--bg-tertiary, #16213e);
                padding: 1rem;
                border-radius: 0.5rem;
                text-align: center;
            }
            .half-stat h4 {
                margin: 0 0 0.5rem 0;
            }
            .goals-scored, .goals-conceded {
                display: block;
                font-size: 0.9rem;
                margin: 0.25rem 0;
            }
            .matches-table {
                width: 100%;
                border-collapse: collapse;
                background: var(--bg-tertiary, #16213e);
                border-radius: 0.5rem;
                overflow: hidden;
            }
            .matches-table th, .matches-table td {
                padding: 0.75rem 1rem;
                text-align: left;
                border-bottom: 1px solid var(--border-color, #333);
            }
            .matches-table th {
                background: var(--bg-secondary, #1a1a2e);
                font-weight: 600;
                font-size: 0.75rem;
                text-transform: uppercase;
                color: var(--text-muted, #888);
            }
            .result-badge {
                padding: 0.25rem 0.5rem;
                border-radius: 4px;
                font-weight: 700;
                font-size: 0.75rem;
            }
            .result-win { color: var(--accent-green, #2ecc71); }
            .result-draw { color: var(--accent-yellow, #f1c40f); }
            .result-loss { color: var(--accent-red, #e74c3c); }
            .result-badge.result-win { background: rgba(46, 204, 113, 0.2); }
            .result-badge.result-draw { background: rgba(241, 196, 15, 0.2); }
            .result-badge.result-loss { background: rgba(231, 76, 60, 0.2); }
            .no-data {
                text-align: center;
                color: var(--text-muted, #888);
                padding: 2rem;
                font-style: italic;
            }
            @media (max-width: 600px) {
                .team-stats-modal { width: 95%; max-height: 90vh; }
                .home-away-comparison, .half-goals { grid-template-columns: 1fr; }
                .team-stats-tabs { gap: 0.25rem; }
                .team-stats-tab { padding: 0.5rem 0.75rem; font-size: 0.8rem; }
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Render Matches view with real data
     */
    renderMatches() {
        this.mainContent.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">Matches</h2>
                <p class="page-description">Match history and upcoming fixtures</p>
            </div>
            <div id="matchesContent">
                <div class="matches-tabs" style="display: flex; gap: 0.5rem; margin-bottom: 1.5rem;">
                    <button class="btn btn-primary matches-tab active" data-tab="history">📜 Match History</button>
                    <button class="btn btn-outline matches-tab" data-tab="upcoming">📅 Upcoming</button>
                </div>
                <div class="matches-loading" style="text-align: center; padding: 3rem;">
                    <div class="loading-spinner" style="width: 40px; height: 40px; border: 3px solid var(--border-color); border-top-color: var(--accent-blue); border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 1rem;"></div>
                    <p style="color: var(--text-muted);">Loading matches...</p>
                </div>
            </div>
            <style>
                .matches-table { width: 100%; border-collapse: collapse; background: var(--bg-secondary); border-radius: 0.75rem; overflow: hidden; }
                .matches-table th, .matches-table td { padding: 1rem; text-align: left; border-bottom: 1px solid var(--border-color); }
                .matches-table th { background: var(--bg-tertiary); font-weight: 600; color: var(--text-muted); text-transform: uppercase; font-size: 0.75rem; }
                .matches-table tr:hover { background: var(--bg-tertiary); }
                .matches-table tr:last-child td { border-bottom: none; }
                .match-score { font-weight: 700; font-size: 1.125rem; }
                .match-result-H { color: var(--accent-green); }
                .match-result-A { color: var(--accent-blue); }
                .match-result-D { color: var(--accent-yellow); }
                .matches-tab { transition: all 0.2s; }
                .matches-tab.active { background: var(--accent-blue); border-color: var(--accent-blue); }
                .matches-filters { display: flex; gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap; }
                .matches-filters select, .matches-filters input { padding: 0.5rem 1rem; border-radius: 0.5rem; border: 1px solid var(--border-color); background: var(--bg-secondary); color: var(--text-primary); }
            </style>
        `;
        this.initMatchesTabs();
        this.loadMatchesHistory();
    }

    /**
     * Initialize matches tabs
     */
    initMatchesTabs() {
        const tabs = document.querySelectorAll('.matches-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', (e) => {
                tabs.forEach(t => t.classList.remove('active', 'btn-primary'));
                tabs.forEach(t => t.classList.add('btn-outline'));
                e.target.classList.remove('btn-outline');
                e.target.classList.add('active', 'btn-primary');

                const tabType = e.target.dataset.tab;
                if (tabType === 'history') {
                    this.loadMatchesHistory();
                } else if (tabType === 'upcoming') {
                    this.loadUpcomingMatches();
                }
            });
        });
    }

    /**
     * Load match history from API
     */
    async loadMatchesHistory() {
        const container = document.getElementById('matchesContent');
        if (!container) {
            console.error('[Router] matchesContent container not found!');
            return;
        }

        const loadingDiv = container.querySelector('.matches-loading');
        const existingTable = container.querySelector('.matches-table-container');

        if (existingTable) existingTable.remove();
        if (loadingDiv) loadingDiv.style.display = 'block';

        try {
            console.log('[Router] Fetching match history...');
            console.log('[Router] window.api available:', !!window.api);

            let response;
            if (window.api && typeof window.api.get === 'function') {
                response = await window.api.get('/matches/history?limit=50');
            } else {
                console.log('[Router] Falling back to native fetch for matches...');
                const fetchResponse = await fetch('/api/matches/history?limit=50');
                if (!fetchResponse.ok) {
                    throw new Error(`HTTP error! status: ${fetchResponse.status}`);
                }
                response = await fetchResponse.json();
            }

            console.log('[Router] Match history response received:', response ? 'yes' : 'no');

            const matches = response?.matches || [];
            console.log('[Router] Match count:', matches.length);

            if (loadingDiv) loadingDiv.style.display = 'none';

            if (matches.length === 0) {
                container.insertAdjacentHTML('beforeend', `
                    <div class="matches-table-container">
                        <div class="card" style="text-align: center; padding: 3rem;">
                            <div style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;">📜</div>
                            <h3>No Match History</h3>
                            <p style="color: var(--text-muted);">No historical matches found in the database.</p>
                        </div>
                    </div>
                `);
                return;
            }

            container.insertAdjacentHTML('beforeend', this.buildMatchesTableHTML(matches, 'history'));
            console.log('[Router] Match history loaded:', matches.length);

        } catch (error) {
            console.error('[Router] Failed to load match history:', error);
            if (loadingDiv) loadingDiv.style.display = 'none';
            container.insertAdjacentHTML('beforeend', `
                <div class="matches-table-container">
                    <div class="card" style="text-align: center; padding: 2rem;">
                        <div style="font-size: 3rem; margin-bottom: 1rem;">⚠️</div>
                        <h3>Unable to Load Matches</h3>
                        <p style="color: var(--text-muted); margin-bottom: 1rem;">${error.message || 'Please try again later'}</p>
                        <button class="btn btn-primary" onclick="window.router.loadMatchesHistory()">Retry</button>
                    </div>
                </div>
            `);
        }
    }

    /**
     * Load upcoming matches from API
     */
    async loadUpcomingMatches() {
        const container = document.getElementById('matchesContent');
        const loadingDiv = container?.querySelector('.matches-loading');
        const existingTable = container?.querySelector('.matches-table-container');

        if (existingTable) existingTable.remove();
        if (loadingDiv) loadingDiv.style.display = 'block';

        try {
            console.log('[Router] Fetching upcoming matches...');
            const response = await (window.api ? window.api.getUpcomingMatches(20) : fetch('/api/matches/upcoming?limit=20').then(r => r.json()));

            const matches = response?.matches || [];
            if (loadingDiv) loadingDiv.style.display = 'none';

            if (matches.length === 0) {
                container.insertAdjacentHTML('beforeend', `
                    <div class="matches-table-container">
                        <div class="card" style="text-align: center; padding: 3rem;">
                            <div style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;">📅</div>
                            <h3>No Upcoming Matches</h3>
                            <p style="color: var(--text-muted);">No scheduled matches found. Check back later!</p>
                        </div>
                    </div>
                `);
                return;
            }

            container.insertAdjacentHTML('beforeend', this.buildMatchesTableHTML(matches, 'upcoming'));
            console.log('[Router] Upcoming matches loaded:', matches.length);

        } catch (error) {
            console.error('[Router] Failed to load upcoming matches:', error);
            if (loadingDiv) loadingDiv.style.display = 'none';
            container.insertAdjacentHTML('beforeend', `
                <div class="matches-table-container">
                    <div class="card" style="text-align: center; padding: 2rem;">
                        <div style="font-size: 3rem; margin-bottom: 1rem;">⚠️</div>
                        <h3>Unable to Load Upcoming Matches</h3>
                        <p style="color: var(--text-muted); margin-bottom: 1rem;">${error.message || 'Please try again later'}</p>
                        <button class="btn btn-primary" onclick="window.router.loadUpcomingMatches()">Retry</button>
                    </div>
                </div>
            `);
        }
    }

    /**
     * Build matches table HTML
     */
    buildMatchesTableHTML(matches, type) {
        const isHistory = type === 'history';

        const rows = matches.map(match => {
            // Handle both object (TeamInfo) and string team names
            const homeTeamObj = typeof match.homeTeam === 'object' ? match.homeTeam : null;
            const awayTeamObj = typeof match.awayTeam === 'object' ? match.awayTeam : null;
            const homeTeam = homeTeamObj?.name || homeTeamObj?.shortName || match.homeTeam || match.home?.name || 'Home Team';
            const awayTeam = awayTeamObj?.name || awayTeamObj?.shortName || match.awayTeam || match.away?.name || 'Away Team';

            // Handle multiple logo property names: crest, logoUrl, homeTeamCrest
            const homeLogo = homeTeamObj?.crest || homeTeamObj?.logoUrl || match.homeTeamCrest || match.homeCrest || null;
            const awayLogo = awayTeamObj?.crest || awayTeamObj?.logoUrl || match.awayTeamCrest || match.awayCrest || null;
            const date = match.date || match.utcDate || match.matchDate || '';
            const formattedDate = this.formatMatchDate(date);

            // Create team cell with logo
            const homeTeamCell = this.createTeamCellWithLogo(homeTeam, homeLogo);
            const awayTeamCell = this.createTeamCellWithLogo(awayTeam, awayLogo);

            if (isHistory) {
                const homeGoals = match.homeGoals ?? match.score?.fullTime?.home ?? '-';
                const awayGoals = match.awayGoals ?? match.score?.fullTime?.away ?? '-';
                const result = match.result || (homeGoals > awayGoals ? 'H' : awayGoals > homeGoals ? 'A' : 'D');

                return `
                    <tr>
                        <td>${formattedDate}</td>
                        <td>${homeTeamCell}</td>
                        <td class="match-score match-result-${result}">${homeGoals} - ${awayGoals}</td>
                        <td>${awayTeamCell}</td>
                        <td>
                            <button class="btn btn-primary btn-sm" onclick="window.router.predictMatch('${this.escapeHtml(homeTeam)}', '${this.escapeHtml(awayTeam)}')">
                                Predict Similar
                            </button>
                        </td>
                    </tr>
                `;
            } else {
                const time = match.time || (date ? new Date(date).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }) : 'TBD');
                const competition = match.competition?.name || 'Premier League';

                return `
                    <tr>
                        <td>${formattedDate}</td>
                        <td>${time}</td>
                        <td>${homeTeamCell}</td>
                        <td style="text-align: center;">vs</td>
                        <td>${awayTeamCell}</td>
                        <td>
                            <button class="btn btn-primary btn-sm" onclick="window.router.predictMatch('${this.escapeHtml(homeTeam)}', '${this.escapeHtml(awayTeam)}')">
                                Predict
                            </button>
                        </td>
                    </tr>
                `;
            }
        }).join('');

        const headers = isHistory
            ? '<th>Date</th><th>Home</th><th>Score</th><th>Away</th><th>Action</th>'
            : '<th>Date</th><th>Time</th><th>Home</th><th></th><th>Away</th><th>Action</th>';

        return `
            <div class="matches-table-container" style="overflow-x: auto;">
                <table class="matches-table">
                    <thead><tr>${headers}</tr></thead>
                    <tbody>${rows}</tbody>
                </table>
                <div style="text-align: center; margin-top: 1rem; color: var(--text-muted); font-size: 0.875rem;">
                    Showing ${matches.length} ${isHistory ? 'past' : 'upcoming'} matches
                </div>
            </div>
        `;
    }

    /**
     * Create a team cell with logo and name for table display
     * @param {string} teamName - The team name
     * @param {string|null} logoUrl - Optional explicit logo URL
     * @returns {string} HTML string for team cell
     */
    createTeamCellWithLogo(teamName, logoUrl) {
        const escapedName = this.escapeHtml(teamName);

        // Use TeamLogos utility if available
        if (window.TeamLogos && window.TeamLogos.createTeamCellHTML) {
            return window.TeamLogos.createTeamCellHTML(teamName, logoUrl);
        }

        // Fallback implementation
        const logoSrc = logoUrl || (window.TeamLogos?.getLogoUrl?.(teamName)) || 'https://cdn-icons-png.flaticon.com/512/861/861512.png';
        return `
            <div class="team-cell">
                <div class="team-logo team-logo--sm">
                    <img src="${logoSrc}"
                         alt="${escapedName} logo"
                         crossorigin="anonymous"
                         onerror="this.onerror=null; this.removeAttribute('crossorigin'); this.src='https://cdn-icons-png.flaticon.com/512/861/861512.png';"
                         loading="lazy">
                </div>
                <span class="team-name" style="font-weight: 500;">${escapedName}</span>
            </div>
        `;
    }

    /**
     * Format match date
     */
    formatMatchDate(dateString) {
        if (!dateString) return 'Unknown';
        try {
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return dateString;
            return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
        } catch {
            return dateString;
        }
    }

    /**
     * Navigate to predictions with pre-filled teams
     */
    predictMatch(homeTeam, awayTeam) {
        sessionStorage.setItem('predictHomeTeam', homeTeam);
        sessionStorage.setItem('predictAwayTeam', awayTeam);
        window.location.hash = '#predictions';
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }


    /**
     * Render Betting Dashboard view
     */

    /**
     * Render Admin Dashboard view
     */
    renderAdmin() {
        this.mainContent.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">Admin Control Panel</h2>
                <p class="page-description">System administration and configuration</p>
            </div>
            <div id="adminDashboardContent"></div>
        `;

        // Initialize admin dashboard
        if (window.adminManager) {
            const container = document.getElementById('adminDashboardContent');
            window.adminManager.renderDashboard(container);
        } else {
            document.getElementById('adminDashboardContent').innerHTML = `
                <div class="admin-error">
                    <div class="admin-error-icon">⚠️</div>
                    <p class="admin-error-message">Admin module not loaded</p>
                </div>
            `;
        }
    }

    /**
     * Render placeholder view
     */
    renderPlaceholder(title, icon, description) {
        this.mainContent.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">${title}</h2>
                <p class="page-description">${description}</p>
            </div>
            <div class="card" style="max-width: 600px; margin: 2rem auto;">
                <div class="card-body text-center" style="padding: 3rem;">
                    <div style="font-size: 4rem; margin-bottom: 1rem;">${icon}</div>
                    <h3 style="margin-bottom: 1rem;">${title}</h3>
                    <p style="color: var(--text-muted); margin-bottom: 2rem;">${description}</p>
                    <button class="btn btn-outline" onclick="window.location.hash='#dashboard'">
                        Back to Dashboard
                    </button>
                </div>
            </div>
        `;
    }
}

// Initialize router when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.router = new Router();
        console.log('Router initialized');
    });
} else {
    window.router = new Router();
    console.log('Router initialized');
}


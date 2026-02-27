/**
 * Half Analysis Card Component
 * =============================
 *
 * Renders a comprehensive first half vs second half performance analysis card.
 *
 * Features:
 * - Split layout showing First Half / Second Half statistics
 * - SVG donut chart for goal distribution visualization
 * - Win rate horizontal bars based on HT position
 * - Pattern badge (Fast Starter / Strong Finisher / Balanced)
 * - Animated transitions
 * - Defensive rendering for missing data
 *
 * Usage:
 *   window.HalfAnalysisCard.render(container, data)
 *   window.HalfAnalysisCard.fetchAndRender(container, teamName)
 *   window.HalfAnalysisCard.renderLoading(container)
 *   window.HalfAnalysisCard.renderError(container, message, retryFn)
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */

(function() {
    'use strict';

    // ══════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * API base path for half analysis endpoint.
     */
    var API_BASE = '/api/teams';

    /**
     * Colors for halves.
     */
    var COLORS = {
        FIRST_HALF: '#3b82f6',
        SECOND_HALF: '#ef4444',
        BALANCED: '#6b7280',
        LEADING: '#22c55e',
        DRAWING: '#fbbf24',
        LOSING: '#ef4444',
        COMEBACK: '#8b5cf6'
    };

    /**
     * Pattern display names.
     */
    var PATTERN_LABELS = {
        'Fast Starter': { icon: '🚀', color: COLORS.FIRST_HALF },
        'Strong Finisher': { icon: '💪', color: COLORS.SECOND_HALF },
        'Balanced': { icon: '⚖️', color: COLORS.BALANCED }
    };

    /**
     * Stronger half badge classes.
     */
    var STRONGER_HALF_CLASSES = {
        'First Half': 'half-analysis-card__badge--first-half',
        'Second Half': 'half-analysis-card__badge--second-half',
        'Balanced': 'half-analysis-card__badge--balanced'
    };

    // ══════════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Escape HTML to prevent XSS.
     * @param {*} str - Input string
     * @returns {string} Escaped string
     */
    function escapeHtml(str) {
        if (str == null) return '';
        var div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    /**
     * Format a number with specified decimals.
     * @param {number} value - Number to format
     * @param {number} decimals - Decimal places (default: 1)
     * @returns {string} Formatted number
     */
    function formatNumber(value, decimals) {
        decimals = decimals !== undefined ? decimals : 1;
        if (value == null || isNaN(value)) return '0';
        return Number(value).toFixed(decimals);
    }

    /**
     * Format percentage value.
     * @param {number} value - Percentage value
     * @returns {string} Formatted percentage
     */
    function formatPercent(value) {
        if (value == null || isNaN(value)) return '0%';
        return formatNumber(value, 1) + '%';
    }

    /**
     * Safely get a nested property.
     * @param {Object} obj - Object to access
     * @param {string} path - Property path (e.g., 'a.b.c')
     * @param {*} defaultVal - Default value if not found
     * @returns {*} Property value or default
     */
    function safeGet(obj, path, defaultVal) {
        if (defaultVal === undefined) defaultVal = null;
        if (!obj) return defaultVal;
        var parts = path.split('.');
        var current = obj;
        for (var i = 0; i < parts.length; i++) {
            if (current == null || typeof current !== 'object') return defaultVal;
            current = current[parts[i]];
        }
        return current !== undefined && current !== null ? current : defaultVal;
    }

    // ══════════════════════════════════════════════════════════════════════
    // SVG DONUT CHART
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Create SVG donut chart for goal distribution.
     * @param {number} firstHalfPct - First half percentage (0-100)
     * @param {number} secondHalfPct - Second half percentage (0-100)
     * @returns {string} SVG HTML string
     */
    function createDonutChart(firstHalfPct, secondHalfPct) {
        // Normalize percentages
        firstHalfPct = Math.max(0, Math.min(100, firstHalfPct || 50));
        secondHalfPct = Math.max(0, Math.min(100, secondHalfPct || 50));

        // SVG parameters
        var size = 160;
        var strokeWidth = 20;
        var radius = (size - strokeWidth) / 2;
        var center = size / 2;
        var circumference = 2 * Math.PI * radius;

        // Calculate stroke dash arrays
        var firstHalfLength = (firstHalfPct / 100) * circumference;
        var secondHalfLength = (secondHalfPct / 100) * circumference;

        // First half starts at top (after rotation)
        var firstHalfDasharray = firstHalfLength + ' ' + (circumference - firstHalfLength);

        // Second half starts where first ends
        var secondHalfDashoffset = -firstHalfLength;
        var secondHalfDasharray = secondHalfLength + ' ' + (circumference - secondHalfLength);

        return '\
            <div class="half-analysis-chart">\
                <svg class="half-analysis-chart__svg" viewBox="0 0 ' + size + ' ' + size + '">\
                    <circle \
                        class="half-analysis-chart__track" \
                        cx="' + center + '" \
                        cy="' + center + '" \
                        r="' + radius + '" \
                    />\
                    <circle \
                        class="half-analysis-chart__first-half" \
                        cx="' + center + '" \
                        cy="' + center + '" \
                        r="' + radius + '" \
                        stroke-dasharray="' + firstHalfDasharray + '" \
                        stroke-dashoffset="0" \
                    />\
                    <circle \
                        class="half-analysis-chart__second-half" \
                        cx="' + center + '" \
                        cy="' + center + '" \
                        r="' + radius + '" \
                        stroke-dasharray="' + secondHalfDasharray + '" \
                        stroke-dashoffset="' + secondHalfDashoffset + '" \
                    />\
                </svg>\
                <div class="half-analysis-chart__center">\
                    <div class="half-analysis-chart__center-icon">⏱️</div>\
                    <div class="half-analysis-chart__center-label">Goals</div>\
                </div>\
            </div>';
    }

    // ══════════════════════════════════════════════════════════════════════
    // WIN RATE BAR
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Create a win rate bar item.
     * @param {string} label - Bar label
     * @param {string} icon - Emoji icon
     * @param {number} value - Percentage value (0-100)
     * @param {string} fillClass - CSS class for fill color
     * @param {number} count - Number of matches in category
     * @returns {string} HTML string
     */
    function createWinRateBar(label, icon, value, fillClass, count) {
        value = Math.max(0, Math.min(100, value || 0));
        count = count || 0;

        var countText = count > 0 ? ' (' + count + ')' : '';

        return '\
            <div class="half-analysis-card__win-rate-item">\
                <div class="half-analysis-card__win-rate-header">\
                    <span class="half-analysis-card__win-rate-label">\
                        <span>' + icon + '</span>\
                        <span>' + escapeHtml(label) + countText + '</span>\
                    </span>\
                    <span class="half-analysis-card__win-rate-value">' + formatPercent(value) + '</span>\
                </div>\
                <div class="half-analysis-card__win-rate-bar">\
                    <div class="half-analysis-card__win-rate-fill ' + fillClass + '" style="width: ' + value + '%;"></div>\
                </div>\
            </div>';
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render the half analysis card.
     * @param {HTMLElement} container - Container element
     * @param {Object} data - Half analysis data from API
     */
    function render(container, data) {
        if (!container) {
            console.error('[HalfAnalysisCard] Container not provided');
            return;
        }

        // Defensive: build safe data object
        var safeData = {
            teamName: safeGet(data, 'teamName', 'Unknown Team'),
            dataScope: safeGet(data, 'dataScope', 'Recent Matches'),
            matchesAnalyzed: Number(safeGet(data, 'matchesAnalyzed', 0)),
            firstHalfGoalsAvg: Number(safeGet(data, 'firstHalfGoalsAvg', 0)),
            secondHalfGoalsAvg: Number(safeGet(data, 'secondHalfGoalsAvg', 0)),
            totalFirstHalfGoals: Number(safeGet(data, 'totalFirstHalfGoals', 0)),
            totalSecondHalfGoals: Number(safeGet(data, 'totalSecondHalfGoals', 0)),
            totalGoals: Number(safeGet(data, 'totalGoals', 0)),
            firstHalfPercentage: Number(safeGet(data, 'firstHalfPercentage', 50)),
            secondHalfPercentage: Number(safeGet(data, 'secondHalfPercentage', 50)),
            strongerHalf: safeGet(data, 'strongerHalf', 'Balanced'),
            pattern: safeGet(data, 'pattern', 'Balanced'),
            winRateWhenLeadingHT: Number(safeGet(data, 'winRateWhenLeadingHT', 0)),
            winRateWhenDrawingHT: Number(safeGet(data, 'winRateWhenDrawingHT', 0)),
            winRateWhenLosingHT: Number(safeGet(data, 'winRateWhenLosingHT', 0)),
            comebackRate: Number(safeGet(data, 'comebackRate', 0)),
            matchesLeadingHT: Number(safeGet(data, 'matchesLeadingHT', 0)),
            matchesDrawingHT: Number(safeGet(data, 'matchesDrawingHT', 0)),
            matchesTrailingHT: Number(safeGet(data, 'matchesTrailingHT', 0)),
            firstHalfConcededAvg: Number(safeGet(data, 'firstHalfConcededAvg', 0)),
            secondHalfConcededAvg: Number(safeGet(data, 'secondHalfConcededAvg', 0)),
            confidence: Number(safeGet(data, 'confidence', 0)),
            anomalyDetected: Boolean(safeGet(data, 'anomalyDetected', false)),
            anomalyDescription: safeGet(data, 'anomalyDescription', null)
        };

        // Calculate total goals if not provided
        if (!safeData.totalGoals) {
            safeData.totalGoals = safeData.totalFirstHalfGoals + safeData.totalSecondHalfGoals;
        }

        // Build badge class
        var badgeClass = STRONGER_HALF_CLASSES[safeData.strongerHalf] || STRONGER_HALF_CLASSES['Balanced'];

        // Build pattern info
        var patternInfo = PATTERN_LABELS[safeData.pattern] || PATTERN_LABELS['Balanced'];
        var patternClass = 'half-analysis-card__pattern--' + safeData.pattern.toLowerCase().replace(/\s+/g, '-');

        // Build HTML
        var html = '\
            <div class="half-analysis-card">\
                <!-- Header -->\
                <div class="half-analysis-card__header">\
                    <div class="half-analysis-card__team-info">\
                        <h3 class="half-analysis-card__team-name">' + escapeHtml(safeData.teamName) + '</h3>\
                        <span class="half-analysis-card__data-scope">' + escapeHtml(safeData.dataScope) + '</span>\
                    </div>\
                    <div class="half-analysis-card__badge ' + badgeClass + '">\
                        <span>⏱️</span>\
                        <span>' + escapeHtml(safeData.strongerHalf) + '</span>\
                    </div>\
                </div>\
                \
                <!-- Donut Chart -->\
                <div class="half-analysis-card__chart-container">\
                    ' + createDonutChart(safeData.firstHalfPercentage, safeData.secondHalfPercentage) + '\
                </div>\
                \
                <!-- Split Layout -->\
                <div class="half-analysis-card__split-container">\
                    <div class="half-analysis-card__half half-analysis-card__half--first">\
                        <div class="half-analysis-card__half-header">\
                            <span class="half-analysis-card__half-icon">1️⃣</span>\
                            <span class="half-analysis-card__half-title">First Half</span>\
                        </div>\
                        <div class="half-analysis-card__half-percentage">' + formatPercent(safeData.firstHalfPercentage) + '</div>\
                        <div class="half-analysis-card__half-avg">\
                            <strong>' + formatNumber(safeData.firstHalfGoalsAvg, 2) + '</strong> goals/match\
                        </div>\
                    </div>\
                    <div class="half-analysis-card__half half-analysis-card__half--second">\
                        <div class="half-analysis-card__half-header">\
                            <span class="half-analysis-card__half-icon">2️⃣</span>\
                            <span class="half-analysis-card__half-title">Second Half</span>\
                        </div>\
                        <div class="half-analysis-card__half-percentage">' + formatPercent(safeData.secondHalfPercentage) + '</div>\
                        <div class="half-analysis-card__half-avg">\
                            <strong>' + formatNumber(safeData.secondHalfGoalsAvg, 2) + '</strong> goals/match\
                        </div>\
                    </div>\
                </div>\
                \
                <!-- Win Rate Bars -->\
                <div class="half-analysis-card__win-rates">\
                    <div class="half-analysis-card__win-rates-title">\
                        <span>📊</span>\
                        <span>Win Rates by HT Position</span>\
                    </div>\
                    ' + createWinRateBar('Leading at HT', '✅', safeData.winRateWhenLeadingHT, 'half-analysis-card__win-rate-fill--leading', safeData.matchesLeadingHT) + '\
                    ' + createWinRateBar('Drawing at HT', '🟡', safeData.winRateWhenDrawingHT, 'half-analysis-card__win-rate-fill--drawing', safeData.matchesDrawingHT) + '\
                    ' + createWinRateBar('Losing at HT', '❌', safeData.winRateWhenLosingHT, 'half-analysis-card__win-rate-fill--losing', safeData.matchesTrailingHT) + '\
                    ' + createWinRateBar('Comeback Rate', '🔄', safeData.comebackRate, 'half-analysis-card__win-rate-fill--comeback', safeData.matchesTrailingHT) + '\
                </div>\
                \
                <!-- Pattern Badge -->\
                <div class="half-analysis-card__pattern-container">\
                    <div class="half-analysis-card__pattern ' + patternClass + '">\
                        <span>' + patternInfo.icon + '</span>\
                        <span>' + escapeHtml(safeData.pattern) + '</span>\
                    </div>\
                </div>';

        // Add anomaly warning if detected
        if (safeData.anomalyDetected && safeData.anomalyDescription) {
            html += '\
                <div class="half-analysis-card__anomaly">\
                    <span class="half-analysis-card__anomaly-icon">⚠️</span>\
                    <span class="half-analysis-card__anomaly-text">' + escapeHtml(safeData.anomalyDescription) + '</span>\
                </div>';
        }

        html += '</div>';

        container.innerHTML = html;
    }

    /**
     * Render loading state.
     * @param {HTMLElement} container - Container element
     */
    function renderLoading(container) {
        if (!container) return;

        container.innerHTML = '\
            <div class="half-analysis-card half-analysis-card--loading">\
                <div class="half-analysis-card__loader">\
                    <div class="half-analysis-card__spinner"></div>\
                    <div class="half-analysis-card__loading-text">Loading half analysis...</div>\
                </div>\
            </div>';
    }

    /**
     * Render error state.
     * @param {HTMLElement} container - Container element
     * @param {string} message - Error message
     * @param {Function} retryFn - Retry callback function
     */
    function renderError(container, message, retryFn) {
        if (!container) return;

        var html = '\
            <div class="half-analysis-card half-analysis-card--error">\
                <div class="half-analysis-card__error">\
                    <div class="half-analysis-card__error-icon">⚠️</div>\
                    <h4 class="half-analysis-card__error-title">Unable to Load Data</h4>\
                    <p class="half-analysis-card__error-message">' + escapeHtml(message || 'An error occurred') + '</p>';

        if (typeof retryFn === 'function') {
            html += '<button class="half-analysis-card__retry-btn" type="button">Try Again</button>';
        }

        html += '\
                </div>\
            </div>';

        container.innerHTML = html;

        // Attach retry handler
        if (typeof retryFn === 'function') {
            var retryBtn = container.querySelector('.half-analysis-card__retry-btn');
            if (retryBtn) {
                retryBtn.addEventListener('click', retryFn);
            }
        }
    }

    /**
     * Fetch data and render the card.
     * @param {HTMLElement} container - Container element
     * @param {string} teamName - Team name to fetch analysis for
     * @returns {Promise} Promise that resolves when render is complete
     */
    function fetchAndRender(container, teamName) {
        if (!container) {
            console.error('[HalfAnalysisCard] Container not provided');
            return Promise.reject(new Error('Container not provided'));
        }

        if (!teamName) {
            renderError(container, 'Team name is required');
            return Promise.reject(new Error('Team name is required'));
        }

        // Show loading state
        renderLoading(container);

        var url = API_BASE + '/' + encodeURIComponent(teamName) + '/half-analysis';

        return fetch(url)
            .then(function(response) {
                if (!response.ok) {
                    if (response.status === 404) {
                        throw new Error('No data found for team: ' + teamName);
                    }
                    throw new Error('Failed to load half analysis (HTTP ' + response.status + ')');
                }
                return response.json();
            })
            .then(function(data) {
                render(container, data);
                return data;
            })
            .catch(function(error) {
                console.error('[HalfAnalysisCard] Error:', error);
                renderError(container, error.message, function() {
                    fetchAndRender(container, teamName);
                });
                throw error;
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════════════════

    window.HalfAnalysisCard = {
        render: render,
        renderLoading: renderLoading,
        renderError: renderError,
        fetchAndRender: fetchAndRender
    };

})();


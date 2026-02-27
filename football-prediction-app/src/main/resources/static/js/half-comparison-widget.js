/**
 * Half Comparison Widget Component
 * ==================================
 *
 * Compares first half vs second half performance between two teams.
 *
 * Features:
 * - Side-by-side comparison of both teams' half statistics
 * - Pattern comparison (Fast Starter vs Strong Finisher)
 * - HT/FT outcome prediction logic
 * - Animated gradient transitions
 * - Defensive rendering for missing data
 *
 * Usage:
 *   window.HalfComparisonWidget.render(container, homeData, awayData)
 *   window.HalfComparisonWidget.fetchAndRender(container, homeTeam, awayTeam)
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
     * Pattern identifiers.
     */
    var PATTERNS = {
        FAST_STARTER: 'Fast Starter',
        STRONG_FINISHER: 'Strong Finisher',
        BALANCED: 'Balanced'
    };

    /**
     * Pattern icons.
     */
    var PATTERN_ICONS = {
        'Fast Starter': '🚀',
        'Strong Finisher': '💪',
        'Balanced': '⚖️'
    };

    // ══════════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Escape HTML to prevent XSS.
     */
    function escapeHtml(str) {
        if (str == null) return '';
        var div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    /**
     * Format a number with specified decimals.
     */
    function formatNumber(value, decimals) {
        decimals = decimals !== undefined ? decimals : 1;
        if (value == null || isNaN(value)) return '0';
        return Number(value).toFixed(decimals);
    }

    /**
     * Format percentage value.
     */
    function formatPercent(value) {
        if (value == null || isNaN(value)) return '0%';
        return formatNumber(value, 1) + '%';
    }

    /**
     * Safely get a nested property.
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
    // PREDICTION LOGIC
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Generate HT/FT outcome prediction based on both teams' half performance.
     *
     * Logic:
     * - Both strong 1H → Likely early goals
     * - Both strong 2H → Likely late goals
     * - Fast starter vs slow starter → HT advantage predicted
     *
     * @param {Object} homeData - Home team half analysis
     * @param {Object} awayData - Away team half analysis
     * @returns {Object} Prediction with text and confidence
     */
    function generatePrediction(homeData, awayData) {
        var homePattern = safeGet(homeData, 'pattern', 'Balanced');
        var awayPattern = safeGet(awayData, 'pattern', 'Balanced');
        var homeFirst = Number(safeGet(homeData, 'firstHalfPercentage', 50));
        var awayFirst = Number(safeGet(awayData, 'firstHalfPercentage', 50));
        var homeSecond = Number(safeGet(homeData, 'secondHalfPercentage', 50));
        var awaySecond = Number(safeGet(awayData, 'secondHalfPercentage', 50));
        var homeComebackRate = Number(safeGet(homeData, 'comebackRate', 0));
        var awayComebackRate = Number(safeGet(awayData, 'comebackRate', 0));

        var prediction = {
            text: '',
            confidence: 'Medium',
            icon: '🎯'
        };

        // Both are fast starters
        if (homePattern === PATTERNS.FAST_STARTER && awayPattern === PATTERNS.FAST_STARTER) {
            prediction.text = 'High probability of <strong>early breakthrough</strong>. Both teams score majority of goals in first half. Expect an eventful opening 45 minutes.';
            prediction.confidence = 'High';
            prediction.icon = '⚡';
        }
        // Both are strong finishers
        else if (homePattern === PATTERNS.STRONG_FINISHER && awayPattern === PATTERNS.STRONG_FINISHER) {
            prediction.text = 'Likely <strong>0-0 or low-scoring HT</strong>, decided in second half. Both teams historically peak after the break.';
            prediction.confidence = 'High';
            prediction.icon = '🔥';
        }
        // Home fast starter vs Away strong finisher
        else if (homePattern === PATTERNS.FAST_STARTER && awayPattern === PATTERNS.STRONG_FINISHER) {
            prediction.text = 'Home team may lead at HT due to fast start. Away team could <strong>come back in second half</strong>. Consider HT/FT market.';
            prediction.confidence = 'Medium';
            prediction.icon = '🎢';
        }
        // Home strong finisher vs Away fast starter
        else if (homePattern === PATTERNS.STRONG_FINISHER && awayPattern === PATTERNS.FAST_STARTER) {
            prediction.text = 'Away team may grab early lead. Home team typically <strong>finishes stronger</strong>. Late equalizers possible.';
            prediction.confidence = 'Medium';
            prediction.icon = '🎢';
        }
        // Home fast starter, Away balanced
        else if (homePattern === PATTERNS.FAST_STARTER && awayPattern === PATTERNS.BALANCED) {
            prediction.text = 'Home team scores early (' + formatNumber(homeFirst, 0) + '% 1H goals). <strong>Home lead at HT</strong> is likely.';
            prediction.confidence = 'Medium';
            prediction.icon = '🏠';
        }
        // Away fast starter, Home balanced
        else if (awayPattern === PATTERNS.FAST_STARTER && homePattern === PATTERNS.BALANCED) {
            prediction.text = 'Away team starts quickly (' + formatNumber(awayFirst, 0) + '% 1H goals). May <strong>steal early lead</strong>.';
            prediction.confidence = 'Medium';
            prediction.icon = '✈️';
        }
        // Home strong finisher, Away balanced
        else if (homePattern === PATTERNS.STRONG_FINISHER && awayPattern === PATTERNS.BALANCED) {
            prediction.text = 'Home team dangerous after the break (' + formatNumber(homeSecond, 0) + '% 2H goals). <strong>Late home surge</strong> expected.';
            prediction.confidence = 'Medium';
            prediction.icon = '⏰';
        }
        // Away strong finisher, Home balanced
        else if (awayPattern === PATTERNS.STRONG_FINISHER && homePattern === PATTERNS.BALANCED) {
            prediction.text = 'Away team finishes strong (' + formatNumber(awaySecond, 0) + '% 2H goals). Beware of <strong>late away goals</strong>.';
            prediction.confidence = 'Medium';
            prediction.icon = '⏰';
        }
        // Both balanced
        else {
            prediction.text = 'Both teams evenly distribute goals across halves. <strong>No clear timing advantage</strong>. Match could swing either way.';
            prediction.confidence = 'Low';
            prediction.icon = '⚖️';
        }

        // Add comeback insight if notable
        if (homeComebackRate > 20 || awayComebackRate > 20) {
            var comebackTeam = homeComebackRate > awayComebackRate ? safeGet(homeData, 'teamName', 'Home') : safeGet(awayData, 'teamName', 'Away');
            var comebackPct = Math.max(homeComebackRate, awayComebackRate);
            prediction.text += ' <em>' + escapeHtml(comebackTeam) + ' has notable comeback ability (' + formatNumber(comebackPct, 0) + '%).</em>';
        }

        return prediction;
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Create a comparison row.
     */
    function createComparisonRow(label, homeValue, awayValue, homeName, awayName, format) {
        format = format || 'percent';
        var homeDisplay = format === 'percent' ? formatPercent(homeValue) : formatNumber(homeValue, 1);
        var awayDisplay = format === 'percent' ? formatPercent(awayValue) : formatNumber(awayValue, 1);

        return '\
            <div class="half-comparison-widget__comparison">\
                <div class="half-comparison-widget__team-value half-comparison-widget__team-value--home">\
                    <div class="half-comparison-widget__value">' + homeDisplay + '</div>\
                    <div class="half-comparison-widget__team-name">' + escapeHtml(homeName) + '</div>\
                </div>\
                <div class="half-comparison-widget__metric-label">' + escapeHtml(label) + '</div>\
                <div class="half-comparison-widget__team-value half-comparison-widget__team-value--away">\
                    <div class="half-comparison-widget__value">' + awayDisplay + '</div>\
                    <div class="half-comparison-widget__team-name">' + escapeHtml(awayName) + '</div>\
                </div>\
            </div>';
    }

    /**
     * Create pattern comparison row.
     */
    function createPatternRow(homePattern, awayPattern, homeName, awayName) {
        var homeIcon = PATTERN_ICONS[homePattern] || '⚖️';
        var awayIcon = PATTERN_ICONS[awayPattern] || '⚖️';

        return '\
            <div class="half-comparison-widget__comparison">\
                <div class="half-comparison-widget__team-value half-comparison-widget__team-value--home">\
                    <div class="half-comparison-widget__value">' + homeIcon + ' ' + escapeHtml(homePattern) + '</div>\
                    <div class="half-comparison-widget__team-name">' + escapeHtml(homeName) + '</div>\
                </div>\
                <div class="half-comparison-widget__metric-label">Pattern</div>\
                <div class="half-comparison-widget__team-value half-comparison-widget__team-value--away">\
                    <div class="half-comparison-widget__value">' + awayIcon + ' ' + escapeHtml(awayPattern) + '</div>\
                    <div class="half-comparison-widget__team-name">' + escapeHtml(awayName) + '</div>\
                </div>\
            </div>';
    }

    /**
     * Render the half comparison widget.
     * @param {HTMLElement} container - Container element
     * @param {Object} homeData - Home team half analysis data
     * @param {Object} awayData - Away team half analysis data
     */
    function render(container, homeData, awayData) {
        if (!container) {
            console.error('[HalfComparisonWidget] Container not provided');
            return;
        }

        // Extract safe data
        var homeName = safeGet(homeData, 'teamName', 'Home Team');
        var awayName = safeGet(awayData, 'teamName', 'Away Team');
        var homeFirst = Number(safeGet(homeData, 'firstHalfPercentage', 50));
        var awayFirst = Number(safeGet(awayData, 'firstHalfPercentage', 50));
        var homeSecond = Number(safeGet(homeData, 'secondHalfPercentage', 50));
        var awaySecond = Number(safeGet(awayData, 'secondHalfPercentage', 50));
        var homePattern = safeGet(homeData, 'pattern', 'Balanced');
        var awayPattern = safeGet(awayData, 'pattern', 'Balanced');
        var homeComeback = Number(safeGet(homeData, 'comebackRate', 0));
        var awayComeback = Number(safeGet(awayData, 'comebackRate', 0));

        // Generate prediction
        var prediction = generatePrediction(homeData, awayData);

        // Build HTML
        var html = '\
            <div class="half-comparison-widget">\
                <div class="half-comparison-widget__header">\
                    <h3 class="half-comparison-widget__title">\
                        <span>⏱️</span>\
                        <span>Half Performance Comparison</span>\
                    </h3>\
                </div>\
                \
                <!-- Comparison Rows -->\
                ' + createComparisonRow('1st Half %', homeFirst, awayFirst, homeName, awayName) + '\
                ' + createComparisonRow('2nd Half %', homeSecond, awaySecond, homeName, awayName) + '\
                ' + createPatternRow(homePattern, awayPattern, homeName, awayName) + '\
                ' + createComparisonRow('Comeback %', homeComeback, awayComeback, homeName, awayName) + '\
                \
                <!-- Prediction -->\
                <div class="half-comparison-widget__prediction">\
                    <div class="half-comparison-widget__prediction-header">\
                        <span class="half-comparison-widget__prediction-icon">' + prediction.icon + '</span>\
                        <span class="half-comparison-widget__prediction-title">HT/FT Insight</span>\
                    </div>\
                    <p class="half-comparison-widget__prediction-text">' + prediction.text + '</p>\
                </div>\
            </div>';

        container.innerHTML = html;
    }

    /**
     * Render loading state.
     */
    function renderLoading(container) {
        if (!container) return;

        container.innerHTML = '\
            <div class="half-comparison-widget">\
                <div class="half-comparison-widget__header">\
                    <h3 class="half-comparison-widget__title">\
                        <span>⏱️</span>\
                        <span>Half Performance Comparison</span>\
                    </h3>\
                </div>\
                <div style="text-align: center; padding: 2rem;">\
                    <div class="half-analysis-card__spinner"></div>\
                    <div style="color: var(--text-muted, #64748b); font-size: 0.875rem;">Loading comparison...</div>\
                </div>\
            </div>';
    }

    /**
     * Render error state.
     */
    function renderError(container, message, retryFn) {
        if (!container) return;

        var html = '\
            <div class="half-comparison-widget">\
                <div class="half-comparison-widget__header">\
                    <h3 class="half-comparison-widget__title">\
                        <span>⏱️</span>\
                        <span>Half Performance Comparison</span>\
                    </h3>\
                </div>\
                <div style="text-align: center; padding: 2rem;">\
                    <div style="font-size: 2rem; margin-bottom: 0.5rem;">⚠️</div>\
                    <div style="color: var(--text-muted, #64748b); font-size: 0.875rem;">' + escapeHtml(message || 'Unable to load comparison') + '</div>';

        if (typeof retryFn === 'function') {
            html += '<button class="half-analysis-card__retry-btn" type="button" style="margin-top: 1rem;">Try Again</button>';
        }

        html += '</div></div>';

        container.innerHTML = html;

        if (typeof retryFn === 'function') {
            var btn = container.querySelector('.half-analysis-card__retry-btn');
            if (btn) btn.addEventListener('click', retryFn);
        }
    }

    /**
     * Fetch data for both teams and render comparison.
     * @param {HTMLElement} container - Container element
     * @param {string} homeTeam - Home team name
     * @param {string} awayTeam - Away team name
     * @returns {Promise} Promise that resolves when render is complete
     */
    function fetchAndRender(container, homeTeam, awayTeam) {
        if (!container) {
            return Promise.reject(new Error('Container not provided'));
        }

        if (!homeTeam || !awayTeam) {
            renderError(container, 'Both team names are required');
            return Promise.reject(new Error('Team names are required'));
        }

        renderLoading(container);

        var homeUrl = API_BASE + '/' + encodeURIComponent(homeTeam) + '/half-analysis';
        var awayUrl = API_BASE + '/' + encodeURIComponent(awayTeam) + '/half-analysis';

        return Promise.all([
            fetch(homeUrl).then(function(r) { return r.ok ? r.json() : Promise.reject('Home team not found'); }),
            fetch(awayUrl).then(function(r) { return r.ok ? r.json() : Promise.reject('Away team not found'); })
        ])
        .then(function(results) {
            render(container, results[0], results[1]);
            return { home: results[0], away: results[1] };
        })
        .catch(function(error) {
            console.error('[HalfComparisonWidget] Error:', error);
            renderError(container, String(error), function() {
                fetchAndRender(container, homeTeam, awayTeam);
            });
            throw error;
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════════════════

    window.HalfComparisonWidget = {
        render: render,
        renderLoading: renderLoading,
        renderError: renderError,
        fetchAndRender: fetchAndRender,
        generatePrediction: generatePrediction
    };

})();


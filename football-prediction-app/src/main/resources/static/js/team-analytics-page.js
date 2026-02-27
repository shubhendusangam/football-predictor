/**
 * Team Analytics Page Module
 * ===========================
 *
 * Handles the team analytics page including:
 * - Half Analysis Card (First Half vs Second Half)
 * - Integration with other analytics components
 * - Loading, error, and retry states
 *
 * Usage:
 *   Include this script in the team analytics page
 *   Call: window.TeamAnalyticsPage.init(teamName)
 *
 * Dependencies:
 *   - half-analysis-card.js (HalfAnalysisCard)
 *   - half-analysis.css
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */

(function() {
    'use strict';

    // ══════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════

    var RETRY_MAX_ATTEMPTS = 3;
    var RETRY_DELAY_MS = 1000;

    // ══════════════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════════════

    var state = {
        currentTeam: null,
        isLoading: false,
        retryCount: 0,
        container: null
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
     * Wait for specified milliseconds.
     */
    function delay(ms) {
        return new Promise(function(resolve) {
            setTimeout(resolve, ms);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render the page structure.
     */
    function renderPageStructure(teamName) {
        return '\
            <div class="team-analytics-page">\
                <div class="team-analytics-page__header">\
                    <h2 class="team-analytics-page__title">📊 Team Analytics</h2>\
                    <p class="team-analytics-page__subtitle">' + escapeHtml(teamName) + '</p>\
                </div>\
                \
                <!-- Half Analysis Section -->\
                <section class="half-analysis-section">\
                    <div class="section-header">\
                        <span class="header-icon">⏱️</span>\
                        <h3 class="header-title">First Half vs Second Half Performance</h3>\
                    </div>\
                    <div id="half-analysis-card-container"></div>\
                </section>\
                \
                <!-- Additional Sections Can Be Added Here -->\
                \
                <div class="team-analytics-page__footer">\
                    <p class="team-analytics-page__note">\
                        Analysis based on recent match data. Statistics may vary with sample size.\
                    </p>\
                </div>\
            </div>';
    }

    /**
     * Render loading state for the entire page.
     */
    function renderLoading() {
        if (!state.container) return;

        state.container.innerHTML = '\
            <div class="team-analytics-page team-analytics-page--loading">\
                <div class="team-analytics-page__loader">\
                    <div class="half-analysis-card__spinner"></div>\
                    <div>Loading analytics for ' + escapeHtml(state.currentTeam) + '...</div>\
                </div>\
            </div>';
    }

    /**
     * Render error state for the entire page.
     */
    function renderError(message) {
        if (!state.container) return;

        var retryText = state.retryCount < RETRY_MAX_ATTEMPTS
            ? '<button class="half-analysis-card__retry-btn team-analytics-retry-btn" type="button">Retry</button>'
            : '<p style="color: var(--text-muted, #64748b); font-size: 0.875rem;">Max retries reached. Please refresh the page.</p>';

        state.container.innerHTML = '\
            <div class="team-analytics-page team-analytics-page--error">\
                <div class="team-analytics-page__error">\
                    <div style="font-size: 3rem; margin-bottom: 1rem;">⚠️</div>\
                    <h3 style="margin: 0 0 0.5rem;">Unable to Load Analytics</h3>\
                    <p style="color: var(--text-muted, #64748b); margin: 0 0 1rem;">' + escapeHtml(message || 'An error occurred') + '</p>\
                    ' + retryText + '\
                </div>\
            </div>';

        // Attach retry handler
        var retryBtn = state.container.querySelector('.team-analytics-retry-btn');
        if (retryBtn) {
            retryBtn.addEventListener('click', function() {
                loadAnalytics();
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CORE FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Load all analytics for the current team.
     */
    function loadAnalytics() {
        if (!state.currentTeam || state.isLoading) return;

        state.isLoading = true;
        renderLoading();

        // Render page structure first
        if (state.container) {
            state.container.innerHTML = renderPageStructure(state.currentTeam);
        }

        // Load Half Analysis Card
        loadHalfAnalysis();
    }

    /**
     * Load half analysis component.
     */
    function loadHalfAnalysis() {
        var halfAnalysisContainer = document.getElementById('half-analysis-card-container');

        if (!halfAnalysisContainer) {
            console.error('[TeamAnalyticsPage] Half analysis container not found');
            state.isLoading = false;
            return;
        }

        // Check if HalfAnalysisCard is available
        if (!window.HalfAnalysisCard || typeof window.HalfAnalysisCard.fetchAndRender !== 'function') {
            console.error('[TeamAnalyticsPage] HalfAnalysisCard not loaded');
            halfAnalysisContainer.innerHTML = '\
                <div class="half-analysis-card half-analysis-card--error">\
                    <div class="half-analysis-card__error">\
                        <div class="half-analysis-card__error-icon">⚠️</div>\
                        <p>Half Analysis component not available</p>\
                    </div>\
                </div>';
            state.isLoading = false;
            return;
        }

        window.HalfAnalysisCard.fetchAndRender(halfAnalysisContainer, state.currentTeam)
            .then(function(data) {
                console.log('[TeamAnalyticsPage] Half analysis loaded:', data.teamName);
                state.isLoading = false;
                state.retryCount = 0;
            })
            .catch(function(error) {
                console.error('[TeamAnalyticsPage] Half analysis error:', error);
                state.isLoading = false;

                // HalfAnalysisCard handles its own error display
                // But if we need page-level retry, increment counter
                if (state.retryCount < RETRY_MAX_ATTEMPTS) {
                    // Error is shown in the component
                }
            });
    }

    /**
     * Initialize the team analytics page.
     * @param {string} teamName - Team name to display analytics for
     * @param {string|HTMLElement} containerId - Container element or ID
     */
    function init(teamName, containerId) {
        containerId = containerId || 'team-analytics-container';

        // Get container
        if (typeof containerId === 'string') {
            state.container = document.getElementById(containerId);
        } else if (containerId instanceof HTMLElement) {
            state.container = containerId;
        }

        if (!state.container) {
            console.error('[TeamAnalyticsPage] Container not found:', containerId);
            return;
        }

        if (!teamName) {
            renderError('No team specified');
            return;
        }

        state.currentTeam = teamName;
        state.retryCount = 0;
        loadAnalytics();
    }

    /**
     * Reload analytics for the current team.
     */
    function reload() {
        if (state.currentTeam) {
            state.retryCount = 0;
            loadAnalytics();
        }
    }

    /**
     * Change the current team and reload analytics.
     * @param {string} teamName - New team name
     */
    function setTeam(teamName) {
        if (!teamName) return;
        state.currentTeam = teamName;
        state.retryCount = 0;
        loadAnalytics();
    }

    /**
     * Get the current team.
     * @returns {string|null} Current team name
     */
    function getCurrentTeam() {
        return state.currentTeam;
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════════════════

    window.TeamAnalyticsPage = {
        init: init,
        reload: reload,
        setTeam: setTeam,
        getCurrentTeam: getCurrentTeam
    };

})();


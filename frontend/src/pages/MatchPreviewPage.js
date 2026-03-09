/**
 * Match Preview Page Module
 * ==========================
 *
 * Page component for displaying match preview analytics including:
 * - Corner kick predictions
 * - Match outcome predictions (future integration)
 * - Head-to-head statistics (future integration)
 *
 * @module MatchPreviewPage
 * @author Football Forecaster Team
 * @version 1.0.0
 */

import {
    renderCornerPredictionCard,
    renderCornerPredictionLoading,
    renderCornerPredictionError,
    fetchAndRenderCornerPredictionCard
} from '../components/match/CornerPredictionCard.js';

import {
    renderMatchXGCard,
    renderMatchXGLoading,
    renderMatchXGError,
    fetchAndRenderMatchXGCard
} from '../components/match/MatchXGCard.js';

// ══════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ══════════════════════════════════════════════════════════════════════

/**
 * Escape HTML to prevent XSS
 * @param {string} str - String to escape
 * @returns {string} Escaped string
 */
function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// ══════════════════════════════════════════════════════════════════════
// MATCH PREVIEW PAGE CLASS
// ══════════════════════════════════════════════════════════════════════

/**
 * MatchPreviewPage class
 * Manages the match preview page state and rendering
 */
class MatchPreviewPage {
    /**
     * Create a MatchPreviewPage instance
     * @param {string} containerId - ID of the container element
     */
    constructor(containerId) {
        this.containerId = containerId;
        this.container = null;
        this.homeTeam = null;
        this.awayTeam = null;
        this.isLoading = false;
        this.predictionData = null;
    }

    /**
     * Initialize the page
     * @param {string} homeTeam - Home team name
     * @param {string} awayTeam - Away team name
     */
    async init(homeTeam, awayTeam) {
        this.container = document.getElementById(this.containerId);

        if (!this.container) {
            console.error('[MatchPreviewPage] Container not found:', this.containerId);
            return;
        }

        if (!homeTeam || !awayTeam) {
            this.showError('Both home and away teams must be specified');
            return;
        }

        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        await this.loadPredictions();
    }

    /**
     * Load and render all predictions for the match
     */
    async loadPredictions() {
        if (this.isLoading || !this.homeTeam || !this.awayTeam) return;

        this.isLoading = true;
        this.showLoading();

        try {
            // Fetch corner prediction data
            const response = await fetch(
                `${window.location.origin}/api/matches/predict-corners?home=${encodeURIComponent(this.homeTeam)}&away=${encodeURIComponent(this.awayTeam)}`
            );

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
            }

            this.predictionData = await response.json();
            this.render(this.predictionData);

        } catch (error) {
            console.error('[MatchPreviewPage] Failed to load predictions:', error);
            this.showError(error.message || 'Failed to load match predictions');
        } finally {
            this.isLoading = false;
        }
    }

    /**
     * Show loading state
     */
    showLoading() {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="match-preview-page">
                <div class="match-preview-page__header">
                    <h2 class="match-preview-page__title">⚽ Match Preview</h2>
                    <p class="match-preview-page__subtitle">Loading predictions for ${escapeHtml(this.homeTeam)} vs ${escapeHtml(this.awayTeam)}...</p>
                </div>
                <div class="match-preview-page__content">
                    <div id="corner-prediction-container"></div>
                    <div id="xg-prediction-container"></div>
                </div>
            </div>
        `;

        // Show loading in containers
        const cornerContainer = document.getElementById('corner-prediction-container');
        if (cornerContainer) {
            renderCornerPredictionLoading(cornerContainer);
        }

        const xgContainer = document.getElementById('xg-prediction-container');
        if (xgContainer) {
            renderMatchXGLoading(xgContainer);
        }
    }

    /**
     * Show error state
     * @param {string} message - Error message
     */
    showError(message) {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="match-preview-page match-preview-page--error">
                <div class="match-preview-page__error">
                    <span class="match-preview-page__error-icon">⚠️</span>
                    <h3>Failed to Load Match Preview</h3>
                    <p>${escapeHtml(message)}</p>
                    <button class="btn btn-primary" id="retry-predictions">Retry</button>
                </div>
            </div>
        `;

        // Add retry handler
        const retryBtn = document.getElementById('retry-predictions');
        if (retryBtn) {
            retryBtn.addEventListener('click', () => this.loadPredictions());
        }
    }

    /**
     * Render the match preview page with prediction data
     * @param {Object} data - Corner prediction data
     */
    render(data) {
        if (!this.container) return;

        const homeTeam = data.homeTeam || this.homeTeam;
        const awayTeam = data.awayTeam || this.awayTeam;

        this.container.innerHTML = `
            <div class="match-preview-page">
                <div class="match-preview-page__header">
                    <h2 class="match-preview-page__title">⚽ Match Preview</h2>
                    <div class="match-preview-page__teams">
                        <span class="match-preview-page__team match-preview-page__team--home">${escapeHtml(homeTeam)}</span>
                        <span class="match-preview-page__vs">vs</span>
                        <span class="match-preview-page__team match-preview-page__team--away">${escapeHtml(awayTeam)}</span>
                    </div>
                </div>
                <div class="match-preview-page__content">
                    <div class="match-preview-page__section">
                        <h3 class="match-preview-page__section-title">⚑ Corner Prediction</h3>
                        <div id="corner-prediction-container" class="match-preview-page__card-container"></div>
                    </div>
                    <div class="match-preview-page__section">
                        <h3 class="match-preview-page__section-title">🎯 Expected Goals (xG) Prediction</h3>
                        <div id="xg-prediction-container" class="match-preview-page__card-container"></div>
                    </div>
                </div>
                <div class="match-preview-page__footer">
                    <p class="match-preview-page__note">
                        Predictions based on historical data from the last 20 matches
                    </p>
                </div>
            </div>
        `;

        // Render corner prediction card
        const cornerContainer = document.getElementById('corner-prediction-container');
        if (cornerContainer) {
            renderCornerPredictionCard(cornerContainer, data);
        }

        // Render xG prediction card
        const xgContainer = document.getElementById('xg-prediction-container');
        if (xgContainer) {
            fetchAndRenderMatchXGCard(xgContainer, homeTeam, awayTeam);
        }
    }

    /**
     * Refresh predictions data
     */
    async refresh() {
        await this.loadPredictions();
    }

    /**
     * Change match teams and reload predictions
     * @param {string} homeTeam - New home team
     * @param {string} awayTeam - New away team
     */
    async changeMatch(homeTeam, awayTeam) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        await this.loadPredictions();
    }

    /**
     * Get current prediction data
     * @returns {Object|null} Current prediction data
     */
    getPredictionData() {
        return this.predictionData;
    }

    /**
     * Cleanup resources
     */
    destroy() {
        if (this.container) {
            this.container.innerHTML = '';
        }
        this.homeTeam = null;
        this.awayTeam = null;
        this.predictionData = null;
    }
}

// ══════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS FOR INTEGRATION
// ══════════════════════════════════════════════════════════════════════

/**
 * Render corner prediction section standalone
 * Utility function for integration into existing pages
 *
 * @param {HTMLElement} container - Container element
 * @param {string} homeTeam - Home team name
 * @param {string} awayTeam - Away team name
 * @returns {Promise<Object>} Prediction data
 */
export async function renderCornerPredictionSection(container, homeTeam, awayTeam) {
    if (!container || !homeTeam || !awayTeam) {
        console.error('[MatchPreviewPage] Container and team names are required');
        return null;
    }

    // Create section structure
    const section = document.createElement('div');
    section.className = 'corner-prediction-section';
    section.innerHTML = `
        <div class="corner-prediction-section__header">
            <h3 class="corner-prediction-section__title">⚑ Corner Prediction</h3>
        </div>
        <div id="standalone-corner-prediction" class="corner-prediction-section__card"></div>
    `;

    container.innerHTML = '';
    container.appendChild(section);

    const predictionContainer = document.getElementById('standalone-corner-prediction');

    // Fetch and render
    return fetchAndRenderCornerPredictionCard(predictionContainer, homeTeam, awayTeam);
}

/**
 * Render xG prediction section standalone
 * Utility function for integration into existing pages
 *
 * @param {HTMLElement} container - Container element
 * @param {string} homeTeam - Home team name
 * @param {string} awayTeam - Away team name
 * @returns {Promise<Object>} xG Prediction data
 */
export async function renderXGPredictionSection(container, homeTeam, awayTeam) {
    if (!container || !homeTeam || !awayTeam) {
        console.error('[MatchPreviewPage] Container and team names are required');
        return null;
    }

    const section = document.createElement('div');
    section.className = 'xg-prediction-section';
    section.innerHTML = `
        <div class="xg-prediction-section__header">
            <h3 class="xg-prediction-section__title">🎯 Expected Goals (xG) Prediction</h3>
        </div>
        <div id="standalone-xg-prediction" class="xg-prediction-section__card"></div>
    `;

    container.innerHTML = '';
    container.appendChild(section);

    const predictionContainer = document.getElementById('standalone-xg-prediction');

    return fetchAndRenderMatchXGCard(predictionContainer, homeTeam, awayTeam);
}

/**
 * Factory function to create and initialize MatchPreviewPage
 * @param {string} containerId - Container element ID
 * @param {string} homeTeam - Home team name
 * @param {string} awayTeam - Away team name
 * @returns {Promise<MatchPreviewPage>} Initialized page instance
 */
export async function createMatchPreviewPage(containerId, homeTeam, awayTeam) {
    const page = new MatchPreviewPage(containerId);
    await page.init(homeTeam, awayTeam);
    return page;
}

// Export the class
export { MatchPreviewPage };

// Make available globally for non-module usage
if (typeof window !== 'undefined') {
    window.MatchPreviewPage = MatchPreviewPage;
    window.renderCornerPredictionSection = renderCornerPredictionSection;
    window.renderXGPredictionSection = renderXGPredictionSection;
    window.createMatchPreviewPage = createMatchPreviewPage;
}


/**
 * Team Analytics Page Module
 * ==========================
 *
 * Page component for displaying comprehensive team analytics including:
 * - Shot Quality Analysis (Home/Away side-by-side cards)
 * - Additional analytics components can be added
 *
 * @module TeamAnalyticsPage
 * @author Football Forecaster Team
 * @version 1.0.0
 */

import {
    renderShotQualityCard,
    renderShotQualityLoading,
    renderShotQualityError,
    fetchAndRenderShotQualityCard,
    LEAGUE_AVERAGES
} from './ShotQualityCard.js';

import {
    renderCornerStatsCard,
    renderCornerStatsLoading,
    renderCornerStatsError,
    fetchAndRenderCornerStatsCard,
    LEAGUE_AVERAGE_CORNERS
} from '../components/team/CornerStatsCard.js';

import {
    renderExpectedGoalsCard,
    renderExpectedGoalsLoading,
    renderExpectedGoalsError,
    fetchAndRenderExpectedGoalsCard,
    LEAGUE_AVERAGE_XG
} from '../components/team/ExpectedGoalsCard.js';

import {
    renderKickoffTimeCard,
    renderKickoffTimeLoading,
    renderKickoffTimeError,
    fetchAndRenderKickoffTimeCard
} from '../components/team/KickoffTimeCard.js';

/**
 * TeamAnalyticsPage class
 * Manages the team analytics page state and rendering
 */
class TeamAnalyticsPage {
    /**
     * Create a TeamAnalyticsPage instance
     * @param {string} containerId - ID of the container element
     */
    constructor(containerId) {
        this.containerId = containerId;
        this.container = null;
        this.currentTeam = null;
        this.isLoading = false;
    }

    /**
     * Initialize the page
     * @param {string} teamName - Team name to display analytics for
     */
    async init(teamName) {
        this.container = document.getElementById(this.containerId);

        if (!this.container) {
            console.error('[TeamAnalyticsPage] Container not found:', this.containerId);
            return;
        }

        if (!teamName) {
            this.showError('No team specified');
            return;
        }

        this.currentTeam = teamName;
        await this.loadAnalytics();
    }

    /**
     * Load and render all analytics for the current team
     */
    async loadAnalytics() {
        if (this.isLoading || !this.currentTeam) return;

        this.isLoading = true;
        this.showLoading();

        try {
            // Fetch shot quality data with home/away split
            const response = await fetch(
                `${window.location.origin}/api/teams/${encodeURIComponent(this.currentTeam)}/shot-quality?split=true`
            );

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const data = await response.json();
            this.render(data);

        } catch (error) {
            console.error('[TeamAnalyticsPage] Failed to load analytics:', error);
            this.showError(error.message || 'Failed to load analytics');
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
            <div class="team-analytics-page">
                <div class="team-analytics-page__header">
                    <h2 class="team-analytics-page__title">📊 Shot Quality Analytics</h2>
                    <p class="team-analytics-page__subtitle">Loading data for ${this.escapeHtml(this.currentTeam)}...</p>
                </div>
                <div class="shot-quality-cards-container">
                    <div id="home-shot-quality-container"></div>
                    <div id="away-shot-quality-container"></div>
                </div>
            </div>
        `;

        // Show loading in both containers
        const homeContainer = document.getElementById('home-shot-quality-container');
        const awayContainer = document.getElementById('away-shot-quality-container');

        if (homeContainer) renderShotQualityLoading(homeContainer);
        if (awayContainer) renderShotQualityLoading(awayContainer);
    }

    /**
     * Show error state
     * @param {string} message - Error message
     */
    showError(message) {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="team-analytics-page team-analytics-page--error">
                <div class="team-analytics-page__error">
                    <span class="team-analytics-page__error-icon">⚠️</span>
                    <h3>Failed to Load Analytics</h3>
                    <p>${this.escapeHtml(message)}</p>
                    <button class="btn btn-primary" id="retry-analytics">Retry</button>
                </div>
            </div>
        `;

        // Add retry handler
        const retryBtn = document.getElementById('retry-analytics');
        if (retryBtn) {
            retryBtn.addEventListener('click', () => this.loadAnalytics());
        }
    }

    /**
     * Render the analytics page with data
     * @param {Object} data - Shot quality data with home/away split
     */
    render(data) {
        if (!this.container) return;

        const teamName = data.teamName || this.currentTeam;

        this.container.innerHTML = `
            <div class="team-analytics-page">
                <div class="team-analytics-page__header">
                    <h2 class="team-analytics-page__title">📊 Team Analytics</h2>
                    <p class="team-analytics-page__subtitle">${this.escapeHtml(teamName)} - Home vs Away Performance</p>
                </div>
                <div class="team-analytics-page__section">
                    <h3 class="team-analytics-page__section-title">🎯 Shot Efficiency Comparison</h3>
                    <div class="shot-quality-cards-container">
                        <div id="home-shot-quality-container" class="shot-quality-card-wrapper"></div>
                        <div id="away-shot-quality-container" class="shot-quality-card-wrapper"></div>
                    </div>
                </div>
                <div class="team-analytics-page__section">
                    <h3 class="team-analytics-page__section-title">⚑ Corner Kick Statistics</h3>
                    <div class="corner-stats-cards-container">
                        <div id="home-corner-stats-container" class="corner-stats-card-wrapper"></div>
                        <div id="away-corner-stats-container" class="corner-stats-card-wrapper"></div>
                    </div>
                </div>
                <div class="team-analytics-page__section">
                    <h3 class="team-analytics-page__section-title">🎯 Expected Goals (xG)</h3>
                    <div class="xg-cards-container">
                        <div id="home-xg-container" class="xg-card-wrapper"></div>
                        <div id="away-xg-container" class="xg-card-wrapper"></div>
                    </div>
                </div>
                <div class="team-analytics-page__section">
                    <h3 class="team-analytics-page__section-title">🕐 Performance Patterns</h3>
                    <div class="kickoff-time-container">
                        <div id="kickoff-time-container" class="kickoff-time-card-wrapper"></div>
                    </div>
                </div>
                <div class="team-analytics-page__footer">
                    <p class="team-analytics-page__note">
                        League averages: Shot Accuracy ${LEAGUE_AVERAGES.shotAccuracy}% | Corners ${LEAGUE_AVERAGE_CORNERS} per match | xG ${LEAGUE_AVERAGE_XG} per team
                    </p>
                </div>
            </div>
        `;

        // Render shot quality cards
        const homeContainer = document.getElementById('home-shot-quality-container');
        const awayContainer = document.getElementById('away-shot-quality-container');

        if (homeContainer && data.home) {
            renderShotQualityCard(homeContainer, data.home);
        }

        if (awayContainer && data.away) {
            renderShotQualityCard(awayContainer, data.away);
        }

        // Render corner stats cards
        this.loadCornerStats(teamName);

        // Render expected goals cards
        this.loadExpectedGoals(teamName);

        // Render kick-off time analysis
        this.loadKickoffTimeAnalysis(teamName);
    }

    /**
     * Load and render corner statistics
     * @param {string} teamName - Team name
     */
    async loadCornerStats(teamName) {
        const homeCornerContainer = document.getElementById('home-corner-stats-container');
        const awayCornerContainer = document.getElementById('away-corner-stats-container');

        if (homeCornerContainer) {
            renderCornerStatsLoading(homeCornerContainer);
            fetchAndRenderCornerStatsCard(homeCornerContainer, teamName, true);
        }

        if (awayCornerContainer) {
            renderCornerStatsLoading(awayCornerContainer);
            fetchAndRenderCornerStatsCard(awayCornerContainer, teamName, false);
        }
    }

    /**
     * Load and render expected goals statistics
     * @param {string} teamName - Team name
     */
    async loadExpectedGoals(teamName) {
        const homeXGContainer = document.getElementById('home-xg-container');
        const awayXGContainer = document.getElementById('away-xg-container');

        if (homeXGContainer) {
            renderExpectedGoalsLoading(homeXGContainer);
            fetchAndRenderExpectedGoalsCard(homeXGContainer, teamName, true);
        }

        if (awayXGContainer) {
            renderExpectedGoalsLoading(awayXGContainer);
            fetchAndRenderExpectedGoalsCard(awayXGContainer, teamName, false);
        }
    }

    /**
     * Load and render kick-off time performance analysis
     * @param {string} teamName - Team name
     */
    async loadKickoffTimeAnalysis(teamName) {
        const kickoffContainer = document.getElementById('kickoff-time-container');

        if (kickoffContainer) {
            renderKickoffTimeLoading(kickoffContainer);
            fetchAndRenderKickoffTimeCard(kickoffContainer, teamName);
        }
    }

    /**
     * Escape HTML to prevent XSS
     * @param {string} str - String to escape
     * @returns {string} Escaped string
     */
    escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    /**
     * Refresh analytics data
     */
    async refresh() {
        await this.loadAnalytics();
    }

    /**
     * Change team and reload analytics
     * @param {string} teamName - New team name
     */
    async changeTeam(teamName) {
        this.currentTeam = teamName;
        await this.loadAnalytics();
    }

    /**
     * Cleanup resources
     */
    destroy() {
        if (this.container) {
            this.container.innerHTML = '';
        }
        this.currentTeam = null;
    }
}

/**
 * Render shot quality cards side-by-side for home and away stats
 * Utility function for integration into existing pages
 *
 * @param {HTMLElement} container - Container element
 * @param {string} teamName - Team name
 * @returns {Promise<Object>} Object containing home and away card elements
 */
export async function renderShotQualitySection(container, teamName) {
    if (!container || !teamName) {
        console.error('[TeamAnalyticsPage] Container and teamName are required');
        return null;
    }

    // Create the section structure
    const section = document.createElement('div');
    section.className = 'shot-quality-section';
    section.innerHTML = `
        <div class="shot-quality-section__header">
            <h3 class="shot-quality-section__title">🎯 Shot Quality Analysis</h3>
        </div>
        <div class="shot-quality-cards-container">
            <div id="section-home-shot-quality" class="shot-quality-card-wrapper"></div>
            <div id="section-away-shot-quality" class="shot-quality-card-wrapper"></div>
        </div>
    `;

    container.innerHTML = '';
    container.appendChild(section);

    const homeContainer = document.getElementById('section-home-shot-quality');
    const awayContainer = document.getElementById('section-away-shot-quality');

    // Show loading state
    renderShotQualityLoading(homeContainer);
    renderShotQualityLoading(awayContainer);

    try {
        // Fetch data with split
        const response = await fetch(
            `${window.location.origin}/api/teams/${encodeURIComponent(teamName)}/shot-quality?split=true`
        );

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();

        // Render cards
        const homeCard = renderShotQualityCard(homeContainer, data.home);
        const awayCard = renderShotQualityCard(awayContainer, data.away);

        return { homeCard, awayCard, data };

    } catch (error) {
        console.error('[TeamAnalyticsPage] Failed to load shot quality:', error);
        renderShotQualityError(homeContainer, 'Failed to load home stats');
        renderShotQualityError(awayContainer, 'Failed to load away stats');
        return null;
    }
}

/**
 * Factory function to create and initialize TeamAnalyticsPage
 * @param {string} containerId - Container element ID
 * @param {string} teamName - Team name
 * @returns {Promise<TeamAnalyticsPage>} Initialized page instance
 */
export async function createTeamAnalyticsPage(containerId, teamName) {
    const page = new TeamAnalyticsPage(containerId);
    await page.init(teamName);
    return page;
}

// Export the class and utility functions
export { TeamAnalyticsPage };

/**
 * Render corner stats cards side-by-side for home and away
 * Utility function for integration into existing pages
 *
 * @param {HTMLElement} container - Container element
 * @param {string} teamName - Team name
 * @returns {Promise<Object>} Object containing home and away card data
 */
export async function renderCornerStatsSection(container, teamName) {
    if (!container || !teamName) {
        console.error('[TeamAnalyticsPage] Container and teamName are required');
        return null;
    }

    // Create the section structure
    const section = document.createElement('div');
    section.className = 'corner-stats-section';
    section.innerHTML = `
        <div class="corner-stats-section__header">
            <h3 class="corner-stats-section__title">⚑ Corner Kick Statistics</h3>
        </div>
        <div class="corner-stats-cards-container">
            <div id="section-home-corner-stats" class="corner-stats-card-wrapper"></div>
            <div id="section-away-corner-stats" class="corner-stats-card-wrapper"></div>
        </div>
    `;

    container.innerHTML = '';
    container.appendChild(section);

    const homeContainer = document.getElementById('section-home-corner-stats');
    const awayContainer = document.getElementById('section-away-corner-stats');

    // Show loading state
    renderCornerStatsLoading(homeContainer);
    renderCornerStatsLoading(awayContainer);

    try {
        // Fetch both home and away stats in parallel
        const [homeData, awayData] = await Promise.all([
            fetchAndRenderCornerStatsCard(homeContainer, teamName, true),
            fetchAndRenderCornerStatsCard(awayContainer, teamName, false)
        ]);

        return { home: homeData, away: awayData };

    } catch (error) {
        console.error('[TeamAnalyticsPage] Failed to load corner stats:', error);
        renderCornerStatsError(homeContainer, 'Failed to load home stats');
        renderCornerStatsError(awayContainer, 'Failed to load away stats');
        return null;
    }
}

/**
 * Render expected goals cards side-by-side for home and away
 * Utility function for integration into existing pages
 *
 * @param {HTMLElement} container - Container element
 * @param {string} teamName - Team name
 * @returns {Promise<Object>} Object containing home and away card data
 */
export async function renderExpectedGoalsSection(container, teamName) {
    if (!container || !teamName) {
        console.error('[TeamAnalyticsPage] Container and teamName are required');
        return null;
    }

    const section = document.createElement('div');
    section.className = 'xg-section';
    section.innerHTML = `
        <div class="xg-section__header">
            <h3 class="xg-section__title">🎯 Expected Goals (xG)</h3>
        </div>
        <div class="xg-cards-container">
            <div id="section-home-xg" class="xg-card-wrapper"></div>
            <div id="section-away-xg" class="xg-card-wrapper"></div>
        </div>
    `;

    container.innerHTML = '';
    container.appendChild(section);

    const homeContainer = document.getElementById('section-home-xg');
    const awayContainer = document.getElementById('section-away-xg');

    renderExpectedGoalsLoading(homeContainer);
    renderExpectedGoalsLoading(awayContainer);

    try {
        const [homeData, awayData] = await Promise.all([
            fetchAndRenderExpectedGoalsCard(homeContainer, teamName, true),
            fetchAndRenderExpectedGoalsCard(awayContainer, teamName, false)
        ]);

        return { home: homeData, away: awayData };

    } catch (error) {
        console.error('[TeamAnalyticsPage] Failed to load expected goals:', error);
        renderExpectedGoalsError(homeContainer, 'Failed to load home xG');
        renderExpectedGoalsError(awayContainer, 'Failed to load away xG');
        return null;
    }
}

// Make available globally for non-module usage
if (typeof window !== 'undefined') {
    window.TeamAnalyticsPage = TeamAnalyticsPage;
    window.renderShotQualitySection = renderShotQualitySection;
    window.renderCornerStatsSection = renderCornerStatsSection;
    window.renderExpectedGoalsSection = renderExpectedGoalsSection;
    window.createTeamAnalyticsPage = createTeamAnalyticsPage;
}


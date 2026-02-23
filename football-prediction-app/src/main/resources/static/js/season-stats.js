/**
 * Season Team Stats Component
 *
 * A reusable component for displaying team statistics including:
 * - Elo rating with strength classification
 * - Current streak badge
 * - Last 5 match form visualization
 * - Comprehensive stats grid
 *
 * @module SeasonTeamStats
 * @version 1.0.0
 */

const SeasonTeamStats = (() => {
    'use strict';

    // ═══════════════════════════════════════════════════════════════════
    // Configuration
    // ═══════════════════════════════════════════════════════════════════

    const CONFIG = {
        API_BASE_URL: '/api/season',
        TIMEOUT_MS: 10000,
        ELO_THRESHOLDS: {
            WEAK: 1450,
            COMPETITIVE: 1600,
            STRONG: 1750
        }
    };

    // ═══════════════════════════════════════════════════════════════════
    // Elo Strength Classification
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Classify team strength based on Elo rating
     * @param {number} elo - The Elo rating value
     * @returns {{label: string, cssClass: string}} Strength classification
     */
    const classifyEloStrength = (elo) => {
        if (elo < CONFIG.ELO_THRESHOLDS.WEAK) {
            return { label: 'Weak', cssClass: 'sts-strength-badge--weak' };
        }
        if (elo < CONFIG.ELO_THRESHOLDS.COMPETITIVE) {
            return { label: 'Competitive', cssClass: 'sts-strength-badge--competitive' };
        }
        if (elo < CONFIG.ELO_THRESHOLDS.STRONG) {
            return { label: 'Strong', cssClass: 'sts-strength-badge--strong' };
        }
        return { label: 'Elite', cssClass: 'sts-strength-badge--elite' };
    };

    // ═══════════════════════════════════════════════════════════════════
    // Streak Badge Logic
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get streak badge class based on streak type
     * @param {string} streak - Streak string (e.g., "W3", "D1", "L2")
     * @returns {string} CSS class for the streak badge
     */
    const getStreakBadgeClass = (streak) => {
        if (!streak || streak.length === 0) {
            return 'sts-streak-badge--draw';
        }

        const firstChar = streak.charAt(0).toUpperCase();

        switch (firstChar) {
            case 'W':
                return 'sts-streak-badge--win';
            case 'L':
                return 'sts-streak-badge--loss';
            case 'D':
            default:
                return 'sts-streak-badge--draw';
        }
    };

    /**
     * Get human-readable streak description
     * @param {string} streak - Streak string (e.g., "W3")
     * @returns {string} Human-readable description
     */
    const getStreakDescription = (streak) => {
        if (!streak || streak.length === 0) {
            return 'No current streak';
        }

        const type = streak.charAt(0).toUpperCase();
        const count = streak.substring(1) || '0';

        const types = {
            'W': 'Win',
            'D': 'Draw',
            'L': 'Loss'
        };

        const typeName = types[type] || 'Match';
        return `${count} ${typeName}${parseInt(count) !== 1 ? 's' : ''} streak`;
    };

    // ═══════════════════════════════════════════════════════════════════
    // Form Dot Logic
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get form dot class based on result
     * @param {string} result - Single result character (W/D/L)
     * @returns {string} CSS class for the form dot
     */
    const getFormDotClass = (result) => {
        const normalizedResult = result?.toUpperCase();

        switch (normalizedResult) {
            case 'W':
                return 'sts-form-dot--win';
            case 'L':
                return 'sts-form-dot--loss';
            case 'D':
            default:
                return 'sts-form-dot--draw';
        }
    };

    /**
     * Get accessible label for form result
     * @param {string} result - Single result character
     * @returns {string} Full result name
     */
    const getFormResultLabel = (result) => {
        const labels = {
            'W': 'Win',
            'D': 'Draw',
            'L': 'Loss'
        };
        return labels[result?.toUpperCase()] || 'Unknown';
    };

    // ═══════════════════════════════════════════════════════════════════
    // DOM Creation Helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Create an element with classes and attributes
     * @param {string} tag - HTML tag name
     * @param {string|string[]} classes - CSS class(es)
     * @param {Object} attrs - HTML attributes
     * @returns {HTMLElement}
     */
    const createElement = (tag, classes = [], attrs = {}) => {
        const el = document.createElement(tag);

        const classArray = Array.isArray(classes) ? classes : [classes];
        classArray.filter(Boolean).forEach(cls => el.classList.add(cls));

        Object.entries(attrs).forEach(([key, value]) => {
            if (key === 'textContent') {
                el.textContent = value;
            } else if (key === 'innerHTML') {
                el.innerHTML = value;
            } else {
                el.setAttribute(key, value);
            }
        });

        return el;
    };

    // ═══════════════════════════════════════════════════════════════════
    // Component Rendering
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Render loading skeleton
     * @returns {HTMLElement}
     */
    const renderSkeleton = () => {
        const card = createElement('article', ['sts-card', 'sts-card--loading'], {
            'aria-busy': 'true',
            'aria-label': 'Loading team statistics'
        });

        // Header skeleton
        const header = createElement('div', 'sts-header');
        const teamInfo = createElement('div', 'sts-team-info');
        teamInfo.appendChild(createElement('div', ['sts-skeleton', 'sts-skeleton--text']));
        teamInfo.appendChild(createElement('div', ['sts-skeleton', 'sts-skeleton--text-sm'], {
            style: 'margin-top: 8px;'
        }));
        header.appendChild(teamInfo);
        card.appendChild(header);

        // Elo section skeleton
        const eloSection = createElement('div', 'sts-elo-section');
        eloSection.appendChild(createElement('div', ['sts-skeleton', 'sts-skeleton--elo']));
        eloSection.appendChild(createElement('div', ['sts-skeleton', 'sts-skeleton--badge']));
        card.appendChild(eloSection);

        // Form section skeleton
        const formSection = createElement('div', 'sts-form-section');
        formSection.appendChild(createElement('div', ['sts-skeleton', 'sts-skeleton--badge']));
        formSection.appendChild(createElement('div', ['sts-skeleton', 'sts-skeleton--dots']));
        card.appendChild(formSection);

        // Stats grid skeleton
        const statsGrid = createElement('div', 'sts-stats-grid');
        for (let i = 0; i < 8; i++) {
            statsGrid.appendChild(createElement('div', ['sts-skeleton', 'sts-skeleton--stat']));
        }
        card.appendChild(statsGrid);

        return card;
    };

    /**
     * Render error state
     * @param {string} message - Error message
     * @param {Function} onRetry - Retry callback function
     * @returns {HTMLElement}
     */
    const renderError = (message, onRetry) => {
        const card = createElement('article', 'sts-card', {
            'aria-live': 'polite'
        });

        const errorContainer = createElement('div', 'sts-error');

        // Error icon (inline SVG)
        const iconSvg = `
            <svg class="sts-error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                 aria-hidden="true">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="12"></line>
                <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
        `;
        errorContainer.innerHTML = iconSvg;

        errorContainer.appendChild(
            createElement('h3', 'sts-error-title', { textContent: 'Failed to Load Stats' })
        );

        errorContainer.appendChild(
            createElement('p', 'sts-error-message', { textContent: message })
        );

        const retryBtn = createElement('button', 'sts-error-retry', {
            textContent: 'Try Again',
            type: 'button',
            'aria-label': 'Retry loading team statistics'
        });

        if (typeof onRetry === 'function') {
            retryBtn.addEventListener('click', onRetry);
        }

        errorContainer.appendChild(retryBtn);
        card.appendChild(errorContainer);

        return card;
    };

    /**
     * Render the complete stats card
     * @param {Object} data - Team stats data
     * @param {string} teamName - Team name to display
     * @returns {HTMLElement}
     */
    const renderStatsCard = (data, teamName = 'Team') => {
        const card = createElement('article', 'sts-card', {
            'aria-label': `Statistics for ${teamName}`,
            tabindex: '0'
        });

        // ─── Header ───────────────────────────────────────────
        const header = createElement('header', 'sts-header');
        const teamInfo = createElement('div', 'sts-team-info');

        teamInfo.appendChild(
            createElement('h2', 'sts-team-name', { textContent: teamName })
        );
        teamInfo.appendChild(
            createElement('p', 'sts-season-label', {
                textContent: `Season ${data.seasonId || 'N/A'}`
            })
        );

        header.appendChild(teamInfo);
        card.appendChild(header);

        // ─── Elo Section ──────────────────────────────────────
        const eloSection = createElement('div', 'sts-elo-section');

        const eloValue = data.eloRating || 1500;
        const strength = classifyEloStrength(eloValue);

        eloSection.appendChild(
            createElement('div', 'sts-elo-label', { textContent: 'Elo Rating' })
        );
        eloSection.appendChild(
            createElement('div', 'sts-elo-value', {
                textContent: eloValue.toFixed(1),
                'aria-label': `Elo rating: ${eloValue.toFixed(1)}`
            })
        );
        eloSection.appendChild(
            createElement('span', ['sts-strength-badge', strength.cssClass], {
                textContent: strength.label,
                'aria-label': `Strength classification: ${strength.label}`
            })
        );

        card.appendChild(eloSection);

        // ─── Form Section ─────────────────────────────────────
        const formSection = createElement('div', 'sts-form-section');

        // Current Streak
        const streakContainer = createElement('div');
        const streak = data.currentStreak || 'N0';
        const streakBadgeClass = getStreakBadgeClass(streak);

        streakContainer.appendChild(
            createElement('span', 'sts-streak-label', { textContent: 'Current Streak' })
        );
        streakContainer.appendChild(
            createElement('div', ['sts-streak-badge', streakBadgeClass], {
                textContent: streak,
                'aria-label': getStreakDescription(streak)
            })
        );
        formSection.appendChild(streakContainer);

        // Last 5 Form
        const formContainer = createElement('div', 'sts-form-container');
        formContainer.appendChild(
            createElement('span', 'sts-form-label', { textContent: 'Last 5 Matches' })
        );

        const formDots = createElement('div', 'sts-form-dots', {
            role: 'list',
            'aria-label': 'Recent form: last 5 match results'
        });

        const formArray = data.formLast5 || data.formString?.split('') || [];
        formArray.slice(0, 5).forEach((result, index) => {
            const dot = createElement('span', ['sts-form-dot', getFormDotClass(result)], {
                role: 'listitem',
                'aria-label': `Match ${index + 1}: ${getFormResultLabel(result)}`,
                title: getFormResultLabel(result)
            });
            formDots.appendChild(dot);
        });

        // Fill remaining dots if less than 5
        const remaining = 5 - formArray.length;
        for (let i = 0; i < remaining; i++) {
            const dot = createElement('span', ['sts-form-dot', 'sts-form-dot--draw'], {
                role: 'listitem',
                'aria-label': 'No match data',
                style: 'opacity: 0.3;'
            });
            formDots.appendChild(dot);
        }

        formContainer.appendChild(formDots);
        formSection.appendChild(formContainer);
        card.appendChild(formSection);

        // ─── Stats Grid ───────────────────────────────────────
        const statsGrid = createElement('div', 'sts-stats-grid', {
            role: 'list',
            'aria-label': 'Team statistics'
        });

        const stats = [
            { key: 'matchesPlayed', label: 'Played', value: data.matchesPlayed ?? 0 },
            { key: 'wins', label: 'Wins', value: data.wins ?? 0, modifier: 'wins' },
            { key: 'draws', label: 'Draws', value: data.draws ?? 0 },
            { key: 'losses', label: 'Losses', value: data.losses ?? 0, modifier: 'losses' },
            { key: 'goalsScored', label: 'Goals For', value: data.goalsScored ?? 0 },
            { key: 'goalsConceded', label: 'Goals Agn', value: data.goalsConceded ?? 0 },
            { key: 'cleanSheets', label: 'Clean Shts', value: data.cleanSheets ?? 0 },
            { key: 'goalDiff', label: 'Goal Diff', value: (data.goalsScored ?? 0) - (data.goalsConceded ?? 0) }
        ];

        stats.forEach(stat => {
            const classes = ['sts-stat-item'];
            if (stat.modifier) {
                classes.push(`sts-stat-item--${stat.modifier}`);
            }

            const statItem = createElement('div', classes, {
                role: 'listitem'
            });

            statItem.appendChild(
                createElement('span', 'sts-stat-value', {
                    textContent: stat.value,
                    'aria-hidden': 'true'
                })
            );
            statItem.appendChild(
                createElement('span', 'sts-stat-label', { textContent: stat.label })
            );

            // Hidden accessible text
            statItem.appendChild(
                createElement('span', 'sts-visually-hidden', {
                    textContent: `${stat.label}: ${stat.value}`
                })
            );

            statsGrid.appendChild(statItem);
        });

        card.appendChild(statsGrid);

        return card;
    };

    // ═══════════════════════════════════════════════════════════════════
    // API Functions
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Fetch team stats from API
     * @param {string|number} seasonId - Season identifier
     * @param {string|number} teamId - Team identifier
     * @returns {Promise<Object>} Team stats data
     */
    const fetchSeasonTeamStats = async (seasonId, teamId) => {
        const url = `${CONFIG.API_BASE_URL}/${encodeURIComponent(seasonId)}/team/${encodeURIComponent(teamId)}/stats`;

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), CONFIG.TIMEOUT_MS);

        try {
            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                if (response.status === 404) {
                    throw new Error('Team statistics not found');
                }
                throw new Error(`Server error: ${response.status}`);
            }

            const data = await response.json();
            return data;
        } catch (error) {
            clearTimeout(timeoutId);

            if (error.name === 'AbortError') {
                throw new Error('Request timed out. Please try again.');
            }

            throw error;
        }
    };

    // ═══════════════════════════════════════════════════════════════════
    // Main Component Class
    // ═══════════════════════════════════════════════════════════════════

    /**
     * SeasonTeamStatsCard Component
     */
    class SeasonTeamStatsCard {
        /**
         * Create a new stats card instance
         * @param {HTMLElement} container - Container element
         * @param {Object} options - Configuration options
         */
        constructor(container, options = {}) {
            if (!container) {
                throw new Error('Container element is required');
            }

            this.container = typeof container === 'string'
                ? document.querySelector(container)
                : container;

            if (!this.container) {
                throw new Error('Container element not found');
            }

            this.options = {
                seasonId: options.seasonId || null,
                teamId: options.teamId || null,
                teamName: options.teamName || 'Team',
                autoLoad: options.autoLoad !== false,
                onLoad: options.onLoad || null,
                onError: options.onError || null
            };

            this.data = null;
            this.isLoading = false;
            this.error = null;

            if (this.options.autoLoad && this.options.seasonId && this.options.teamId) {
                this.load();
            }
        }

        /**
         * Load team stats from API
         * @returns {Promise<void>}
         */
        async load() {
            if (!this.options.seasonId || !this.options.teamId) {
                this.renderError('Season ID and Team ID are required');
                return;
            }

            this.isLoading = true;
            this.error = null;
            this.render();

            try {
                this.data = await fetchSeasonTeamStats(
                    this.options.seasonId,
                    this.options.teamId
                );

                this.isLoading = false;
                this.render();

                if (typeof this.options.onLoad === 'function') {
                    this.options.onLoad(this.data);
                }
            } catch (error) {
                this.isLoading = false;
                this.error = error.message || 'Failed to load team statistics';
                this.render();

                if (typeof this.options.onError === 'function') {
                    this.options.onError(error);
                }
            }
        }

        /**
         * Render the component
         */
        render() {
            // Clear container
            this.container.innerHTML = '';

            let element;

            if (this.isLoading) {
                element = renderSkeleton();
            } else if (this.error) {
                element = renderError(this.error, () => this.load());
            } else if (this.data) {
                element = renderStatsCard(this.data, this.options.teamName);
            } else {
                element = renderSkeleton();
            }

            this.container.appendChild(element);
        }

        /**
         * Update options and reload
         * @param {Object} newOptions - New options to merge
         */
        update(newOptions) {
            Object.assign(this.options, newOptions);
            this.load();
        }

        /**
         * Set data directly (without API call)
         * @param {Object} data - Stats data
         */
        setData(data) {
            this.data = data;
            this.isLoading = false;
            this.error = null;
            this.render();
        }

        /**
         * Destroy the component
         */
        destroy() {
            this.container.innerHTML = '';
            this.data = null;
            this.options = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════

    return {
        // Main component class
        Card: SeasonTeamStatsCard,

        // Utility functions
        classifyEloStrength,
        getStreakBadgeClass,
        getStreakDescription,
        getFormDotClass,
        getFormResultLabel,

        // API function
        fetchStats: fetchSeasonTeamStats,

        // Render functions (for custom usage)
        renderSkeleton,
        renderError,
        renderStatsCard,

        // Quick initialization
        init: (container, options) => new SeasonTeamStatsCard(container, options)
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = SeasonTeamStats;
}


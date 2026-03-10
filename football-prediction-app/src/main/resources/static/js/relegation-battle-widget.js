/**
 * Relegation Battle Widget
 * =========================
 * Displays the relegation battle (fight to avoid drop) with:
 * - Position badges and points
 * - Gap to safety line (color-coded)
 * - Survival probability bars (animated)
 * - Desperation indicators for teams in trouble
 *
 * Features:
 * - Danger zone styling (red/white striped background)
 * - Warning icons for teams in relegation zone
 * - Color-coded status indicators
 * - Compact design for dashboard integration
 */

(function() {
    'use strict';

    const RELEGATION_CONFIG = {
        // Number of teams to display (positions 14-20)
        teamsToShow: 7,
        // Points thresholds
        pointsThresholds: {
            safe: 40,      // Green - essentially safe
            fighting: 30,  // Yellow - fighting for survival
            danger: 20     // Red - in serious danger
        },
        // Probability thresholds for status colors
        probabilityThresholds: {
            safe: 85,
            fighting: 50,
            danger: 25
        }
    };

    /**
     * RelegationBattleWidget class for rendering the relegation battle
     */
    class RelegationBattleWidget {
        constructor(containerId) {
            this.containerId = containerId;
            this.container = null;
            this.data = null;
            this.api = window.api || window.apiClient;
        }

        /**
         * Initialize and render the widget
         */
        async init() {
            this.container = document.getElementById(this.containerId);
            if (!this.container) {
                console.warn(`[RelegationBattle] Container #${this.containerId} not found`);
                return;
            }

            await this.loadData();
        }

        /**
         * Load relegation battle data from API
         */
        async loadData() {
            if (!this.container) return;

            this.container.innerHTML = this.renderLoading();

            try {
                this.data = await this.api.getRelegationBattle();
                this.render();
            } catch (error) {
                console.error('[RelegationBattle] Failed to load data:', error);
                this.container.innerHTML = this.renderError('Failed to load relegation battle data');
            }
        }

        /**
         * Render loading state
         */
        renderLoading() {
            return `
                <div class="relegation-battle-loading">
                    <div class="relegation-loading-spinner"></div>
                    <span>Loading relegation battle...</span>
                </div>
            `;
        }

        /**
         * Render error state
         */
        renderError(message) {
            return `
                <div class="relegation-battle-error">
                    <span class="relegation-error-icon">⚠️</span>
                    <span class="relegation-error-message">${message}</span>
                    <button onclick="window.relegationBattleWidget?.loadData()" class="relegation-retry-btn">Retry</button>
                </div>
            `;
        }

        /**
         * Render the complete widget
         */
        render() {
            if (!this.container || !this.data) return;

            const teams = this.data.teamsInBattle?.slice(0, RELEGATION_CONFIG.teamsToShow) || [];
            const summary = this.data.summary || {};

            this.container.innerHTML = `
                <div class="relegation-battle-widget">
                    <div class="relegation-battle-header">
                        <h3 class="relegation-battle-title">
                            <span class="relegation-battle-icon">⚠️</span>
                            Relegation Battle
                        </h3>
                        <div class="relegation-battle-header-meta">
                            <span class="relegation-battle-season">${this.data.season || ''}</span>
                            ${this.renderIntensityBadge(summary.intensity)}
                        </div>
                    </div>

                    <div class="relegation-battle-summary">
                        ${this.renderSummaryStats(summary)}
                    </div>

                    <div class="relegation-battle-teams">
                        ${teams.map((team, index) => this.renderTeamRow(team, index)).join('')}
                    </div>

                    <div class="relegation-battle-footer">
                        <span class="relegation-progress-text">
                            ${this.data.matchdaysCompleted || 0}/${this.data.totalMatchesInSeason || 38} played
                        </span>
                        <span class="relegation-survival-target">
                            Target: ${this.data.survivalPointsTarget || 38} pts
                        </span>
                    </div>
                </div>
            `;

            // Add animation after render
            this.animateProbabilityBars();
        }

        /**
         * Render intensity badge
         */
        renderIntensityBadge(intensity) {
            if (!intensity) return '';

            const intensityClasses = {
                'Calm': 'intensity-calm',
                'Tense': 'intensity-tense',
                'Critical': 'intensity-critical',
                'Dramatic': 'intensity-dramatic'
            };

            const className = intensityClasses[intensity] || 'intensity-default';

            return `<span class="relegation-intensity-badge ${className}">${intensity}</span>`;
        }

        /**
         * Render summary statistics
         */
        renderSummaryStats(summary) {
            return `
                <div class="relegation-summary-stats">
                    <div class="summary-stat">
                        <span class="summary-stat-value">${summary.safetyLinePoints || 0}</span>
                        <span class="summary-stat-label">17th pts</span>
                    </div>
                    <div class="summary-stat-divider">
                        <span class="gap-indicator ${summary.gapAtRelegationLine <= 2 ? 'gap-tight' : ''}">
                            ${summary.gapAtRelegationLine || 0} pt gap
                        </span>
                    </div>
                    <div class="summary-stat danger">
                        <span class="summary-stat-value">${summary.relegationLinePoints || 0}</span>
                        <span class="summary-stat-label">18th pts</span>
                    </div>
                </div>
            `;
        }

        /**
         * Render a single team row
         */
        renderTeamRow(team, index) {
            const position = team.currentPosition || (14 + index);
            const isInRelegationZone = position >= 18;
            const isOnSafetyLine = position === 17;
            const statusClass = this.getStatusClass(team.status);
            const desperationClass = this.getDesperationClass(team.desperationLevel);
            const probabilityClass = this.getProbabilityClass(team.survivalProbability);

            return `
                <div class="relegation-team ${isInRelegationZone ? 'in-relegation-zone' : ''} ${isOnSafetyLine ? 'on-safety-line' : ''}"
                     data-position="${position}">
                    <div class="relegation-team-position">
                        ${this.renderPositionBadge(position, team.status)}
                    </div>

                    <div class="relegation-team-info">
                        ${team.teamLogo ? `
                            <img src="${team.teamLogo}"
                                 alt="${team.teamName}"
                                 class="relegation-team-logo"
                                 onerror="this.src='https://cdn-icons-png.flaticon.com/512/861/861512.png'">
                        ` : ''}
                        <span class="relegation-team-name">${team.teamName}</span>
                        ${this.renderDesperationIndicator(team.desperationLevel)}
                    </div>

                    <div class="relegation-points">
                        <span class="points-value">${team.points}</span>
                        <span class="points-label">pts</span>
                    </div>

                    <div class="relegation-gap">
                        ${this.renderGapToSafety(team.gapToSafety, isInRelegationZone)}
                    </div>

                    <div class="relegation-probability">
                        <div class="probability-bar-container">
                            <div class="probability-bar ${probabilityClass}"
                                 data-probability="${team.survivalProbability || 0}"
                                 style="width: 0%">
                            </div>
                        </div>
                        <span class="probability-text">${Math.round(team.survivalProbability || 0)}%</span>
                    </div>
                </div>
            `;
        }

        /**
         * Render position badge with status icons
         */
        renderPositionBadge(position, status) {
            let icon = '';
            let badgeClass = 'position-default';

            if (status === 'Relegated') {
                icon = '💀';
                badgeClass = 'position-relegated';
            } else if (status === 'Danger') {
                icon = '🔥';
                badgeClass = 'position-danger';
            } else if (status === 'Fighting') {
                icon = '⚔️';
                badgeClass = 'position-fighting';
            } else if (status === 'Safe') {
                icon = '✓';
                badgeClass = 'position-safe';
            } else {
                icon = position;
            }

            return `<span class="position-badge ${badgeClass}">${icon}</span>`;
        }

        /**
         * Render gap to safety indicator
         */
        renderGapToSafety(gap, isInRelegationZone) {
            if (gap === undefined || gap === null) {
                return '<span class="gap-value">-</span>';
            }

            const absGap = Math.abs(gap);
            let gapClass = '';
            let prefix = '';

            if (gap > 0) {
                prefix = '+';
                gapClass = 'gap-safe';
            } else if (gap === 0) {
                gapClass = 'gap-even';
            } else {
                prefix = '';
                gapClass = 'gap-danger';
            }

            return `
                <span class="gap-value ${gapClass}">
                    ${prefix}${gap}
                </span>
            `;
        }

        /**
         * Render desperation indicator
         */
        renderDesperationIndicator(level) {
            if (!level || level === 'Low') return '';

            const indicators = {
                'Medium': '⚡',
                'High': '🔥',
                'Extreme': '💀'
            };

            return `<span class="desperation-indicator desperation-${level.toLowerCase()}" title="${level} desperation">${indicators[level] || ''}</span>`;
        }

        /**
         * Get CSS class based on status
         */
        getStatusClass(status) {
            const statusMap = {
                'Safe': 'status-safe',
                'Fighting': 'status-fighting',
                'Danger': 'status-danger',
                'Relegated': 'status-relegated'
            };
            return statusMap[status] || 'status-default';
        }

        /**
         * Get CSS class based on desperation level
         */
        getDesperationClass(level) {
            const levelMap = {
                'Low': 'desperation-low',
                'Medium': 'desperation-medium',
                'High': 'desperation-high',
                'Extreme': 'desperation-extreme'
            };
            return levelMap[level] || 'desperation-low';
        }

        /**
         * Get CSS class based on survival probability
         */
        getProbabilityClass(probability) {
            if (probability >= RELEGATION_CONFIG.probabilityThresholds.safe) {
                return 'prob-safe';
            } else if (probability >= RELEGATION_CONFIG.probabilityThresholds.fighting) {
                return 'prob-fighting';
            } else if (probability >= RELEGATION_CONFIG.probabilityThresholds.danger) {
                return 'prob-danger';
            }
            return 'prob-relegated';
        }

        /**
         * Animate probability bars after render
         */
        animateProbabilityBars() {
            setTimeout(() => {
                const bars = this.container.querySelectorAll('.probability-bar');
                bars.forEach(bar => {
                    const probability = bar.dataset.probability || 0;
                    bar.style.width = `${probability}%`;
                });
            }, 100);
        }
    }

    // Expose globally
    window.RelegationBattleWidget = RelegationBattleWidget;

    // Auto-initialize if container exists
    document.addEventListener('DOMContentLoaded', () => {
        const container = document.getElementById('relegationBattleWidget');
        if (container) {
            const widget = new RelegationBattleWidget('relegationBattleWidget');
            window.relegationBattleWidget = widget;
            widget.init();
        }
    });

})();


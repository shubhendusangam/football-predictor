/**
 * Top 4 Race Widget
 * ==================
 * Displays the Champions League race (top 4 battle) with:
 * - Position badges and points
 * - Gap to 4th place (color-coded)
 * - Probability bars (animated)
 * - Tension indicators for teams on the edge
 *
 * Features:
 * - Podium icons for top 3
 * - UEFA Champions League indicator for top 4
 * - Red/yellow/green tension indicators
 */

(function() {
    'use strict';

    const TOP4_CONFIG = {
        // Number of teams to display (top 7 = title + top 4 + chasers)
        teamsToShow: 7,
        // Probability thresholds for status colors
        probabilityThresholds: {
            safe: 85,      // Green - UCL likely
            fighting: 50,  // Yellow - In contention
            unlikely: 25   // Red - Tough battle
        }
    };

    /**
     * Top4RaceWidget class for rendering the Champions League race
     */
    class Top4RaceWidget {
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
                console.warn(`[Top4Race] Container #${this.containerId} not found`);
                return;
            }

            await this.loadData();
        }

        /**
         * Load Top 4 race data from API
         */
        async loadData() {
            if (!this.container) return;

            this.container.innerHTML = this.renderLoading();

            try {
                this.data = await this.api.getTop4Race();
                this.render();
            } catch (error) {
                console.error('[Top4Race] Failed to load data:', error);
                this.container.innerHTML = this.renderError('Failed to load Top 4 race data');
            }
        }

        /**
         * Render the complete widget
         */
        render() {
            if (!this.container || !this.data) return;

            const teams = this.data.teamsInRace?.slice(0, TOP4_CONFIG.teamsToShow) || [];
            const titleRace = this.data.titleRace || {};

            this.container.innerHTML = `
                <div class="top4-race-widget top4-race-compact">
                    <div class="top4-race-header">
                        <h3 class="top4-race-title">
                            <span class="top4-race-icon">🏆</span>
                            UCL Race
                        </h3>
                        <div class="top4-race-header-meta">
                            <span class="top4-race-season">${this.data.season || ''}</span>
                            ${this.renderTitleRaceIndicator(titleRace)}
                        </div>
                    </div>

                    <div class="top4-race-teams">
                        ${teams.map((team, index) => this.renderTeamRow(team, index)).join('')}
                    </div>

                    <div class="top4-race-footer">
                        <span class="top4-race-progress-text">
                            ${this.data.matchdaysCompleted || 0}/${this.data.totalMatchesInSeason || 38} played
                        </span>
                        <span class="top4-race-safety-points">Target: ${this.data.pointsForSafety || 72} pts</span>
                    </div>
                </div>
            `;

            // Add animation after render
            this.animateProbabilityBars();
        }

        /**
         * Render a single team row
         */
        renderTeamRow(team, index) {
            const position = team.currentPosition || (index + 1);
            const isTop4 = position <= 4;
            const isOnEdge = position === 4 || position === 5;
            const tensionClass = this.getTensionClass(team.top4Probability);

            return `
                <div class="top4-race-team ${isTop4 ? 'in-top4' : 'outside-top4'} ${isOnEdge ? 'on-edge' : ''}"
                     data-position="${position}">
                    <div class="top4-race-team-position">
                        ${this.renderPositionBadge(position)}
                    </div>

                    <div class="top4-race-team-info">
                        ${team.teamLogo ? `
                            <img src="${team.teamLogo}"
                                 alt="${team.teamName}"
                                 class="top4-race-team-logo"
                                 onerror="this.src='https://cdn-icons-png.flaticon.com/512/861/861512.png'">
                        ` : ''}
                        <span class="top4-race-team-name">${team.teamName}</span>
                    </div>

                    <div class="top4-race-points">
                        <span class="points-value">${team.points}</span>
                    </div>

                    <div class="top4-race-probability">
                        <div class="probability-bar-container">
                            <div class="probability-bar ${tensionClass}"
                                 data-probability="${team.top4Probability || 0}"
                                 style="width: 0%">
                            </div>
                        </div>
                        <span class="probability-text">${Math.round(team.top4Probability || 0)}%</span>
                    </div>
                </div>
            `;
        }

        /**
         * Render position badge with special icons
         */
        renderPositionBadge(position) {
            if (position === 1) {
                return '<span class="position-badge position-1">🥇</span>';
            } else if (position === 2) {
                return '<span class="position-badge position-2">🥈</span>';
            } else if (position === 3) {
                return '<span class="position-badge position-3">🥉</span>';
            } else if (position === 4) {
                return '<span class="position-badge position-4 ucl">⚽</span>';
            } else {
                return `<span class="position-badge position-default">${position}</span>`;
            }
        }

        /**
         * Render gap to 4th place
         */
        renderGapToFourth(gap, isTop4) {
            if (gap === undefined || gap === null) {
                return '<span class="gap-value">-</span>';
            }

            const absGap = Math.abs(gap);
            if (absGap === 0) {
                return '<span class="gap-value gap-even">EVEN</span>';
            }

            const prefix = isTop4 ? '+' : '-';
            const direction = isTop4 ? 'ahead' : 'behind';

            return `
                <span class="gap-value ${direction}">
                    ${prefix}${absGap}
                </span>
                <span class="gap-label">to 4th</span>
            `;
        }

        /**
         * Render title race indicator
         */
        renderTitleRaceIndicator(titleRace) {
            if (!titleRace || !titleRace.intensity) {
                return '';
            }

            const intensityClass = titleRace.intensity.toLowerCase().replace(/\s+/g, '-');

            return `
                <div class="title-race-indicator ${intensityClass}">
                    <span class="title-race-label">${titleRace.intensity}</span>
                    ${titleRace.contenders > 1 ? `
                        <span class="title-race-contenders">${titleRace.contenders} contenders</span>
                    ` : ''}
                </div>
            `;
        }

        /**
         * Get tension class based on probability
         */
        getTensionClass(probability) {
            if (probability >= TOP4_CONFIG.probabilityThresholds.safe) {
                return 'tension-green';
            } else if (probability >= TOP4_CONFIG.probabilityThresholds.fighting) {
                return 'tension-yellow';
            } else if (probability >= TOP4_CONFIG.probabilityThresholds.unlikely) {
                return 'tension-orange';
            }
            return 'tension-red';
        }

        /**
         * Get gap class for styling
         */
        getGapClass(gap, isTop4) {
            if (gap === 0) return 'gap-even';
            return isTop4 ? 'gap-positive' : 'gap-negative';
        }

        /**
         * Get status class for badge styling
         */
        getStatusClass(status) {
            if (!status) return '';
            return `status-${status.toLowerCase().replace(/\s+/g, '-')}`;
        }

        /**
         * Animate probability bars on render
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

        /**
         * Render loading state
         */
        renderLoading() {
            return `
                <div class="top4-race-widget loading">
                    <div class="top4-race-header">
                        <h3 class="top4-race-title">
                            <span class="top4-race-icon">🏆</span>
                            Champions League Race
                        </h3>
                    </div>
                    <div class="loading-skeleton">
                        <div class="skeleton-team"></div>
                        <div class="skeleton-team"></div>
                        <div class="skeleton-team"></div>
                        <div class="skeleton-team"></div>
                        <div class="skeleton-team"></div>
                    </div>
                </div>
            `;
        }

        /**
         * Render error state
         */
        renderError(message) {
            return `
                <div class="top4-race-widget error">
                    <div class="top4-race-header">
                        <h3 class="top4-race-title">
                            <span class="top4-race-icon">🏆</span>
                            Champions League Race
                        </h3>
                    </div>
                    <div class="error-message">
                        <span class="error-icon">⚠️</span>
                        <p>${message}</p>
                        <button onclick="window.top4RaceWidget?.loadData()" class="retry-btn">
                            Retry
                        </button>
                    </div>
                </div>
            `;
        }

        /**
         * Refresh the widget data
         */
        async refresh() {
            await this.loadData();
        }
    }

    // =====================================================
    // CSS Styles (injected)
    // =====================================================
    function injectStyles() {
        if (document.getElementById('top4-race-styles')) return;

        const styles = document.createElement('style');
        styles.id = 'top4-race-styles';
        styles.textContent = `
            .top4-race-widget {
                background: var(--card-bg, #1a1a2e);
                border-radius: 12px;
                padding: 1.25rem;
                color: var(--text-primary, #fff);
            }

            .top4-race-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 1rem;
            }

            .top4-race-header-left {
                display: flex;
                align-items: center;
                gap: 0.75rem;
            }

            .top4-race-title {
                font-size: 1.1rem;
                font-weight: 600;
                margin: 0;
                display: flex;
                align-items: center;
                gap: 0.5rem;
            }

            .top4-race-icon {
                font-size: 1.25rem;
            }

            .top4-race-season {
                background: var(--badge-bg, rgba(99, 102, 241, 0.2));
                color: var(--badge-text, #818cf8);
                padding: 0.15rem 0.4rem;
                border-radius: 4px;
                font-size: 0.65rem;
                font-weight: 500;
            }

            /* Compact mode */
            .top4-race-compact {
                padding: 0.75rem;
            }

            .top4-race-compact .top4-race-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 0.5rem;
            }

            .top4-race-compact .top4-race-title {
                font-size: 0.9rem;
                gap: 0.35rem;
            }

            .top4-race-compact .top4-race-icon {
                font-size: 1rem;
            }

            .top4-race-header-meta {
                display: flex;
                align-items: center;
                gap: 0.5rem;
            }

            .top4-race-footer {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-top: 0.5rem;
                padding-top: 0.5rem;
                border-top: 1px solid var(--border-color, rgba(255, 255, 255, 0.1));
                font-size: 0.65rem;
                color: var(--text-muted, #9ca3af);
            }

            .top4-race-footer .top4-race-safety-points {
                font-size: 0.65rem;
                color: var(--success-color, #34d399);
            }

            .top4-race-progress {
                margin-bottom: 0.75rem;
            }

            .top4-race-progress-bar {
                height: 4px;
                background: var(--progress-bg, rgba(255, 255, 255, 0.1));
                border-radius: 2px;
                overflow: hidden;
            }

            .top4-race-progress-fill {
                height: 100%;
                background: linear-gradient(90deg, var(--accent-color, #6366f1), var(--accent-light, #818cf8));
                border-radius: 2px;
                transition: width 0.5s ease;
            }

            .top4-race-progress-text {
                font-size: 0.7rem;
                color: var(--text-muted, #9ca3af);
                margin-top: 0.25rem;
                display: block;
            }

            .top4-race-safety {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 0.5rem 0.75rem;
                background: var(--safety-bg, rgba(52, 211, 153, 0.1));
                border-radius: 6px;
                margin-bottom: 1rem;
                font-size: 0.8rem;
            }

            .top4-race-safety-points {
                font-weight: 600;
                color: var(--success-color, #34d399);
            }

            .top4-race-teams {
                display: flex;
                flex-direction: column;
                gap: 0.25rem;
            }

            .top4-race-team {
                display: grid;
                grid-template-columns: 28px 1fr 45px 80px;
                align-items: center;
                gap: 0.5rem;
                padding: 0.4rem 0.5rem;
                background: var(--team-bg, rgba(255, 255, 255, 0.03));
                border-radius: 6px;
                transition: all 0.2s ease;
            }

            .top4-race-team:hover {
                background: var(--team-bg-hover, rgba(255, 255, 255, 0.06));
            }

            .top4-race-team.in-top4 {
                border-left: 2px solid var(--ucl-color, #6366f1);
            }

            .top4-race-team.on-edge {
                animation: pulse-edge 2s ease-in-out infinite;
            }

            @keyframes pulse-edge {
                0%, 100% { box-shadow: 0 0 0 0 rgba(251, 191, 36, 0.4); }
                50% { box-shadow: 0 0 0 4px rgba(251, 191, 36, 0); }
            }

            .top4-race-team-position {
                display: flex;
                justify-content: center;
            }

            .position-badge {
                width: 22px;
                height: 22px;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 50%;
                font-size: 0.85rem;
            }

            .position-badge.position-1 { font-size: 1rem; }
            .position-badge.position-2 { font-size: 1rem; }
            .position-badge.position-3 { font-size: 1rem; }
            .position-badge.position-4.ucl { font-size: 0.85rem; }
            .position-badge.position-default {
                background: var(--position-bg, rgba(255, 255, 255, 0.1));
                font-size: 0.7rem;
                font-weight: 600;
            }

            .top4-race-team-info {
                display: flex;
                align-items: center;
                gap: 0.35rem;
                min-width: 0;
            }

            .top4-race-team-logo {
                width: 18px;
                height: 18px;
                object-fit: contain;
                flex-shrink: 0;
            }

            .top4-race-team-name {
                font-weight: 500;
                font-size: 0.8rem;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }

            .top4-race-points {
                text-align: center;
            }

            .points-value {
                font-size: 0.85rem;
                font-weight: 700;
            }

            .points-label {
                font-size: 0.65rem;
                color: var(--text-muted, #9ca3af);
                display: block;
            }

            .top4-race-gap {
                text-align: center;
                min-width: 60px;
            }

            .gap-value {
                font-size: 0.85rem;
                font-weight: 600;
            }

            .gap-value.ahead {
                color: var(--success-color, #34d399);
            }

            .gap-value.behind {
                color: var(--danger-color, #f87171);
            }

            .gap-value.gap-even {
                color: var(--warning-color, #fbbf24);
            }

            .gap-label {
                font-size: 0.6rem;
                color: var(--text-muted, #9ca3af);
                display: block;
            }

            .top4-race-probability {
                display: flex;
                align-items: center;
                gap: 0.35rem;
            }

            .probability-bar-container {
                flex: 1;
                height: 6px;
                background: var(--bar-bg, rgba(255, 255, 255, 0.1));
                border-radius: 3px;
                overflow: hidden;
            }

            .probability-bar {
                height: 100%;
                border-radius: 3px;
                transition: width 0.8s ease-out;
            }

            .probability-bar.tension-green {
                background: linear-gradient(90deg, #10b981, #34d399);
            }

            .probability-bar.tension-yellow {
                background: linear-gradient(90deg, #f59e0b, #fbbf24);
            }

            .probability-bar.tension-orange {
                background: linear-gradient(90deg, #f97316, #fb923c);
            }

            .probability-bar.tension-red {
                background: linear-gradient(90deg, #ef4444, #f87171);
            }

            .probability-text {
                font-size: 0.7rem;
                font-weight: 600;
                min-width: 28px;
                text-align: right;
            }

            .top4-race-status {
                display: flex;
                align-items: center;
                gap: 0.5rem;
            }

            .status-badge {
                padding: 0.2rem 0.5rem;
                border-radius: 4px;
                font-size: 0.65rem;
                font-weight: 600;
                text-transform: uppercase;
            }

            .status-badge.status-champion {
                background: linear-gradient(135deg, #f59e0b, #fbbf24);
                color: #1a1a2e;
            }

            .status-badge.status-ucl-safe {
                background: rgba(16, 185, 129, 0.2);
                color: #34d399;
            }

            .status-badge.status-fighting {
                background: rgba(251, 191, 36, 0.2);
                color: #fbbf24;
            }

            .status-badge.status-unlikely {
                background: rgba(239, 68, 68, 0.2);
                color: #f87171;
            }

            .tension-indicator {
                width: 8px;
                height: 8px;
                border-radius: 50%;
            }

            .tension-indicator.tension-green {
                background: #34d399;
                box-shadow: 0 0 8px #34d399;
            }

            .tension-indicator.tension-yellow {
                background: #fbbf24;
                box-shadow: 0 0 8px #fbbf24;
            }

            .tension-indicator.tension-orange {
                background: #fb923c;
                box-shadow: 0 0 8px #fb923c;
            }

            .tension-indicator.tension-red {
                background: #f87171;
                box-shadow: 0 0 8px #f87171;
            }

            .title-race-indicator {
                padding: 0.25rem 0.75rem;
                border-radius: 6px;
                font-size: 0.75rem;
                font-weight: 500;
                text-align: center;
            }

            .title-race-indicator.wide-open {
                background: rgba(99, 102, 241, 0.2);
                color: #818cf8;
            }

            .title-race-indicator.close-race {
                background: rgba(251, 191, 36, 0.2);
                color: #fbbf24;
            }

            .title-race-indicator.comfortable-lead {
                background: rgba(16, 185, 129, 0.2);
                color: #34d399;
            }

            .title-race-indicator.decided {
                background: rgba(107, 114, 128, 0.2);
                color: #9ca3af;
            }

            .title-race-contenders {
                display: block;
                font-size: 0.65rem;
                opacity: 0.8;
            }

            .top4-race-legend {
                display: flex;
                justify-content: center;
                gap: 1rem;
                margin-top: 1rem;
                padding-top: 0.75rem;
                border-top: 1px solid var(--border-color, rgba(255, 255, 255, 0.1));
            }

            .top4-race-legend-item {
                display: flex;
                align-items: center;
                gap: 0.25rem;
                font-size: 0.7rem;
                color: var(--text-muted, #9ca3af);
            }

            .legend-icon {
                font-size: 0.9rem;
            }

            .tension-dot {
                width: 8px;
                height: 8px;
                border-radius: 50%;
            }

            .tension-dot.tension-green { background: #34d399; }
            .tension-dot.tension-yellow { background: #fbbf24; }
            .tension-dot.tension-red { background: #f87171; }

            /* Loading skeleton */
            .loading-skeleton .skeleton-team {
                height: 50px;
                background: linear-gradient(90deg,
                    rgba(255,255,255,0.03) 25%,
                    rgba(255,255,255,0.08) 50%,
                    rgba(255,255,255,0.03) 75%);
                background-size: 200% 100%;
                animation: skeleton-loading 1.5s infinite;
                border-radius: 8px;
                margin-bottom: 0.5rem;
            }

            @keyframes skeleton-loading {
                0% { background-position: 200% 0; }
                100% { background-position: -200% 0; }
            }

            /* Error state */
            .top4-race-widget.error .error-message {
                text-align: center;
                padding: 2rem;
            }

            .error-message .error-icon {
                font-size: 2rem;
                display: block;
                margin-bottom: 0.5rem;
            }

            .error-message p {
                color: var(--text-muted, #9ca3af);
                margin-bottom: 1rem;
            }

            .retry-btn {
                background: var(--accent-color, #6366f1);
                color: white;
                border: none;
                padding: 0.5rem 1rem;
                border-radius: 6px;
                cursor: pointer;
                font-size: 0.85rem;
            }

            .retry-btn:hover {
                background: var(--accent-light, #818cf8);
            }

            /* Responsive */
            @media (max-width: 768px) {
                .top4-race-team {
                    grid-template-columns: 35px 1fr 70px 80px;
                    gap: 0.5rem;
                    padding: 0.5rem;
                }

                .top4-race-status {
                    display: none;
                }

                .top4-race-team-name {
                    font-size: 0.85rem;
                }
            }

            @media (max-width: 480px) {
                .top4-race-team {
                    grid-template-columns: 30px 1fr 60px;
                }

                .top4-race-probability {
                    display: none;
                }

                .top4-race-legend {
                    flex-wrap: wrap;
                }
            }
        `;
        document.head.appendChild(styles);
    }

    // =====================================================
    // Initialize and Export
    // =====================================================

    // Inject styles on load
    injectStyles();

    // Export to global scope
    window.Top4RaceWidget = Top4RaceWidget;

    // Create default instance
    window.top4RaceWidget = new Top4RaceWidget('top4RaceCard');

    console.log('[Top4Race] Widget module loaded');

})();


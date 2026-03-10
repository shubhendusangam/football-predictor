/**
 * Relegation Zone Chart
 * ======================
 * Area chart showing points progression for bottom 6 teams
 * with survival target line (35-40 points).
 *
 * Uses simple SVG rendering (no external dependencies).
 */

(function() {
    'use strict';

    const CHART_CONFIG = {
        // Chart dimensions
        width: 400,
        height: 200,
        padding: { top: 20, right: 30, bottom: 30, left: 40 },
        // Survival target
        survivalTarget: 38,
        minSurvivalTarget: 35,
        maxSurvivalTarget: 40,
        // Colors for teams
        teamColors: [
            '#f87171', // Red - danger
            '#fb923c', // Orange
            '#fbbf24', // Yellow
            '#a3e635', // Lime
            '#4ade80', // Green
            '#22d3ee'  // Cyan
        ]
    };

    /**
     * RelegationZoneChart class for rendering the points progression
     */
    class RelegationZoneChart {
        constructor(containerId) {
            this.containerId = containerId;
            this.container = null;
            this.data = null;
            this.api = window.api || window.apiClient;
        }

        /**
         * Initialize and render the chart
         */
        async init() {
            this.container = document.getElementById(this.containerId);
            if (!this.container) {
                console.warn(`[RelegationZoneChart] Container #${this.containerId} not found`);
                return;
            }

            await this.loadData();
        }

        /**
         * Load data from API
         */
        async loadData() {
            if (!this.container) return;

            this.container.innerHTML = this.renderLoading();

            try {
                this.data = await this.api.getRelegationBattle();
                this.render();
            } catch (error) {
                console.error('[RelegationZoneChart] Failed to load data:', error);
                this.container.innerHTML = this.renderError('Failed to load chart data');
            }
        }

        /**
         * Render loading state
         */
        renderLoading() {
            return `
                <div class="relegation-chart-loading">
                    <div class="chart-loading-spinner"></div>
                    <span>Loading chart...</span>
                </div>
            `;
        }

        /**
         * Render error state
         */
        renderError(message) {
            return `
                <div class="relegation-chart-error">
                    <span class="chart-error-icon">⚠️</span>
                    <span>${message}</span>
                </div>
            `;
        }

        /**
         * Render the chart
         */
        render() {
            if (!this.container || !this.data) return;

            const teams = this.data.teamsInBattle?.slice(-6) || []; // Bottom 6
            if (teams.length === 0) {
                this.container.innerHTML = '<p class="chart-no-data">No data available</p>';
                return;
            }

            const matchesPlayed = this.data.matchdaysCompleted || 0;
            const totalMatches = this.data.totalMatchesInSeason || 38;
            const survivalTarget = this.data.survivalPointsTarget || CHART_CONFIG.survivalTarget;

            // Calculate chart dimensions
            const width = CHART_CONFIG.width;
            const height = CHART_CONFIG.height;
            const { top, right, bottom, left } = CHART_CONFIG.padding;
            const chartWidth = width - left - right;
            const chartHeight = height - top - bottom;

            // Scale calculations
            const maxPoints = Math.max(survivalTarget + 5, ...teams.map(t => t.points));
            const xScale = chartWidth / totalMatches;
            const yScale = chartHeight / maxPoints;

            // Generate SVG
            const svg = `
                <svg width="100%" height="${height}" viewBox="0 0 ${width} ${height}" class="relegation-zone-chart">
                    <defs>
                        <!-- Danger zone gradient -->
                        <linearGradient id="dangerZone" x1="0%" y1="0%" x2="0%" y2="100%">
                            <stop offset="0%" stop-color="rgba(248, 113, 113, 0.1)"/>
                            <stop offset="100%" stop-color="rgba(248, 113, 113, 0.3)"/>
                        </linearGradient>
                        <!-- Safety zone gradient -->
                        <linearGradient id="safetyZone" x1="0%" y1="0%" x2="0%" y2="100%">
                            <stop offset="0%" stop-color="rgba(52, 211, 153, 0.2)"/>
                            <stop offset="100%" stop-color="rgba(52, 211, 153, 0.05)"/>
                        </linearGradient>
                    </defs>

                    <!-- Background zones -->
                    <rect x="${left}" y="${top}" width="${chartWidth}" height="${chartHeight - (survivalTarget * yScale)}"
                          fill="url(#safetyZone)" opacity="0.5"/>
                    <rect x="${left}" y="${top + chartHeight - (survivalTarget * yScale)}" width="${chartWidth}"
                          height="${survivalTarget * yScale}" fill="url(#dangerZone)" opacity="0.5"/>

                    <!-- Grid lines -->
                    ${this.renderGridLines(left, top, chartWidth, chartHeight, maxPoints, yScale)}

                    <!-- Survival target line -->
                    <line x1="${left}" y1="${top + chartHeight - (survivalTarget * yScale)}"
                          x2="${left + chartWidth}" y2="${top + chartHeight - (survivalTarget * yScale)}"
                          stroke="#fbbf24" stroke-width="2" stroke-dasharray="5,5"/>
                    <text x="${left + chartWidth + 5}" y="${top + chartHeight - (survivalTarget * yScale) + 4}"
                          fill="#fbbf24" font-size="10" font-weight="600">${survivalTarget}</text>

                    <!-- Current position marker -->
                    <line x1="${left + matchesPlayed * xScale}" y1="${top}"
                          x2="${left + matchesPlayed * xScale}" y2="${top + chartHeight}"
                          stroke="var(--text-secondary)" stroke-width="1" stroke-dasharray="3,3"/>

                    <!-- Team points markers and projections -->
                    ${teams.map((team, index) => this.renderTeamData(team, index, left, top, chartWidth, chartHeight, matchesPlayed, totalMatches, xScale, yScale, survivalTarget)).join('')}

                    <!-- X-axis -->
                    <line x1="${left}" y1="${top + chartHeight}" x2="${left + chartWidth}" y2="${top + chartHeight}"
                          stroke="var(--border-color)" stroke-width="1"/>
                    <text x="${left + chartWidth / 2}" y="${top + chartHeight + 20}"
                          fill="var(--text-secondary)" font-size="10" text-anchor="middle">Matchday</text>

                    <!-- Y-axis -->
                    <line x1="${left}" y1="${top}" x2="${left}" y2="${top + chartHeight}"
                          stroke="var(--border-color)" stroke-width="1"/>
                    <text x="${left - 25}" y="${top + chartHeight / 2}"
                          fill="var(--text-secondary)" font-size="10" text-anchor="middle"
                          transform="rotate(-90, ${left - 25}, ${top + chartHeight / 2})">Points</text>
                </svg>

                <!-- Legend -->
                <div class="relegation-chart-legend">
                    ${teams.map((team, index) => `
                        <div class="legend-item">
                            <span class="legend-color" style="background: ${CHART_CONFIG.teamColors[index % CHART_CONFIG.teamColors.length]}"></span>
                            <span class="legend-team">${team.teamName}</span>
                            <span class="legend-points">${team.points} pts</span>
                        </div>
                    `).join('')}
                </div>
            `;

            this.container.innerHTML = `
                <div class="relegation-zone-chart-container">
                    <div class="chart-header">
                        <h4 class="chart-title">Points Progression</h4>
                        <span class="chart-subtitle">Bottom 6 teams vs survival target</span>
                    </div>
                    ${svg}
                </div>
            `;
        }

        /**
         * Render grid lines
         */
        renderGridLines(left, top, chartWidth, chartHeight, maxPoints, yScale) {
            const gridLines = [];
            const step = Math.ceil(maxPoints / 5);

            for (let p = 0; p <= maxPoints; p += step) {
                const y = top + chartHeight - (p * yScale);
                gridLines.push(`
                    <line x1="${left}" y1="${y}" x2="${left + chartWidth}" y2="${y}"
                          stroke="var(--border-color)" stroke-width="0.5" opacity="0.3"/>
                    <text x="${left - 5}" y="${y + 3}" fill="var(--text-secondary)"
                          font-size="9" text-anchor="end">${p}</text>
                `);
            }

            return gridLines.join('');
        }

        /**
         * Render team data (point and projection)
         */
        renderTeamData(team, index, left, top, chartWidth, chartHeight, matchesPlayed, totalMatches, xScale, yScale, survivalTarget) {
            const color = CHART_CONFIG.teamColors[index % CHART_CONFIG.teamColors.length];
            const currentX = left + matchesPlayed * xScale;
            const currentY = top + chartHeight - (team.points * yScale);

            // Calculate projected end of season points (PPG * remaining matches)
            const ppg = team.pointsPerGame || (matchesPlayed > 0 ? team.points / matchesPlayed : 0);
            const projectedPoints = Math.round(team.points + (ppg * (totalMatches - matchesPlayed)));
            const projectedY = top + chartHeight - (projectedPoints * yScale);
            const endX = left + chartWidth;

            return `
                <!-- Current point marker -->
                <circle cx="${currentX}" cy="${currentY}" r="6" fill="${color}" stroke="white" stroke-width="2"/>

                <!-- Projection line (dashed) -->
                <line x1="${currentX}" y1="${currentY}" x2="${endX}" y2="${projectedY}"
                      stroke="${color}" stroke-width="2" stroke-dasharray="4,2" opacity="0.7"/>

                <!-- Projected end point -->
                <circle cx="${endX}" cy="${projectedY}" r="4" fill="${color}" opacity="0.5"/>

                <!-- Team label at current position -->
                <text x="${currentX + 8}" y="${currentY - 8}" fill="${color}" font-size="9" font-weight="600">
                    ${team.currentPosition}
                </text>
            `;
        }
    }

    // Expose globally
    window.RelegationZoneChart = RelegationZoneChart;

    // Auto-initialize if container exists
    document.addEventListener('DOMContentLoaded', () => {
        const container = document.getElementById('relegationZoneChart');
        if (container) {
            const chart = new RelegationZoneChart('relegationZoneChart');
            window.relegationZoneChart = chart;
            chart.init();
        }
    });

})();


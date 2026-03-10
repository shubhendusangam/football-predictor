/**
 * Title Race Timeline
 * ====================
 * A line chart component showing points progression for top teams
 * with projections to season end.
 *
 * Features:
 * - Interactive line chart using Chart.js
 * - Points progression for top 3-5 teams
 * - Projected finish based on current pace
 * - Hover tooltips with match details
 */

(function() {
    'use strict';

    /**
     * TitleRaceTimeline class for rendering points progression chart
     */
    class TitleRaceTimeline {
        constructor(containerId) {
            this.containerId = containerId;
            this.container = null;
            this.chart = null;
            this.data = null;
            this.api = window.api || window.apiClient;
            this.teamsToShow = 3; // Show top 3 teams in timeline
        }

        /**
         * Initialize and render the timeline
         */
        async init() {
            this.container = document.getElementById(this.containerId);
            if (!this.container) {
                console.warn(`[TitleTimeline] Container #${this.containerId} not found`);
                return;
            }

            // Check if Chart.js is available
            if (typeof Chart === 'undefined') {
                console.warn('[TitleTimeline] Chart.js not loaded, loading from CDN...');
                await this.loadChartJS();
            }

            await this.loadData();
        }

        /**
         * Load Chart.js dynamically if not present
         */
        async loadChartJS() {
            return new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js';
                script.onload = resolve;
                script.onerror = reject;
                document.head.appendChild(script);
            });
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
                console.error('[TitleTimeline] Failed to load data:', error);
                this.container.innerHTML = this.renderError('Failed to load title race data');
            }
        }

        /**
         * Render the timeline component
         */
        render() {
            if (!this.container || !this.data) return;

            const teams = this.data.teamsInRace?.slice(0, this.teamsToShow) || [];
            const titleRace = this.data.titleRace || {};

            this.container.innerHTML = `
                <div class="title-timeline-widget title-timeline-compact">
                    <div class="title-timeline-header">
                        <h3 class="title-timeline-title">
                            <span class="title-timeline-icon">📈</span>
                            Title Race
                        </h3>
                        ${titleRace.intensity ? `
                            <span class="title-timeline-intensity ${this.getIntensityClass(titleRace.intensity)}">
                                ${titleRace.intensity}
                            </span>
                        ` : ''}
                    </div>

                    <div class="title-timeline-chart-container compact">
                        <canvas id="titleRaceChart"></canvas>
                    </div>

                    <div class="title-timeline-projection compact">
                        ${teams.map((team, index) => `
                            <div class="projection-team-row" style="--team-color: ${this.getTeamColor(index)}">
                                <span class="projection-rank">${index + 1}</span>
                                <span class="projection-team-name">${team.teamName}</span>
                                <span class="projection-team-points">${this.calculateProjectedPoints(team)}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            `;

            // Render chart after DOM is ready
            requestAnimationFrame(() => this.renderChart(teams));
        }

        /**
         * Render the Chart.js line chart
         */
        renderChart(teams) {
            const canvas = document.getElementById('titleRaceChart');
            if (!canvas || typeof Chart === 'undefined') {
                console.warn('[TitleTimeline] Cannot render chart');
                return;
            }

            const ctx = canvas.getContext('2d');
            const matchdays = this.data.matchdaysCompleted || 0;
            const totalMatches = this.data.totalMatchesInSeason || 38;

            // Generate labels (matchday numbers)
            const labels = [];
            for (let i = 1; i <= totalMatches; i++) {
                labels.push(`MD ${i}`);
            }

            // Generate datasets
            const datasets = teams.map((team, index) => {
                const color = this.getTeamColor(index);
                const currentPoints = team.points || 0;
                const ppg = team.pointsPerGame || (matchdays > 0 ? currentPoints / matchdays : 2);

                // Generate data points
                const data = [];
                for (let i = 1; i <= totalMatches; i++) {
                    if (i <= matchdays) {
                        // Actual progress (simplified - linear interpolation)
                        data.push(Math.round((currentPoints / matchdays) * i));
                    } else {
                        // Projected points based on PPG
                        const projectedAdditional = ppg * (i - matchdays);
                        data.push(Math.round(currentPoints + projectedAdditional));
                    }
                }

                return {
                    label: team.teamName,
                    data: data,
                    borderColor: color,
                    backgroundColor: `${color}20`,
                    borderWidth: i <= matchdays ? 3 : 1,
                    borderDash: [], // Will be set dynamically
                    pointRadius: 0,
                    pointHoverRadius: 5,
                    tension: 0.3,
                    fill: false,
                    segment: {
                        borderDash: ctx => ctx.p0DataIndex >= matchdays - 1 ? [5, 5] : []
                    }
                };
            });

            // Destroy existing chart if any
            if (this.chart) {
                this.chart.destroy();
            }

            // Create new chart
            this.chart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: datasets
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    interaction: {
                        mode: 'index',
                        intersect: false
                    },
                    plugins: {
                        legend: {
                            display: false
                        },
                        tooltip: {
                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                            titleColor: '#fff',
                            bodyColor: '#fff',
                            padding: 12,
                            displayColors: true,
                            callbacks: {
                                title: (items) => {
                                    const mdIndex = items[0].dataIndex;
                                    const isProjected = mdIndex >= matchdays;
                                    return `Matchday ${mdIndex + 1}${isProjected ? ' (Projected)' : ''}`;
                                },
                                label: (item) => {
                                    return `${item.dataset.label}: ${item.raw} pts`;
                                }
                            }
                        },
                        annotation: {
                            annotations: {
                                currentLine: {
                                    type: 'line',
                                    xMin: matchdays - 0.5,
                                    xMax: matchdays - 0.5,
                                    borderColor: 'rgba(255, 255, 255, 0.3)',
                                    borderWidth: 2,
                                    borderDash: [6, 6],
                                    label: {
                                        display: true,
                                        content: 'Now',
                                        position: 'start',
                                        backgroundColor: 'rgba(0, 0, 0, 0.7)',
                                        color: '#fff',
                                        font: { size: 10 }
                                    }
                                }
                            }
                        }
                    },
                    scales: {
                        x: {
                            grid: {
                                color: 'rgba(255, 255, 255, 0.05)'
                            },
                            ticks: {
                                color: 'rgba(255, 255, 255, 0.5)',
                                maxRotation: 0,
                                callback: function(value, index) {
                                    // Show every 5th matchday
                                    return (index + 1) % 5 === 0 || index === 0 || index === 37 ? `${index + 1}` : '';
                                }
                            }
                        },
                        y: {
                            beginAtZero: true,
                            grid: {
                                color: 'rgba(255, 255, 255, 0.05)'
                            },
                            ticks: {
                                color: 'rgba(255, 255, 255, 0.5)'
                            }
                        }
                    }
                }
            });
        }

        /**
         * Render legend item
         */
        renderLegendItem(team, index) {
            const color = this.getTeamColor(index);
            return `
                <div class="timeline-legend-item" style="--team-color: ${color}">
                    <span class="timeline-legend-color"></span>
                    <span class="timeline-legend-name">${team.teamName}</span>
                    <span class="timeline-legend-points">${team.points} pts</span>
                </div>
            `;
        }

        /**
         * Calculate projected end-of-season points
         */
        calculateProjectedPoints(team) {
            const matchdays = this.data.matchdaysCompleted || 1;
            const totalMatches = this.data.totalMatchesInSeason || 38;
            const remaining = totalMatches - matchdays;
            const ppg = team.pointsPerGame || (team.points / matchdays);
            return Math.round(team.points + (ppg * remaining));
        }

        /**
         * Get team color by index
         */
        getTeamColor(index) {
            const colors = [
                '#6366f1', // Indigo - 1st
                '#f59e0b', // Amber - 2nd
                '#10b981', // Emerald - 3rd
                '#ec4899', // Pink - 4th
                '#8b5cf6'  // Violet - 5th
            ];
            return colors[index % colors.length];
        }

        /**
         * Get intensity class for styling
         */
        getIntensityClass(intensity) {
            return intensity.toLowerCase().replace(/\s+/g, '-');
        }

        /**
         * Render loading state
         */
        renderLoading() {
            return `
                <div class="title-timeline-widget loading">
                    <div class="title-timeline-header">
                        <h3 class="title-timeline-title">
                            <span class="title-timeline-icon">📈</span>
                            Title Race Projection
                        </h3>
                    </div>
                    <div class="loading-chart">
                        <div class="chart-skeleton"></div>
                    </div>
                </div>
            `;
        }

        /**
         * Render error state
         */
        renderError(message) {
            return `
                <div class="title-timeline-widget error">
                    <div class="title-timeline-header">
                        <h3 class="title-timeline-title">
                            <span class="title-timeline-icon">📈</span>
                            Title Race Projection
                        </h3>
                    </div>
                    <div class="error-message">
                        <span class="error-icon">⚠️</span>
                        <p>${message}</p>
                    </div>
                </div>
            `;
        }

        /**
         * Refresh the timeline
         */
        async refresh() {
            await this.loadData();
        }

        /**
         * Destroy the chart and cleanup
         */
        destroy() {
            if (this.chart) {
                this.chart.destroy();
                this.chart = null;
            }
        }
    }

    // =====================================================
    // CSS Styles
    // =====================================================
    function injectStyles() {
        if (document.getElementById('title-timeline-styles')) return;

        const styles = document.createElement('style');
        styles.id = 'title-timeline-styles';
        styles.textContent = `
            .title-timeline-widget {
                background: var(--card-bg, #1a1a2e);
                border-radius: 12px;
                padding: 1.25rem;
                color: var(--text-primary, #fff);
            }

            .title-timeline-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 1rem;
            }

            .title-timeline-title {
                font-size: 1.1rem;
                font-weight: 600;
                margin: 0;
                display: flex;
                align-items: center;
                gap: 0.5rem;
            }

            .title-timeline-icon {
                font-size: 1.25rem;
            }

            .title-timeline-intensity {
                padding: 0.25rem 0.75rem;
                border-radius: 6px;
                font-size: 0.75rem;
                font-weight: 500;
            }

            .title-timeline-intensity.wide-open {
                background: rgba(99, 102, 241, 0.2);
                color: #818cf8;
            }

            .title-timeline-intensity.close-race {
                background: rgba(251, 191, 36, 0.2);
                color: #fbbf24;
            }

            .title-timeline-intensity.comfortable-lead {
                background: rgba(16, 185, 129, 0.2);
                color: #34d399;
            }

            .title-timeline-intensity.decided {
                background: rgba(107, 114, 128, 0.2);
                color: #9ca3af;
            }

            .title-timeline-chart-container {
                height: 200px;
                position: relative;
                margin-bottom: 1rem;
            }

            /* Compact mode styles */
            .title-timeline-compact {
                padding: 1rem;
            }

            .title-timeline-compact .title-timeline-header {
                margin-bottom: 0.75rem;
            }

            .title-timeline-compact .title-timeline-title {
                font-size: 0.95rem;
            }

            .title-timeline-compact .title-timeline-icon {
                font-size: 1rem;
            }

            .title-timeline-chart-container.compact {
                height: 120px;
                margin-bottom: 0.75rem;
            }

            .title-timeline-projection.compact {
                padding: 0.5rem;
                display: flex;
                flex-direction: column;
                gap: 0.4rem;
            }

            .projection-team-row {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                padding: 0.35rem 0.5rem;
                background: rgba(255, 255, 255, 0.03);
                border-radius: 4px;
                border-left: 3px solid var(--team-color);
            }

            .projection-rank {
                font-size: 0.7rem;
                font-weight: 700;
                color: var(--text-muted, #9ca3af);
                min-width: 16px;
            }

            .projection-team-row .projection-team-name {
                flex: 1;
                font-size: 0.75rem;
                color: var(--text-primary, #fff);
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }

            .projection-team-row .projection-team-points {
                font-size: 0.8rem;
                font-weight: 700;
                color: var(--team-color);
            }

            .title-timeline-legend {
                display: flex;
                justify-content: center;
                gap: 1.5rem;
                margin-bottom: 1rem;
            }

            .timeline-legend-item {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                font-size: 0.8rem;
            }

            .timeline-legend-color {
                width: 12px;
                height: 3px;
                background: var(--team-color);
                border-radius: 2px;
            }

            .timeline-legend-name {
                color: var(--text-secondary, #d1d5db);
            }

            .timeline-legend-points {
                font-weight: 600;
                color: var(--text-primary, #fff);
            }

            .title-timeline-projection {
                padding: 0.75rem;
                background: var(--projection-bg, rgba(255, 255, 255, 0.03));
                border-radius: 8px;
            }

            .projection-label {
                font-size: 0.75rem;
                color: var(--text-muted, #9ca3af);
                margin-bottom: 0.5rem;
                display: block;
            }

            .projection-teams {
                display: flex;
                justify-content: space-around;
                gap: 1rem;
            }

            .projection-team {
                text-align: center;
            }

            .projection-team-name {
                font-size: 0.75rem;
                color: var(--team-color);
                display: block;
                margin-bottom: 0.25rem;
            }

            .projection-team-points {
                font-size: 1rem;
                font-weight: 700;
            }

            /* Loading state */
            .chart-skeleton {
                height: 100%;
                background: linear-gradient(90deg,
                    rgba(255,255,255,0.03) 25%,
                    rgba(255,255,255,0.08) 50%,
                    rgba(255,255,255,0.03) 75%);
                background-size: 200% 100%;
                animation: skeleton-loading 1.5s infinite;
                border-radius: 8px;
            }

            /* Error state */
            .title-timeline-widget.error .error-message {
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
            }

            /* Responsive */
            @media (max-width: 640px) {
                .title-timeline-legend {
                    flex-direction: column;
                    align-items: center;
                    gap: 0.5rem;
                }

                .projection-teams {
                    flex-direction: column;
                    gap: 0.5rem;
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
    window.TitleRaceTimeline = TitleRaceTimeline;

    console.log('[TitleTimeline] Widget module loaded');

})();


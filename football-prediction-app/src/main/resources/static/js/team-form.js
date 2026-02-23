/**
 * Team Form Insights Component
 * Reusable component for displaying team form statistics with sparkline charts
 * and animated number count-up effects.
 *
 * FIXES APPLIED:
 * - Integrated with centralized API service
 * - Added defensive null checks for all API response fields
 * - Fixed chart re-rendering issues when team changes
 * - Added fallback UI when data is unavailable
 * - Improved error state display
 * - Added proper cleanup to prevent memory leaks
 */

(function() {
    'use strict';

    // Component configuration
    const CONFIG = {
        animationDuration: 1000,
        sparklineHeight: 40,
        sparklineWidth: 100,
        countUpDecimals: {
            goalsAvg: 2,
            concededAvg: 2,
            cleanSheetRate: 0,
            shotConversion: 0
        }
    };

    // Chart instances storage
    const chartInstances = {};

    /**
     * TeamFormComponent class for creating reusable team form panels
     */
    class TeamFormComponent {
        constructor(containerId, options = {}) {
            this.containerId = containerId;
            this.container = document.getElementById(containerId);
            this.options = {
                showSparkline: true,
                animate: true,
                ...options
            };
            this.data = null;
            this.isLoading = false;
        }

        /**
         * Fetch and display team form data
         * @param {string} teamName - Team name to fetch data for
         */
        async loadTeamForm(teamName) {
            if (!teamName) {
                console.warn('[TeamForm] No team name provided');
                return;
            }

            // Re-query container in case DOM changed
            this.container = document.getElementById(this.containerId);

            if (!this.container) {
                console.warn('[TeamForm] Container not found:', this.containerId);
                return;
            }

            if (this.isLoading) {
                console.log('[TeamForm] Already loading, skipping...');
                return;
            }

            this.isLoading = true;
            this.showLoading();

            try {
                let response;

                // Use centralized API if available
                if (window.api && typeof window.api.getTeamForm === 'function') {
                    response = await window.api.getTeamForm(teamName);
                } else {
                    // Fallback to direct fetch
                    const fetchResponse = await fetch(`${window.location.origin}/api/teams/form?team=${encodeURIComponent(teamName)}`);

                    if (!fetchResponse.ok) {
                        const errorData = await fetchResponse.json().catch(() => ({}));
                        throw new Error(errorData.message || `HTTP ${fetchResponse.status}`);
                    }

                    response = await fetchResponse.json();
                }

                // Defensive: Validate response data
                if (!response || typeof response !== 'object') {
                    throw new Error('Invalid response format');
                }

                this.data = this.normalizeData(response, teamName);
                this.render();

            } catch (error) {
                console.error('[TeamForm] Failed to fetch team form:', error);
                this.showError(error.message || 'Failed to load team form data');
            } finally {
                this.isLoading = false;
            }
        }

        /**
         * Normalize API response data with defensive defaults
         */
        normalizeData(response, teamName) {
            return {
                teamName: response.teamName || teamName || 'Unknown Team',
                last5GoalsAvg: parseFloat(response.last5GoalsAvg) || 0,
                last5ConcededAvg: parseFloat(response.last5ConcededAvg) || 0,
                cleanSheetRate: parseFloat(response.cleanSheetRate) || 0,
                shotConversion: parseFloat(response.shotConversion) || 0,
                formTrend: response.formTrend || 'stable',
                recentForm: response.recentForm || '',
                goalsTimeline: Array.isArray(response.goalsTimeline) ? response.goalsTimeline : [],
                concededTimeline: Array.isArray(response.concededTimeline) ? response.concededTimeline : []
            };
        }

        /**
         * Show loading state
         */
        showLoading() {
            this.container.innerHTML = `
                <div class="team-form-loading">
                    <div class="loading-spinner"></div>
                    <span>Loading form data...</span>
                </div>
            `;
        }

        /**
         * Show error state
         */
        showError(message) {
            this.container.innerHTML = `
                <div class="team-form-error">
                    <span class="error-icon">⚠️</span>
                    <span>${message}</span>
                </div>
            `;
        }

        /**
         * Render the team form panel
         */
        render() {
            if (!this.data || !this.container) {
                console.warn('[TeamForm] No data or container available for rendering');
                return;
            }

            const {
                teamName,
                last5GoalsAvg,
                last5ConcededAvg,
                cleanSheetRate,
                shotConversion,
                formTrend,
                recentForm,
                goalsTimeline,
                concededTimeline
            } = this.data;

            const trendIcon = this.getTrendIcon(formTrend);
            const trendClass = `trend-${formTrend}`;
            const formBadges = this.renderFormBadges(recentForm);

            // Check if Chart.js is available for sparklines
            const hasChart = typeof Chart !== 'undefined';
            const showSparklines = this.options.showSparkline && hasChart &&
                                   goalsTimeline.length > 0;

            this.container.innerHTML = `
                <div class="team-form-panel">
                    <div class="team-form-header">
                        <h4 class="team-form-title">${this.escapeHtml(teamName)}</h4>
                        <div class="form-trend ${trendClass}">
                            <span class="trend-icon">${trendIcon}</span>
                            <span class="trend-label">${this.getTrendLabel(formTrend)}</span>
                        </div>
                    </div>

                    ${formBadges ? `
                    <div class="recent-form-badges">
                        ${formBadges}
                    </div>
                    ` : ''}

                    <div class="form-stats-grid">
                        <div class="form-stat-item">
                            <div class="stat-header">
                                <span class="stat-icon">⚽</span>
                                <span class="stat-label">Goals Avg</span>
                            </div>
                            <div class="stat-value-container">
                                <span class="stat-value count-up" data-target="${last5GoalsAvg}" data-decimals="2">0</span>
                            </div>
                        </div>

                        <div class="form-stat-item">
                            <div class="stat-header">
                                <span class="stat-icon">🥅</span>
                                <span class="stat-label">Conceded Avg</span>
                            </div>
                            <div class="stat-value-container">
                                <span class="stat-value count-up" data-target="${last5ConcededAvg}" data-decimals="2">0</span>
                            </div>
                        </div>

                        <div class="form-stat-item">
                            <div class="stat-header">
                                <span class="stat-icon">🧤</span>
                                <span class="stat-label">Clean Sheet %</span>
                            </div>
                            <div class="stat-value-container">
                                <span class="stat-value count-up" data-target="${Math.round(cleanSheetRate * 100)}" data-decimals="0">0</span>
                                <span class="stat-unit">%</span>
                            </div>
                        </div>

                        <div class="form-stat-item">
                            <div class="stat-header">
                                <span class="stat-icon">🎯</span>
                                <span class="stat-label">Shot Conv %</span>
                            </div>
                            <div class="stat-value-container">
                                <span class="stat-value count-up" data-target="${Math.round(shotConversion * 100)}" data-decimals="0">0</span>
                                <span class="stat-unit">%</span>
                            </div>
                        </div>
                    </div>

                    ${showSparklines ? `
                    <div class="sparkline-container">
                        <div class="sparkline-wrapper">
                            <span class="sparkline-label">Goals Timeline</span>
                            <canvas id="${this.containerId}-goals-sparkline" class="sparkline-canvas"></canvas>
                        </div>
                        <div class="sparkline-wrapper">
                            <span class="sparkline-label">Conceded Timeline</span>
                            <canvas id="${this.containerId}-conceded-sparkline" class="sparkline-canvas"></canvas>
                        </div>
                    </div>
                    ` : ''}
                </div>
            `;

            // Initialize animations and sparklines
            if (this.options.animate) {
                this.animateCountUp();
            } else {
                this.setFinalValues();
            }

            if (showSparklines) {
                // Use requestAnimationFrame to ensure DOM is ready
                requestAnimationFrame(() => {
                    this.renderSparklines(goalsTimeline, concededTimeline);
                });
            }
        }

        /**
         * Escape HTML to prevent XSS
         */
        escapeHtml(str) {
            if (typeof str !== 'string') return '';
            const div = document.createElement('div');
            div.textContent = str;
            return div.innerHTML;
        }

        /**
         * Render recent form badges (W/D/L)
         */
        renderFormBadges(recentForm) {
            if (!recentForm) return '';

            // Reverse to show chronologically (oldest to newest)
            const badges = recentForm.split('').reverse().map(result => {
                const resultClass = result === 'W' ? 'win' : result === 'D' ? 'draw' : 'loss';
                return `<span class="form-badge ${resultClass}">${result}</span>`;
            });

            return badges.join('');
        }

        /**
         * Get trend icon based on form trend
         */
        getTrendIcon(trend) {
            switch(trend) {
                case 'up': return '↗️';
                case 'down': return '↘️';
                default: return '➡️';
            }
        }

        /**
         * Get trend label
         */
        getTrendLabel(trend) {
            switch(trend) {
                case 'up': return 'Improving';
                case 'down': return 'Declining';
                default: return 'Stable';
            }
        }

        /**
         * Animate count-up effect for stat values
         */
        animateCountUp() {
            const elements = this.container.querySelectorAll('.count-up');

            elements.forEach(el => {
                const target = parseFloat(el.dataset.target);
                const decimals = parseInt(el.dataset.decimals) || 0;
                const duration = CONFIG.animationDuration;
                const startTime = performance.now();

                const animate = (currentTime) => {
                    const elapsed = currentTime - startTime;
                    const progress = Math.min(elapsed / duration, 1);

                    // Easing function for smooth animation
                    const easeOutQuart = 1 - Math.pow(1 - progress, 4);
                    const currentValue = target * easeOutQuart;

                    el.textContent = currentValue.toFixed(decimals);

                    if (progress < 1) {
                        requestAnimationFrame(animate);
                    }
                };

                requestAnimationFrame(animate);
            });
        }

        /**
         * Set final values without animation
         */
        setFinalValues() {
            const elements = this.container.querySelectorAll('.count-up');
            elements.forEach(el => {
                const target = parseFloat(el.dataset.target);
                const decimals = parseInt(el.dataset.decimals) || 0;
                el.textContent = target.toFixed(decimals);
            });
        }

        /**
         * Render sparkline charts using Chart.js
         */
        renderSparklines(goalsTimeline, concededTimeline) {
            // Defensive: Check if Chart.js is available
            if (typeof Chart === 'undefined') {
                console.warn('[TeamForm] Chart.js not available, skipping sparklines');
                return;
            }

            // Destroy existing charts to prevent memory leaks
            const goalsChartId = `${this.containerId}-goals-sparkline`;
            const concededChartId = `${this.containerId}-conceded-sparkline`;

            if (chartInstances[goalsChartId]) {
                chartInstances[goalsChartId].destroy();
                delete chartInstances[goalsChartId];
            }
            if (chartInstances[concededChartId]) {
                chartInstances[concededChartId].destroy();
                delete chartInstances[concededChartId];
            }

            // Goals sparkline
            const goalsCanvas = document.getElementById(goalsChartId);
            if (goalsCanvas && goalsTimeline && goalsTimeline.length > 0) {
                try {
                    chartInstances[goalsChartId] = this.createSparkline(
                        goalsCanvas,
                        goalsTimeline.slice().reverse(), // Reverse to show chronologically
                        '#22c55e' // Green
                    );
                } catch (e) {
                    console.warn('[TeamForm] Failed to create goals sparkline:', e);
                }
            }

            // Conceded sparkline
            const concededCanvas = document.getElementById(concededChartId);
            if (concededCanvas && concededTimeline && concededTimeline.length > 0) {
                try {
                    chartInstances[concededChartId] = this.createSparkline(
                        concededCanvas,
                        concededTimeline.slice().reverse(),
                        '#ef4444' // Red
                    );
                } catch (e) {
                    console.warn('[TeamForm] Failed to create conceded sparkline:', e);
                }
            }
        }

        /**
         * Create a sparkline chart
         */
        createSparkline(canvas, data, color) {
            const ctx = canvas.getContext('2d');

            return new Chart(ctx, {
                type: 'line',
                data: {
                    labels: data.map((_, i) => `Match ${i + 1}`),
                    datasets: [{
                        data: data,
                        borderColor: color,
                        backgroundColor: `${color}20`,
                        fill: true,
                        tension: 0.4,
                        pointRadius: 3,
                        pointBackgroundColor: color,
                        pointBorderColor: '#1e293b',
                        pointBorderWidth: 1,
                        borderWidth: 2
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            display: false
                        },
                        tooltip: {
                            enabled: true,
                            backgroundColor: '#1e293b',
                            titleColor: '#f1f5f9',
                            bodyColor: '#f1f5f9',
                            borderColor: '#334155',
                            borderWidth: 1,
                            padding: 8,
                            displayColors: false,
                            callbacks: {
                                title: () => '',
                                label: (context) => `Goals: ${context.raw}`
                            }
                        }
                    },
                    scales: {
                        x: {
                            display: false
                        },
                        y: {
                            display: false,
                            beginAtZero: true
                        }
                    },
                    animation: {
                        duration: CONFIG.animationDuration
                    }
                }
            });
        }

        /**
         * Destroy component and cleanup
         */
        destroy() {
            const goalsChartId = `${this.containerId}-goals-sparkline`;
            const concededChartId = `${this.containerId}-conceded-sparkline`;

            if (chartInstances[goalsChartId]) {
                chartInstances[goalsChartId].destroy();
                delete chartInstances[goalsChartId];
            }
            if (chartInstances[concededChartId]) {
                chartInstances[concededChartId].destroy();
                delete chartInstances[concededChartId];
            }

            if (this.container) {
                this.container.innerHTML = '';
            }

            this.data = null;
            this.isLoading = false;
        }
    }

    /**
     * Initialize Team Form Insights panels for prediction page
     */
    function initTeamFormPanels() {
        // Create containers if they don't exist
        const predictionResults = document.getElementById('predictionResults');
        if (!predictionResults) return;

        // Check if team form section already exists
        if (document.getElementById('teamFormSection')) return;

        // Create team form section
        const teamFormSection = document.createElement('div');
        teamFormSection.id = 'teamFormSection';
        teamFormSection.className = 'team-form-section hidden';
        teamFormSection.innerHTML = `
            <h3 class="card-section-title">Team Form Insights</h3>
            <div class="team-form-panels">
                <div id="homeTeamFormPanel" class="team-form-panel-container home-panel"></div>
                <div id="awayTeamFormPanel" class="team-form-panel-container away-panel"></div>
            </div>
        `;

        // Insert after expected goals card
        const expectedGoalsCard = predictionResults.querySelector('.expected-goals-card');
        if (expectedGoalsCard) {
            expectedGoalsCard.insertAdjacentElement('afterend', teamFormSection);
        } else {
            predictionResults.prepend(teamFormSection);
        }
    }

    /**
     * Load team form data for both teams
     */
    async function loadTeamFormData(homeTeam, awayTeam) {
        initTeamFormPanels();

        const teamFormSection = document.getElementById('teamFormSection');
        if (teamFormSection) {
            teamFormSection.classList.remove('hidden');
        }

        const homeComponent = new TeamFormComponent('homeTeamFormPanel', {
            showSparkline: true,
            animate: true
        });

        const awayComponent = new TeamFormComponent('awayTeamFormPanel', {
            showSparkline: true,
            animate: true
        });

        // Load both teams in parallel
        await Promise.all([
            homeComponent.loadTeamForm(homeTeam),
            awayComponent.loadTeamForm(awayTeam)
        ]);

        // Store references for cleanup
        window.teamFormComponents = {
            home: homeComponent,
            away: awayComponent
        };
    }

    /**
     * Clear team form panels
     */
    function clearTeamFormPanels() {
        if (window.teamFormComponents) {
            if (window.teamFormComponents.home) {
                window.teamFormComponents.home.destroy();
            }
            if (window.teamFormComponents.away) {
                window.teamFormComponents.away.destroy();
            }
            window.teamFormComponents = null;
        }

        const teamFormSection = document.getElementById('teamFormSection');
        if (teamFormSection) {
            teamFormSection.classList.add('hidden');
        }
    }

    // Expose to global scope
    window.TeamFormComponent = TeamFormComponent;
    window.loadTeamFormData = loadTeamFormData;
    window.clearTeamFormPanels = clearTeamFormPanels;
    window.initTeamFormPanels = initTeamFormPanels;

})();


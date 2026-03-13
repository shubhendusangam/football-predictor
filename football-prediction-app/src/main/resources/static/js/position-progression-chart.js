/**
 * Position Progression Chart Component
 * =====================================
 *
 * Line chart showing a team's league position progression over the season.
 * - Inverted Y-axis (position 1 at top, 20 at bottom)
 * - Top 4 (Champions League) zone highlighted in blue
 * - Relegation zone highlighted in red
 * - Animated line drawing from start to current gameweek
 * - Tooltip showing GW, position, points, opponent, result
 * - Key moments marked (biggest jumps/drops)
 *
 * Dependencies:
 *   - Chart.js (loaded dynamically from CDN if not available)
 *
 * Usage:
 *   const chart = new PositionProgressionChart('containerId');
 *   await chart.load('Arsenal', '2025-26');
 */
(function () {
    'use strict';

    // ── Constants ────────────────────────────────────────────────────────
    var CHART_CDN = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js';
    var TOP4_LINE = 4;        // Champions League cutoff
    var RELEGATION_LINE = 18; // Relegation zone starts at 18 (for a 20-team league)
    var MAX_POSITIONS = 20;
    var ANIMATION_DURATION = 1500;

    // ── Helper ───────────────────────────────────────────────────────────

    function escapeHtml(str) {
        if (str == null) return '';
        var d = document.createElement('div');
        d.textContent = String(str);
        return d.innerHTML;
    }

    /**
     * Load Chart.js dynamically if not already present.
     */
    function ensureChartJs() {
        if (typeof Chart !== 'undefined') return Promise.resolve();
        return new Promise(function (resolve, reject) {
            var script = document.createElement('script');
            script.src = CHART_CDN;
            script.onload = resolve;
            script.onerror = function () { reject(new Error('Failed to load Chart.js')); };
            document.head.appendChild(script);
        });
    }

    // ── Detect key moments (biggest position jumps / drops) ─────────────
    function findKeyMoments(progression) {
        if (!progression || progression.length < 2) return [];
        var moments = [];
        for (var i = 1; i < progression.length; i++) {
            var diff = progression[i - 1].position - progression[i].position; // positive = improved
            if (diff !== 0) {
                moments.push({ index: i, diff: diff, gw: progression[i] });
            }
        }
        moments.sort(function (a, b) { return Math.abs(b.diff) - Math.abs(a.diff); });
        return moments.slice(0, 3); // top 3 key moments
    }

    // ── Chart Component ──────────────────────────────────────────────────

    function PositionProgressionChart(containerId) {
        this.containerId = containerId;
        this.chart = null;
    }

    /**
     * Load data from API and render.
     * @param {string} teamName
     * @param {string} [season]
     */
    PositionProgressionChart.prototype.load = async function (teamName, season) {
        var container = document.getElementById(this.containerId);
        if (!container) {
            console.warn('[PosChart] Container not found:', this.containerId);
            return;
        }

        // Loading state
        container.innerHTML =
            '<div class="pos-chart-loading">' +
            '  <div class="loading-spinner"></div>' +
            '  <span>Loading position history…</span>' +
            '</div>';

        try {
            await ensureChartJs();

            var url = '/api/teams/' + encodeURIComponent(teamName) + '/position-history';
            if (season) url += '?season=' + encodeURIComponent(season);

            var response = await fetch(url);
            if (!response.ok) {
                var err = await response.json().catch(function () { return {}; });
                throw new Error(err.message || 'HTTP ' + response.status);
            }

            var data = await response.json();

            if (!data.progression || data.progression.length === 0) {
                container.innerHTML =
                    '<div class="pos-chart-empty">' +
                    '  <span class="empty-icon">📊</span>' +
                    '  <p>No position data available for ' + escapeHtml(teamName) +
                    (season ? ' in season ' + escapeHtml(season) : '') + '</p>' +
                    '</div>';
                return;
            }

            this.render(container, data);
        } catch (error) {
            console.error('[PosChart] Error:', error);
            container.innerHTML =
                '<div class="pos-chart-error">' +
                '  <span class="error-icon">⚠️</span>' +
                '  <p>' + escapeHtml(error.message) + '</p>' +
                '</div>';
        }
    };

    /**
     * Render the chart + summary into the container.
     */
    PositionProgressionChart.prototype.render = function (container, data) {
        var self = this;
        var totalTeams = data.totalTeams || MAX_POSITIONS;
        var relegationLine = totalTeams - 2; // bottom 3

        // Summary strip
        var summaryHtml =
            '<div class="pos-chart-summary">' +
            '  <div class="pos-summary-item">' +
            '    <span class="pos-summary-value pos-best">' + data.highestPosition + '</span>' +
            '    <span class="pos-summary-label">Best</span>' +
            '  </div>' +
            '  <div class="pos-summary-item">' +
            '    <span class="pos-summary-value">' + data.currentPosition + '</span>' +
            '    <span class="pos-summary-label">Current</span>' +
            '  </div>' +
            '  <div class="pos-summary-item">' +
            '    <span class="pos-summary-value pos-worst">' + data.lowestPosition + '</span>' +
            '    <span class="pos-summary-label">Worst</span>' +
            '  </div>' +
            '</div>';

        container.innerHTML =
            '<div class="pos-chart-wrapper">' +
            summaryHtml +
            '  <div class="pos-chart-canvas-container">' +
            '    <canvas id="posProgressionCanvas"></canvas>' +
            '  </div>' +
            '  <div class="pos-chart-legend">' +
            '    <span class="legend-item"><span class="legend-color legend-cl"></span> Top 4 (CL)</span>' +
            '    <span class="legend-item"><span class="legend-color legend-rel"></span> Relegation</span>' +
            '  </div>' +
            '</div>';

        // Key moments badges
        var keyMoments = findKeyMoments(data.progression);
        if (keyMoments.length > 0) {
            var momentsHtml = '<div class="pos-chart-moments">';
            keyMoments.forEach(function (m) {
                var icon = m.diff > 0 ? '🔼' : '🔽';
                var cls = m.diff > 0 ? 'moment-up' : 'moment-down';
                momentsHtml +=
                    '<span class="pos-moment ' + cls + '">' +
                    icon + ' GW' + m.gw.gameweek + ': ' +
                    Math.abs(m.diff) + ' places ' + (m.diff > 0 ? 'up' : 'down') +
                    ' vs ' + escapeHtml(m.gw.opponent) +
                    '</span>';
            });
            momentsHtml += '</div>';
            container.querySelector('.pos-chart-wrapper').insertAdjacentHTML('beforeend', momentsHtml);
        }

        // Build Chart.js chart
        var canvas = document.getElementById('posProgressionCanvas');
        if (!canvas) return;
        var ctx = canvas.getContext('2d');

        // Destroy previous instance
        if (self.chart) {
            self.chart.destroy();
            self.chart = null;
        }

        var labels = data.progression.map(function (p) { return 'GW' + p.gameweek; });
        var positions = data.progression.map(function (p) { return p.position; });
        var meta = data.progression; // for tooltip

        // Background fill zones
        var top4Plugin = {
            id: 'zoneBackground',
            beforeDraw: function (chart) {
                var ctx2 = chart.ctx;
                var yAxis = chart.scales.y;
                var xAxis = chart.scales.x;
                var left = xAxis.left;
                var right = xAxis.right;

                // Top-4 zone (positions 1-4) → green-ish
                var y1 = yAxis.getPixelForValue(1);
                var y4 = yAxis.getPixelForValue(TOP4_LINE + 0.5);
                ctx2.fillStyle = 'rgba(59, 130, 246, 0.07)';
                ctx2.fillRect(left, Math.min(y1, y4), right - left, Math.abs(y4 - y1));

                // Relegation zone → red-ish
                var yRel = yAxis.getPixelForValue(relegationLine - 0.5);
                var yBot = yAxis.getPixelForValue(totalTeams);
                ctx2.fillStyle = 'rgba(239, 68, 68, 0.07)';
                ctx2.fillRect(left, Math.min(yRel, yBot), right - left, Math.abs(yBot - yRel));
            }
        };

        self.chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: escapeHtml(data.teamName) + ' Position',
                    data: positions,
                    borderColor: '#3b82f6',
                    backgroundColor: 'rgba(59, 130, 246, 0.1)',
                    borderWidth: 2.5,
                    tension: 0.3,
                    fill: false,
                    pointRadius: 4,
                    pointHoverRadius: 7,
                    pointBackgroundColor: positions.map(function (p) {
                        if (p <= TOP4_LINE) return '#3b82f6';
                        if (p >= relegationLine) return '#ef4444';
                        return '#8b5cf6';
                    }),
                    pointBorderColor: '#fff',
                    pointBorderWidth: 1.5
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: {
                    duration: ANIMATION_DURATION,
                    easing: 'easeOutQuart'
                },
                interaction: {
                    mode: 'index',
                    intersect: false
                },
                scales: {
                    y: {
                        reverse: true,       // ← inverted: 1 at top
                        min: 1,
                        max: totalTeams,
                        ticks: {
                            stepSize: 1,
                            color: 'rgba(148, 163, 184, 0.7)',
                            font: { size: 11 },
                            callback: function (val) {
                                // Show only every 2nd tick or important positions
                                if (val === 1 || val === TOP4_LINE || val === relegationLine || val === totalTeams || val % 2 === 0)
                                    return val;
                                return '';
                            }
                        },
                        grid: {
                            color: function (ctx) {
                                if (ctx.tick.value === TOP4_LINE) return 'rgba(59, 130, 246, 0.4)';
                                if (ctx.tick.value === relegationLine) return 'rgba(239, 68, 68, 0.4)';
                                return 'rgba(148, 163, 184, 0.1)';
                            },
                            lineWidth: function (ctx) {
                                if (ctx.tick.value === TOP4_LINE || ctx.tick.value === relegationLine) return 2;
                                return 1;
                            }
                        },
                        title: {
                            display: true,
                            text: 'League Position',
                            color: 'rgba(203, 213, 225, 0.9)',
                            font: { size: 12 }
                        }
                    },
                    x: {
                        ticks: {
                            color: 'rgba(148, 163, 184, 0.7)',
                            font: { size: 10 },
                            maxRotation: 45,
                            autoSkip: true,
                            maxTicksLimit: 19
                        },
                        grid: {
                            display: false
                        },
                        title: {
                            display: true,
                            text: 'Gameweek',
                            color: 'rgba(203, 213, 225, 0.9)',
                            font: { size: 12 }
                        }
                    }
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(15, 23, 42, 0.95)',
                        titleColor: '#f8fafc',
                        bodyColor: '#cbd5e1',
                        borderColor: 'rgba(148, 163, 184, 0.2)',
                        borderWidth: 1,
                        cornerRadius: 8,
                        padding: 12,
                        callbacks: {
                            title: function (items) {
                                var idx = items[0].dataIndex;
                                var gw = meta[idx];
                                return 'Gameweek ' + gw.gameweek + ' — ' + gw.date;
                            },
                            label: function (item) {
                                var idx = item.dataIndex;
                                var gw = meta[idx];
                                return [
                                    'Position: ' + gw.position + getSuffix(gw.position),
                                    'Points: ' + gw.points,
                                    'vs ' + gw.opponent + ' (' + (gw.result === 'W' ? '✅ Win' : gw.result === 'D' ? '🤝 Draw' : '❌ Loss') + ')'
                                ];
                            }
                        }
                    }
                }
            },
            plugins: [top4Plugin]
        });
    };

    /**
     * Destroy the chart instance (cleanup).
     */
    PositionProgressionChart.prototype.destroy = function () {
        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }
    };

    // ── Util ─────────────────────────────────────────────────────────────
    function getSuffix(n) {
        var s = ['th', 'st', 'nd', 'rd'];
        var v = n % 100;
        return (s[(v - 20) % 10] || s[v] || s[0]);
    }

    // ── Expose globally ──────────────────────────────────────────────────
    window.PositionProgressionChart = PositionProgressionChart;
})();


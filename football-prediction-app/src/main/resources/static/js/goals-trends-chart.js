/**
 * Goals Trends Chart — League-wide Goals Trends Analysis
 *
 * Renders:
 * - Line chart: goals per game over multiple seasons (total, home, away + trend line)
 * - Stacked bar chart: goal distribution (low / medium / high scoring games)
 * - Summary stats table with per-season breakdown
 * - Season multi-select and trend banner
 *
 * Uses Canvas API exclusively (no external chart libraries).
 *
 * @module GoalsTrendsChart
 * @version 1.0.0
 */
window.GoalsTrendsChart = (function () {
    'use strict';

    // ═══════════════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════════════

    var COLORS = {
        total:     '#3b82f6', // blue
        home:      '#22c55e', // green
        away:      '#ef4444', // red
        trend:     '#a855f7', // purple (dashed)
        low:       '#ef4444', // red
        medium:    '#eab308', // yellow
        high:      '#22c55e', // green
        grid:      'rgba(148, 163, 184, 0.15)',
        axisLabel: 'rgba(148, 163, 184, 0.7)',
        tooltip:   'rgba(15, 23, 42, 0.9)'
    };

    var LINE_CHART_PADDING = { top: 20, right: 20, bottom: 40, left: 50 };
    var BAR_CHART_PADDING  = { top: 20, right: 20, bottom: 40, left: 50 };

    // ═══════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════

    var state = {
        data: null,
        allSeasons: [],
        selectedSeasons: [],
        loading: false
    };

    // ═══════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Render the full goals trends page into a container.
     * @param {HTMLElement} container
     */
    function render(container) {
        if (!container) return;
        container.innerHTML = buildPageHTML();
        loadAvailableSeasons();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Page Layout
    // ═══════════════════════════════════════════════════════════════════

    function buildPageHTML() {
        return '<div class="goals-trends-page">' +
            '<div id="goalsTrendsContent">' +
                '<div class="goals-trends-loading">' +
                    '<div class="loading-spinner"></div>' +
                    '<p class="goals-trends-empty-message">Loading goals trends\u2026</p>' +
                '</div>' +
            '</div>' +
        '</div>';
    }

    // ═══════════════════════════════════════════════════════════════════
    // Data Loading
    // ═══════════════════════════════════════════════════════════════════

    function loadAvailableSeasons() {
        fetch('/api/seasons')
            .then(function (res) { return res.json(); })
            .then(function (data) {
                var seasons = [];
                if (Array.isArray(data)) {
                    seasons = data;
                } else if (data && Array.isArray(data.seasons)) {
                    seasons = data.seasons;
                }
                state.allSeasons = seasons;
                // Default: last 6 seasons
                state.selectedSeasons = seasons.slice(0, 6);
                loadGoalsTrends();
            })
            .catch(function () {
                // Fallback: load without season selector
                state.allSeasons = [];
                state.selectedSeasons = [];
                loadGoalsTrends();
            });
    }

    function loadGoalsTrends() {
        state.loading = true;
        var content = document.getElementById('goalsTrendsContent');
        if (content) {
            content.innerHTML =
                '<div class="goals-trends-loading">' +
                    '<div class="loading-spinner"></div>' +
                    '<p class="goals-trends-empty-message">Analyzing goals trends\u2026</p>' +
                '</div>';
        }

        var url = '/api/league/goals-trends';
        if (state.selectedSeasons.length > 0) {
            url += '?seasons=' + encodeURIComponent(state.selectedSeasons.join(','));
        }

        fetch(url)
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (data) {
                state.data = data;
                state.loading = false;
                renderContent();
            })
            .catch(function (err) {
                state.loading = false;
                showError(err.message);
            });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render Content
    // ═══════════════════════════════════════════════════════════════════

    function renderContent() {
        var content = document.getElementById('goalsTrendsContent');
        if (!content || !state.data) return;

        var d = state.data;
        var stats = d.seasonStats || [];

        if (stats.length === 0) {
            content.innerHTML =
                '<div class="goals-trends-empty">' +
                    '<div class="goals-trends-empty-icon">\u26BD</div>' +
                    '<p class="goals-trends-empty-message">No goals data available for the selected seasons.</p>' +
                '</div>';
            return;
        }

        var trendArrow = d.trendDirection === 'Increasing' ? '\u2191' :
                         d.trendDirection === 'Decreasing' ? '\u2193' : '\u2192';
        var trendClass = d.trendDirection.toLowerCase();

        var html = '';

        // Header
        html += '<div class="goals-trends-header">';
        html +=   '<div class="goals-trends-header-left">';
        html +=     '<span class="goals-trends-header-icon">\u26BD</span>';
        html +=     '<div>';
        html +=       '<h2 class="goals-trends-header-title">Goals Trends Analysis</h2>';
        html +=       '<p class="goals-trends-header-subtitle">Tracking goal-scoring patterns across seasons</p>';
        html +=     '</div>';
        html +=   '</div>';
        html +=   buildSeasonSelector();
        html += '</div>';

        // Summary banner
        var latestSeason = stats[stats.length - 1];
        html += '<div class="goals-trends-summary">';
        html +=   '<div class="goals-trends-summary-item">';
        html +=     '<span>Trend</span>';
        html +=     '<span class="goals-trends-trend-arrow ' + trendClass + '">' + trendArrow + '</span>';
        html +=     '<span class="goals-trends-summary-value ' + trendClass + '">' + d.trendDirection + '</span>';
        html +=   '</div>';
        html +=   '<div class="goals-trends-divider"></div>';
        html +=   '<div class="goals-trends-summary-item">';
        html +=     '<span>Avg Change/Season</span>';
        html +=     '<span class="goals-trends-summary-value ' + trendClass + '">' + formatSign(d.avgChange) + ' goals/game</span>';
        html +=   '</div>';
        html +=   '<div class="goals-trends-divider"></div>';
        html +=   '<div class="goals-trends-summary-item">';
        html +=     '<span>Latest (' + esc(latestSeason.season) + ')</span>';
        html +=     '<span class="goals-trends-summary-value">' + latestSeason.avgGoalsPerGame + ' goals/game</span>';
        html +=   '</div>';
        html +=   '<div class="goals-trends-divider"></div>';
        html +=   '<div class="goals-trends-summary-item">';
        html +=     '<span>Seasons Analyzed</span>';
        html +=     '<span class="goals-trends-summary-value">' + d.seasonsAnalyzed + '</span>';
        html +=   '</div>';
        html += '</div>';

        // Charts row
        html += '<div class="goals-trends-charts">';

        // Line chart card
        html += '<div class="goals-trends-card">';
        html +=   '<div class="goals-trends-card-header">';
        html +=     '<span>\uD83D\uDCC8</span>';
        html +=     '<h3 class="goals-trends-card-title">Goals Per Game</h3>';
        html +=   '</div>';
        html +=   '<div class="goals-trends-canvas-container">';
        html +=     '<canvas id="goalsTrendsLineChart"></canvas>';
        html +=   '</div>';
        html +=   '<div class="goals-trends-legend">';
        html +=     legendItem(COLORS.total, 'Total');
        html +=     legendItem(COLORS.home, 'Home');
        html +=     legendItem(COLORS.away, 'Away');
        html +=     legendItem(COLORS.trend, 'Trend');
        html +=   '</div>';
        html += '</div>';

        // Bar chart card
        html += '<div class="goals-trends-card">';
        html +=   '<div class="goals-trends-card-header">';
        html +=     '<span>\uD83D\uDCCA</span>';
        html +=     '<h3 class="goals-trends-card-title">Goal Distribution</h3>';
        html +=   '</div>';
        html +=   '<div class="goals-trends-canvas-container">';
        html +=     '<canvas id="goalsTrendsBarChart"></canvas>';
        html +=   '</div>';
        html +=   '<div class="goals-trends-legend">';
        html +=     legendItem(COLORS.low, 'Low (0\u20131)');
        html +=     legendItem(COLORS.medium, 'Medium (2\u20133)');
        html +=     legendItem(COLORS.high, 'High (4+)');
        html +=   '</div>';
        html += '</div>';

        html += '</div>'; // end charts

        // Stats table
        html += '<div class="goals-trends-card goals-trends-card-full">';
        html +=   '<div class="goals-trends-card-header">';
        html +=     '<span>\uD83D\uDCCB</span>';
        html +=     '<h3 class="goals-trends-card-title">Season-by-Season Breakdown</h3>';
        html +=   '</div>';
        html +=   buildStatsTable(stats);
        html += '</div>';

        content.innerHTML = html;

        // Bind season selector events
        bindSeasonSelector();

        // Draw charts after DOM is ready
        requestAnimationFrame(function () {
            drawLineChart(stats);
            drawBarChart(stats);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Season Selector
    // ═══════════════════════════════════════════════════════════════════

    function buildSeasonSelector() {
        if (state.allSeasons.length === 0) return '';
        var html = '<div class="goals-trends-controls">';
        html += '<div class="goals-trends-season-selector">';
        for (var i = 0; i < state.allSeasons.length && i < 15; i++) {
            var s = state.allSeasons[i];
            var active = state.selectedSeasons.indexOf(s) !== -1 ? ' active' : '';
            html += '<button class="goals-trends-season-btn' + active + '" data-season="' + esc(s) + '">' + esc(s) + '</button>';
        }
        html += '</div>';
        html += '<button class="goals-trends-apply-btn" id="goalsTrendsApplyBtn">Apply</button>';
        html += '</div>';
        return html;
    }

    function bindSeasonSelector() {
        var buttons = document.querySelectorAll('.goals-trends-season-btn');
        buttons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                btn.classList.toggle('active');
            });
        });

        var applyBtn = document.getElementById('goalsTrendsApplyBtn');
        if (applyBtn) {
            applyBtn.addEventListener('click', function () {
                var selected = [];
                document.querySelectorAll('.goals-trends-season-btn.active').forEach(function (b) {
                    selected.push(b.getAttribute('data-season'));
                });
                if (selected.length === 0) {
                    if (window.UI && window.UI.showToast) {
                        window.UI.showToast('Select at least one season', 'warning');
                    }
                    return;
                }
                state.selectedSeasons = selected.sort();
                loadGoalsTrends();
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Line Chart (Canvas)
    // ═══════════════════════════════════════════════════════════════════

    function drawLineChart(stats) {
        var canvas = document.getElementById('goalsTrendsLineChart');
        if (!canvas || stats.length === 0) return;

        var dpr = window.devicePixelRatio || 1;
        var rect = canvas.parentElement.getBoundingClientRect();
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        canvas.style.width = rect.width + 'px';
        canvas.style.height = rect.height + 'px';

        var ctx = canvas.getContext('2d');
        ctx.scale(dpr, dpr);

        var w = rect.width;
        var h = rect.height;
        var p = LINE_CHART_PADDING;
        var chartW = w - p.left - p.right;
        var chartH = h - p.top - p.bottom;

        // Data
        var labels = stats.map(function (s) { return s.season; });
        var totalData = stats.map(function (s) { return s.avgGoalsPerGame; });
        var homeData  = stats.map(function (s) { return s.homeGoalsAvg; });
        var awayData  = stats.map(function (s) { return s.awayGoalsAvg; });

        var allValues = totalData.concat(homeData).concat(awayData);
        var maxVal = Math.ceil(Math.max.apply(null, allValues) + 0.5);
        var minVal = Math.floor(Math.max(0, Math.min.apply(null, allValues) - 0.3));

        // Clear
        ctx.clearRect(0, 0, w, h);

        // Grid lines
        var steps = 5;
        ctx.strokeStyle = COLORS.grid;
        ctx.lineWidth = 1;
        ctx.font = '11px -apple-system, BlinkMacSystemFont, sans-serif';
        ctx.fillStyle = COLORS.axisLabel;
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (var i = 0; i <= steps; i++) {
            var yVal = minVal + (maxVal - minVal) * (i / steps);
            var yPos = p.top + chartH - (i / steps) * chartH;
            ctx.beginPath();
            ctx.moveTo(p.left, yPos);
            ctx.lineTo(w - p.right, yPos);
            ctx.stroke();
            ctx.fillText(yVal.toFixed(1), p.left - 8, yPos);
        }

        // X-axis labels
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        var xSpacing = labels.length > 1 ? chartW / (labels.length - 1) : 0;
        labels.forEach(function (label, idx) {
            var x = p.left + idx * xSpacing;
            ctx.fillText(label, x, h - p.bottom + 8);
        });

        // Helper to map value → y
        function yFor(val) {
            return p.top + chartH - ((val - minVal) / (maxVal - minVal)) * chartH;
        }

        function xFor(idx) {
            return p.left + idx * xSpacing;
        }

        // Draw line helper
        function drawLine(data, color, lineWidth, dashed) {
            if (data.length < 1) return;
            ctx.beginPath();
            ctx.strokeStyle = color;
            ctx.lineWidth = lineWidth || 2;
            ctx.lineJoin = 'round';
            ctx.lineCap = 'round';
            if (dashed) {
                ctx.setLineDash([6, 4]);
            } else {
                ctx.setLineDash([]);
            }
            data.forEach(function (val, idx) {
                var x = xFor(idx);
                var y = yFor(val);
                if (idx === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            });
            ctx.stroke();
            ctx.setLineDash([]);
        }

        // Draw area fill under total line
        if (totalData.length > 1) {
            ctx.beginPath();
            ctx.moveTo(xFor(0), yFor(totalData[0]));
            totalData.forEach(function (val, idx) {
                ctx.lineTo(xFor(idx), yFor(val));
            });
            ctx.lineTo(xFor(totalData.length - 1), p.top + chartH);
            ctx.lineTo(xFor(0), p.top + chartH);
            ctx.closePath();
            ctx.fillStyle = 'rgba(59, 130, 246, 0.08)';
            ctx.fill();
        }

        // Draw lines
        drawLine(totalData, COLORS.total, 2.5);
        drawLine(homeData, COLORS.home, 2);
        drawLine(awayData, COLORS.away, 2);

        // Trend line (linear regression on total)
        if (totalData.length >= 2) {
            var trendVals = linearTrend(totalData);
            drawLine(trendVals, COLORS.trend, 1.5, true);
        }

        // Data points (dots)
        function drawDots(data, color) {
            data.forEach(function (val, idx) {
                ctx.beginPath();
                ctx.arc(xFor(idx), yFor(val), 3.5, 0, Math.PI * 2);
                ctx.fillStyle = color;
                ctx.fill();
                ctx.strokeStyle = '#fff';
                ctx.lineWidth = 1.5;
                ctx.stroke();
            });
        }
        drawDots(totalData, COLORS.total);
        drawDots(homeData, COLORS.home);
        drawDots(awayData, COLORS.away);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Bar Chart (Canvas) — Stacked distribution
    // ═══════════════════════════════════════════════════════════════════

    function drawBarChart(stats) {
        var canvas = document.getElementById('goalsTrendsBarChart');
        if (!canvas || stats.length === 0) return;

        var dpr = window.devicePixelRatio || 1;
        var rect = canvas.parentElement.getBoundingClientRect();
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        canvas.style.width = rect.width + 'px';
        canvas.style.height = rect.height + 'px';

        var ctx = canvas.getContext('2d');
        ctx.scale(dpr, dpr);

        var w = rect.width;
        var h = rect.height;
        var p = BAR_CHART_PADDING;
        var chartW = w - p.left - p.right;
        var chartH = h - p.top - p.bottom;

        // Clear
        ctx.clearRect(0, 0, w, h);

        var n = stats.length;
        var barWidth = Math.min(40, (chartW / n) * 0.6);
        var gap = (chartW - barWidth * n) / (n + 1);

        // Y axis grid (0-100%)
        ctx.strokeStyle = COLORS.grid;
        ctx.lineWidth = 1;
        ctx.font = '11px -apple-system, BlinkMacSystemFont, sans-serif';
        ctx.fillStyle = COLORS.axisLabel;
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (var i = 0; i <= 5; i++) {
            var pct = i * 20;
            var yPos = p.top + chartH - (pct / 100) * chartH;
            ctx.beginPath();
            ctx.moveTo(p.left, yPos);
            ctx.lineTo(w - p.right, yPos);
            ctx.stroke();
            ctx.fillText(pct + '%', p.left - 8, yPos);
        }

        // Bars
        stats.forEach(function (s, idx) {
            var x = p.left + gap + idx * (barWidth + gap);
            var lowH  = (s.lowScoringPercentage / 100) * chartH;
            var medH  = (s.mediumScoringPercentage / 100) * chartH;
            var highH = (s.highScoringPercentage / 100) * chartH;

            var baseY = p.top + chartH;

            // Low (bottom)
            ctx.fillStyle = COLORS.low;
            roundedRect(ctx, x, baseY - lowH, barWidth, lowH, 0);
            ctx.fill();

            // Medium (middle)
            ctx.fillStyle = COLORS.medium;
            roundedRect(ctx, x, baseY - lowH - medH, barWidth, medH, 0);
            ctx.fill();

            // High (top with rounded top corners)
            ctx.fillStyle = COLORS.high;
            roundedRectTop(ctx, x, baseY - lowH - medH - highH, barWidth, highH, 4);
            ctx.fill();

            // X label
            ctx.fillStyle = COLORS.axisLabel;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'top';
            ctx.font = '10px -apple-system, BlinkMacSystemFont, sans-serif';
            ctx.fillText(s.season, x + barWidth / 2, h - p.bottom + 6);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Stats Table
    // ═══════════════════════════════════════════════════════════════════

    function buildStatsTable(stats) {
        var html = '<div class="goals-trends-table-container"><table class="goals-trends-table">';
        html += '<thead><tr>';
        html += '<th>Season</th>';
        html += '<th>Matches</th>';
        html += '<th>Total Goals</th>';
        html += '<th>Avg/Game</th>';
        html += '<th>Home Avg</th>';
        html += '<th>Away Avg</th>';
        html += '<th>Clean Sheets</th>';
        html += '<th>High (>4)</th>';
        html += '<th>Low (<2)</th>';
        html += '</tr></thead><tbody>';

        stats.forEach(function (s) {
            html += '<tr>';
            html += '<td class="goals-trends-season-col">' + esc(s.season) + '</td>';
            html += '<td>' + s.totalMatches + '</td>';
            html += '<td>' + s.totalGoals + '</td>';
            html += '<td><strong>' + s.avgGoalsPerGame + '</strong></td>';
            html += '<td class="goals-trends-highlight-high">' + s.homeGoalsAvg + '</td>';
            html += '<td class="goals-trends-highlight-low">' + s.awayGoalsAvg + '</td>';
            html += '<td>' + s.cleanSheetPercentage + '%</td>';
            html += '<td class="goals-trends-highlight-high">' + s.highScoringPercentage + '%</td>';
            html += '<td class="goals-trends-highlight-low">' + s.lowScoringPercentage + '%</td>';
            html += '</tr>';
        });

        html += '</tbody></table></div>';
        return html;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Error State
    // ═══════════════════════════════════════════════════════════════════

    function showError(message) {
        var content = document.getElementById('goalsTrendsContent');
        if (!content) return;
        content.innerHTML =
            '<div class="goals-trends-error">' +
                '<div class="goals-trends-error-icon">\u26A0\uFE0F</div>' +
                '<p class="goals-trends-error-message">Failed to load goals trends: ' + esc(message) + '</p>' +
                '<button class="goals-trends-retry-btn" id="goalsTrendsRetryBtn">Retry</button>' +
            '</div>';
        var retryBtn = document.getElementById('goalsTrendsRetryBtn');
        if (retryBtn) {
            retryBtn.addEventListener('click', function () { loadGoalsTrends(); });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Utility Functions
    // ═══════════════════════════════════════════════════════════════════

    function linearTrend(data) {
        var n = data.length;
        if (n < 2) return data.slice();
        var sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (var i = 0; i < n; i++) {
            sumX  += i;
            sumY  += data[i];
            sumXY += i * data[i];
            sumX2 += i * i;
        }
        var slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        var intercept = (sumY - slope * sumX) / n;
        var result = [];
        for (var j = 0; j < n; j++) {
            result.push(intercept + slope * j);
        }
        return result;
    }

    function roundedRect(ctx, x, y, w, h, r) {
        ctx.beginPath();
        ctx.rect(x, y, w, h);
    }

    function roundedRectTop(ctx, x, y, w, h, r) {
        ctx.beginPath();
        if (h <= 0) { ctx.rect(x, y, w, 0); return; }
        r = Math.min(r, h / 2, w / 2);
        ctx.moveTo(x + r, y);
        ctx.lineTo(x + w - r, y);
        ctx.quadraticCurveTo(x + w, y, x + w, y + r);
        ctx.lineTo(x + w, y + h);
        ctx.lineTo(x, y + h);
        ctx.lineTo(x, y + r);
        ctx.quadraticCurveTo(x, y, x + r, y);
        ctx.closePath();
    }

    function legendItem(color, label) {
        return '<div class="goals-trends-legend-item">' +
            '<span class="goals-trends-legend-dot" style="background:' + color + '"></span>' +
            '<span>' + esc(label) + '</span>' +
        '</div>';
    }

    function formatSign(val) {
        if (val > 0) return '+' + val;
        return '' + val;
    }

    function esc(str) {
        if (str === null || str === undefined) return '';
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(String(str)));
        return div.innerHTML;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public Interface
    // ═══════════════════════════════════════════════════════════════════

    return {
        render: render
    };

})();


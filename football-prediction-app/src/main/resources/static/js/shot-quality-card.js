/**
 * Shot Quality Analytics Card Component
 * ======================================
 *
 * Renders a shot quality analytics card with:
 * - Circular progress indicator for quality score (0-100 scale)
 * - Shot accuracy and conversion rate statistics
 * - Color-coded rating badge based on league average comparison
 * - Sparkline chart showing last 10 matches shot trend (Canvas API)
 *
 * Usage:
 *   window.ShotQualityCard.render(container, teamStats, leagueAverages)
 *   window.ShotQualityCard.fetchAndRender(container, teamName, isHome)
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */

(function() {
    'use strict';

    // League average constants
    const LEAGUE_AVERAGES = {
        shotAccuracy: 32, // 32%
        conversionRate: 0.28 // 28%
    };

    // Near-average threshold (±5%)
    const NEAR_AVERAGE_THRESHOLD = 5;

    // Rating levels based on quality score
    const RATING_LEVELS = {
        EXCELLENT: { min: 80, class: 'excellent', label: 'Excellent' },
        GOOD: { min: 60, class: 'good', label: 'Good' },
        AVERAGE: { min: 40, class: 'average', label: 'Average' },
        POOR: { min: 0, class: 'poor', label: 'Poor' }
    };

    /**
     * Get rating level based on quality score
     * @param {number} score - Quality score (0-100)
     * @returns {Object} Rating level object
     */
    function getRatingLevel(score) {
        if (score >= RATING_LEVELS.EXCELLENT.min) return RATING_LEVELS.EXCELLENT;
        if (score >= RATING_LEVELS.GOOD.min) return RATING_LEVELS.GOOD;
        if (score >= RATING_LEVELS.AVERAGE.min) return RATING_LEVELS.AVERAGE;
        return RATING_LEVELS.POOR;
    }

    /**
     * Calculate the league average comparison status
     * @param {number} shotAccuracy - Team's shot accuracy (%)
     * @param {number} conversionRate - Team's conversion rate (0-1 scale)
     * @returns {Object} Comparison status with type and text
     */
    function getLeagueComparison(shotAccuracy, conversionRate) {
        const accuracyDiff = shotAccuracy - LEAGUE_AVERAGES.shotAccuracy;
        const conversionDiff = (conversionRate * 100) - (LEAGUE_AVERAGES.conversionRate * 100);

        // Average of both differences
        const avgDiff = (accuracyDiff + conversionDiff) / 2;

        if (avgDiff > NEAR_AVERAGE_THRESHOLD) {
            return { type: 'above', text: 'Above League Average', icon: '📈' };
        } else if (avgDiff < -NEAR_AVERAGE_THRESHOLD) {
            return { type: 'below', text: 'Below League Average', icon: '📉' };
        } else {
            return { type: 'near', text: 'Near League Average', icon: '📊' };
        }
    }

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

    /**
     * Create SVG element with namespace
     * @param {string} tag - SVG tag name
     * @param {Object} attrs - Attributes object
     * @returns {SVGElement}
     */
    function createSvgElement(tag, attrs) {
        attrs = attrs || {};
        const elem = document.createElementNS('http://www.w3.org/2000/svg', tag);
        Object.keys(attrs).forEach(function(key) {
            elem.setAttribute(key, attrs[key]);
        });
        return elem;
    }

    /**
     * Create a circular progress indicator using SVG
     * @param {number} score - Quality score (0-100)
     * @returns {HTMLElement} Progress container element
     */
    function createCircularProgress(score) {
        const container = document.createElement('div');
        container.className = 'shot-quality-card__progress-container';

        const progressWrapper = document.createElement('div');
        progressWrapper.className = 'shot-quality-card__circular-progress';

        const radius = 60;
        const circumference = 2 * Math.PI * radius;
        const progress = Math.min(Math.max(score, 0), 100);
        const dashOffset = circumference - (progress / 100) * circumference;

        const ratingLevel = getRatingLevel(score);

        // Create SVG
        const svg = createSvgElement('svg', {
            width: '140',
            height: '140',
            viewBox: '0 0 140 140'
        });

        // Background circle
        const bgCircle = createSvgElement('circle', {
            class: 'shot-quality-card__progress-bg',
            cx: '70',
            cy: '70',
            r: String(radius)
        });

        // Progress circle
        const progressCircle = createSvgElement('circle', {
            class: 'shot-quality-card__progress-bar shot-quality-card__progress-bar--' + ratingLevel.class,
            cx: '70',
            cy: '70',
            r: String(radius),
            'stroke-dasharray': String(circumference),
            'stroke-dashoffset': String(circumference) // Start at 0, animate to value
        });

        svg.appendChild(bgCircle);
        svg.appendChild(progressCircle);

        // Animate the progress bar
        requestAnimationFrame(function() {
            setTimeout(function() {
                progressCircle.style.strokeDashoffset = dashOffset;
            }, 100);
        });

        // Create center content
        const content = document.createElement('div');
        content.className = 'shot-quality-card__progress-content';

        const valueEl = document.createElement('div');
        valueEl.className = 'shot-quality-card__progress-value';
        valueEl.textContent = Math.round(score);

        const labelEl = document.createElement('div');
        labelEl.className = 'shot-quality-card__progress-label';
        labelEl.textContent = 'Quality Score';

        content.appendChild(valueEl);
        content.appendChild(labelEl);

        progressWrapper.appendChild(svg);
        progressWrapper.appendChild(content);
        container.appendChild(progressWrapper);

        return container;
    }

    /**
     * Create statistics grid
     * @param {number} shotAccuracy - Shot accuracy percentage
     * @param {number} conversionRate - Conversion rate (0-1 scale)
     * @returns {HTMLElement} Stats grid element
     */
    function createStatsGrid(shotAccuracy, conversionRate) {
        const grid = document.createElement('div');
        grid.className = 'shot-quality-card__stats';

        // Shot Accuracy Stat
        const accuracyStat = document.createElement('div');
        accuracyStat.className = 'shot-quality-card__stat';

        const accuracyValue = document.createElement('div');
        accuracyValue.className = 'shot-quality-card__stat-value';
        accuracyValue.textContent = shotAccuracy.toFixed(1) + '%';

        const accuracyLabel = document.createElement('div');
        accuracyLabel.className = 'shot-quality-card__stat-label';
        accuracyLabel.textContent = 'Shot Accuracy';

        accuracyStat.appendChild(accuracyValue);
        accuracyStat.appendChild(accuracyLabel);

        // Conversion Rate Stat
        const conversionStat = document.createElement('div');
        conversionStat.className = 'shot-quality-card__stat';

        const conversionValue = document.createElement('div');
        conversionValue.className = 'shot-quality-card__stat-value';
        conversionValue.textContent = (conversionRate * 100).toFixed(1) + '%';

        const conversionLabel = document.createElement('div');
        conversionLabel.className = 'shot-quality-card__stat-label';
        conversionLabel.textContent = 'Conversion Rate';

        conversionStat.appendChild(conversionValue);
        conversionStat.appendChild(conversionLabel);

        grid.appendChild(accuracyStat);
        grid.appendChild(conversionStat);

        return grid;
    }

    /**
     * Create rating badge
     * @param {number} shotAccuracy - Shot accuracy percentage
     * @param {number} conversionRate - Conversion rate (0-1 scale)
     * @returns {HTMLElement} Rating badge container
     */
    function createRatingBadge(shotAccuracy, conversionRate) {
        const comparison = getLeagueComparison(shotAccuracy, conversionRate);

        const container = document.createElement('div');
        container.className = 'shot-quality-card__rating-container';

        const badge = document.createElement('div');
        badge.className = 'shot-quality-card__rating-badge shot-quality-card__rating-badge--' + comparison.type;

        const icon = document.createElement('span');
        icon.className = 'shot-quality-card__rating-icon';
        icon.textContent = comparison.icon;

        const text = document.createElement('span');
        text.textContent = comparison.text;

        badge.appendChild(icon);
        badge.appendChild(text);
        container.appendChild(badge);

        return container;
    }

    /**
     * Draw sparkline chart on canvas
     * @param {HTMLCanvasElement} canvas - Canvas element
     * @param {Array} trendData - Array of trend data points
     */
    function drawSparkline(canvas, trendData) {
        if (!canvas || !trendData || trendData.length === 0) return;

        const ctx = canvas.getContext('2d');
        const width = canvas.width;
        const height = canvas.height;
        const padding = 10;

        // Clear canvas
        ctx.clearRect(0, 0, width, height);

        // Get data arrays
        const shots = trendData.map(function(d) { return d.shots || 0; });
        const goals = trendData.map(function(d) { return d.goals || 0; });

        // Calculate scale
        var maxShots = Math.max.apply(null, shots);
        var maxGoals = Math.max.apply(null, goals);
        maxShots = maxShots > 0 ? maxShots : 1;
        maxGoals = maxGoals > 0 ? maxGoals : 1;
        const maxValue = Math.max(maxShots, maxGoals);

        const chartWidth = width - padding * 2;
        const chartHeight = height - padding * 2;
        const pointSpacing = chartWidth / Math.max(trendData.length - 1, 1);

        /**
         * Draw a line on the canvas
         * @param {Array} data - Data points
         * @param {string} color - Line color
         * @param {number} lineWidth - Line width
         */
        function drawLine(data, color, lineWidth) {
            lineWidth = lineWidth || 2;
            if (data.length < 2) return;

            ctx.beginPath();
            ctx.strokeStyle = color;
            ctx.lineWidth = lineWidth;
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';

            data.forEach(function(value, index) {
                const x = padding + index * pointSpacing;
                const y = height - padding - (value / maxValue) * chartHeight;

                if (index === 0) {
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            });

            ctx.stroke();
        }

        /**
         * Draw dots at data points
         * @param {Array} data - Data points
         * @param {string} color - Dot color
         */
        function drawDots(data, color) {
            ctx.fillStyle = color;

            data.forEach(function(value, index) {
                const x = padding + index * pointSpacing;
                const y = height - padding - (value / maxValue) * chartHeight;

                ctx.beginPath();
                ctx.arc(x, y, 3, 0, Math.PI * 2);
                ctx.fill();
            });
        }

        // Draw shots line (blue)
        drawLine(shots, '#3b82f6', 2);
        drawDots(shots, '#3b82f6');

        // Draw goals line (green)
        drawLine(goals, '#22c55e', 2);
        drawDots(goals, '#22c55e');
    }

    /**
     * Create sparkline chart container
     * @param {Array} trendData - Array of trend data points from API
     * @returns {HTMLElement} Sparkline container
     */
    function createSparklineChart(trendData) {
        const container = document.createElement('div');
        container.className = 'shot-quality-card__sparkline-container';

        // Header with title and legend
        const header = document.createElement('div');
        header.className = 'shot-quality-card__sparkline-header';

        const title = document.createElement('span');
        title.className = 'shot-quality-card__sparkline-title';
        title.textContent = 'Last 10 Matches Trend';

        const legend = document.createElement('div');
        legend.className = 'shot-quality-card__sparkline-legend';

        // Shots legend
        const shotsLegend = document.createElement('div');
        shotsLegend.className = 'shot-quality-card__legend-item';
        const shotsDot = document.createElement('span');
        shotsDot.className = 'shot-quality-card__legend-dot shot-quality-card__legend-dot--shots';
        const shotsText = document.createElement('span');
        shotsText.textContent = 'Shots';
        shotsLegend.appendChild(shotsDot);
        shotsLegend.appendChild(shotsText);

        // Goals legend
        const goalsLegend = document.createElement('div');
        goalsLegend.className = 'shot-quality-card__legend-item';
        const goalsDot = document.createElement('span');
        goalsDot.className = 'shot-quality-card__legend-dot shot-quality-card__legend-dot--goals';
        const goalsText = document.createElement('span');
        goalsText.textContent = 'Goals';
        goalsLegend.appendChild(goalsDot);
        goalsLegend.appendChild(goalsText);

        legend.appendChild(shotsLegend);
        legend.appendChild(goalsLegend);

        header.appendChild(title);
        header.appendChild(legend);

        // Canvas element
        const canvas = document.createElement('canvas');
        canvas.className = 'shot-quality-card__sparkline-canvas';
        canvas.width = 300;
        canvas.height = 60;

        container.appendChild(header);
        container.appendChild(canvas);

        // Draw the sparkline after adding to DOM
        requestAnimationFrame(function() {
            // Adjust canvas size based on container
            const containerWidth = container.offsetWidth || 300;
            canvas.width = containerWidth;
            canvas.style.width = '100%';
            drawSparkline(canvas, trendData || []);
        });

        return container;
    }

    /**
     * Create card header
     * @param {string} teamName - Team name
     * @param {boolean} isHome - Whether this is home stats
     * @returns {HTMLElement} Header element
     */
    function createCardHeader(teamName, isHome) {
        const header = document.createElement('div');
        header.className = 'shot-quality-card__header';

        const title = document.createElement('h3');
        title.className = 'shot-quality-card__title';
        title.textContent = escapeHtml(teamName);

        const venueBadge = document.createElement('span');
        venueBadge.className = 'shot-quality-card__venue-badge shot-quality-card__venue-badge--' + (isHome ? 'home' : 'away');
        venueBadge.textContent = isHome ? 'HOME' : 'AWAY';

        header.appendChild(title);
        header.appendChild(venueBadge);

        return header;
    }

    /**
     * Create loading state card
     * @returns {HTMLElement} Loading card element
     */
    function createLoadingCard() {
        const card = document.createElement('div');
        card.className = 'shot-quality-card shot-quality-card--loading';

        const spinner = document.createElement('div');
        spinner.className = 'shot-quality-card__loading-spinner';

        const text = document.createElement('span');
        text.className = 'shot-quality-card__loading-text';
        text.textContent = 'Loading shot quality data...';

        card.appendChild(spinner);
        card.appendChild(text);

        return card;
    }

    /**
     * Create error state card
     * @param {string} message - Error message
     * @returns {HTMLElement} Error card element
     */
    function createErrorCard(message) {
        const card = document.createElement('div');
        card.className = 'shot-quality-card shot-quality-card--error';

        const icon = document.createElement('span');
        icon.className = 'shot-quality-card__error-icon';
        icon.textContent = '⚠️';

        const text = document.createElement('span');
        text.className = 'shot-quality-card__error-message';
        text.textContent = message || 'Failed to load data';

        card.appendChild(icon);
        card.appendChild(text);

        return card;
    }

    /**
     * Calculate quality score on 0-100 scale from 0-10 backend scale
     * @param {number} qualityScore - Quality score from backend (0-10)
     * @returns {number} Quality score on 0-100 scale
     */
    function normalizeQualityScore(qualityScore) {
        return Math.min(Math.max((qualityScore || 0) * 10, 0), 100);
    }

    /**
     * Render shot quality card
     * @param {HTMLElement} container - Container element to render into
     * @param {Object} teamStats - Team statistics object from API
     * @param {Object} leagueAverages - League averages (optional, uses defaults)
     * @returns {HTMLElement} The rendered card element
     */
    function renderShotQualityCard(container, teamStats, leagueAverages) {
        if (!container) {
            console.error('[ShotQualityCard] Container is required');
            return null;
        }

        // Update league averages if provided
        if (leagueAverages) {
            if (leagueAverages.shotAccuracy !== undefined) {
                LEAGUE_AVERAGES.shotAccuracy = leagueAverages.shotAccuracy;
            }
            if (leagueAverages.conversionRate !== undefined) {
                LEAGUE_AVERAGES.conversionRate = leagueAverages.conversionRate;
            }
        }

        // Clear container
        container.innerHTML = '';

        // Handle missing or invalid data
        if (!teamStats) {
            var errorCard = createErrorCard('No data available');
            container.appendChild(errorCard);
            return errorCard;
        }

        // Create the card
        var card = document.createElement('div');
        card.className = 'shot-quality-card shot-quality-card--animate';

        // Extract data with defensive defaults
        var teamName = teamStats.teamName || 'Unknown Team';
        var isHome = teamStats.isHome !== undefined ? teamStats.isHome : null;
        var qualityScore = normalizeQualityScore(teamStats.qualityScore);
        var shotAccuracy = teamStats.shotAccuracy || 0;
        var conversionRate = teamStats.conversionRate || 0;
        var shotsTrend = Array.isArray(teamStats.shotsTrend) ? teamStats.shotsTrend : [];

        // Build card structure
        // 1. Header
        if (isHome !== null) {
            card.appendChild(createCardHeader(teamName, isHome));
        } else {
            var header = document.createElement('div');
            header.className = 'shot-quality-card__header';
            var title = document.createElement('h3');
            title.className = 'shot-quality-card__title';
            title.textContent = escapeHtml(teamName);
            header.appendChild(title);
            card.appendChild(header);
        }

        // 2. Circular Progress
        card.appendChild(createCircularProgress(qualityScore));

        // 3. Stats Grid
        card.appendChild(createStatsGrid(shotAccuracy, conversionRate));

        // 4. Rating Badge
        card.appendChild(createRatingBadge(shotAccuracy, conversionRate));

        // 5. Sparkline Chart (if trend data available)
        if (shotsTrend.length > 0) {
            card.appendChild(createSparklineChart(shotsTrend));
        }

        container.appendChild(card);

        return card;
    }

    /**
     * Render loading state in container
     * @param {HTMLElement} container - Container element
     */
    function renderShotQualityLoading(container) {
        if (!container) return;
        container.innerHTML = '';
        container.appendChild(createLoadingCard());
    }

    /**
     * Render error state in container
     * @param {HTMLElement} container - Container element
     * @param {string} message - Error message
     */
    function renderShotQualityError(container, message) {
        if (!container) return;
        container.innerHTML = '';
        container.appendChild(createErrorCard(message));
    }

    /**
     * Fetch and render shot quality card for a team
     * @param {HTMLElement} container - Container element
     * @param {string} teamName - Team name
     * @param {boolean} isHome - Whether to fetch home (true) or away (false) stats
     * @returns {Promise<HTMLElement>} The rendered card
     */
    async function fetchAndRenderShotQualityCard(container, teamName, isHome) {
        if (!container || !teamName) {
            console.error('[ShotQualityCard] Container and teamName are required');
            return null;
        }

        renderShotQualityLoading(container);

        try {
            // Build API URL
            var baseUrl = window.location.origin;
            var url = baseUrl + '/api/teams/' + encodeURIComponent(teamName) + '/shot-quality';

            // If isHome is specified, use split mode
            if (isHome !== null && isHome !== undefined) {
                url += '?split=true';
            }

            var response = await fetch(url);

            if (!response.ok) {
                throw new Error('HTTP ' + response.status + ': ' + response.statusText);
            }

            var data = await response.json();

            // Handle split response
            var statsToRender;
            if (isHome !== null && isHome !== undefined && data.home && data.away) {
                statsToRender = isHome ? data.home : data.away;
            } else {
                statsToRender = data;
            }

            return renderShotQualityCard(container, statsToRender);

        } catch (error) {
            console.error('[ShotQualityCard] Failed to fetch data:', error);
            renderShotQualityError(container, error.message || 'Failed to load data');
            return null;
        }
    }

    /**
     * Render home and away cards side by side
     * @param {HTMLElement} container - Container element
     * @param {string} teamName - Team name
     * @returns {Promise<Object>} Object with homeCard and awayCard
     */
    async function renderShotQualityPair(container, teamName) {
        if (!container || !teamName) {
            console.error('[ShotQualityCard] Container and teamName are required');
            return null;
        }

        // Create container structure
        container.innerHTML = '' +
            '<div class="shot-quality-cards-container">' +
                '<div id="sqc-home-container" class="shot-quality-card-wrapper"></div>' +
                '<div id="sqc-away-container" class="shot-quality-card-wrapper"></div>' +
            '</div>';

        var homeContainer = document.getElementById('sqc-home-container');
        var awayContainer = document.getElementById('sqc-away-container');

        // Show loading
        renderShotQualityLoading(homeContainer);
        renderShotQualityLoading(awayContainer);

        try {
            var response = await fetch(
                window.location.origin + '/api/teams/' + encodeURIComponent(teamName) + '/shot-quality?split=true'
            );

            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            var data = await response.json();

            var homeCard = renderShotQualityCard(homeContainer, data.home);
            var awayCard = renderShotQualityCard(awayContainer, data.away);

            return { homeCard: homeCard, awayCard: awayCard, data: data };

        } catch (error) {
            console.error('[ShotQualityCard] Failed to fetch pair:', error);
            renderShotQualityError(homeContainer, 'Failed to load home stats');
            renderShotQualityError(awayContainer, 'Failed to load away stats');
            return null;
        }
    }

    // Expose API
    window.ShotQualityCard = {
        render: renderShotQualityCard,
        renderLoading: renderShotQualityLoading,
        renderError: renderShotQualityError,
        fetchAndRender: fetchAndRenderShotQualityCard,
        renderPair: renderShotQualityPair,
        LEAGUE_AVERAGES: LEAGUE_AVERAGES,
        RATING_LEVELS: RATING_LEVELS
    };

})();


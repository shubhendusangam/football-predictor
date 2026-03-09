/**
 * Expected Goals (xG) Card Module
 * =================================
 *
 * Renders an expected goals statistics card with:
 * - Line chart: xG vs Actual Goals comparison
 * - Over/underperformance with +/- indicator
 * - Color coded: Green if overperforming, Red if underperforming
 * - Animated counter showing xG accumulation
 * - Comparison bars for xG vs actual
 * - Target icon (🎯) for xG
 *
 * @module ExpectedGoalsCard
 * @author Football Forecaster Team
 * @version 1.0.0
 */

// ══════════════════════════════════════════════════════════════════════
// CONSTANTS
// ══════════════════════════════════════════════════════════════════════

/**
 * League average xG per team per match (approx 1.3 in Premier League)
 */
const LEAGUE_AVERAGE_XG = 1.3;

/**
 * Maximum xG for bar chart scaling
 */
const MAX_XG_FOR_SCALE = 3.5;

/**
 * Performance thresholds for color coding
 */
const PERFORMANCE_THRESHOLDS = {
    STRONG_OVER: 0.3,    // > +0.3 = Strong overperforming (green)
    SLIGHT_OVER: 0.05,   // > +0.05 = Slight overperforming (light green)
    SLIGHT_UNDER: -0.05, // < -0.05 = Slight underperforming (light red)
    STRONG_UNDER: -0.3   // < -0.3 = Strong underperforming (red)
};

/**
 * Animation duration for counters in milliseconds
 */
const COUNTER_ANIMATION_DURATION = 1500;

// ══════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ══════════════════════════════════════════════════════════════════════

/**
 * Escape HTML to prevent XSS
 * @param {string} str - String to escape
 * @returns {string} Escaped string
 */
function escapeHtml(str) {
    if (str == null) return '';
    const div = document.createElement('div');
    div.textContent = String(str);
    return div.innerHTML;
}

/**
 * Format number to specified decimal places
 * @param {number} value - Value to format
 * @param {number} decimals - Number of decimal places (default: 2)
 * @returns {string} Formatted number
 */
function formatNumber(value, decimals = 2) {
    if (value == null || isNaN(value)) return '0.00';
    return Number(value).toFixed(decimals);
}

/**
 * Format percentage value
 * @param {number} value - Value between 0 and 1
 * @returns {string} Formatted percentage
 */
function formatPercentage(value) {
    if (value == null || isNaN(value)) return '0%';
    return `${Math.round(value * 100)}%`;
}

/**
 * Get performance color class based on xG difference
 * @param {number} diff - xG difference (actual - expected)
 * @returns {string} CSS class name
 */
function getPerformanceColorClass(diff) {
    if (diff == null || isNaN(diff)) return 'neutral';
    if (diff > PERFORMANCE_THRESHOLDS.STRONG_OVER) return 'strong-over';
    if (diff > PERFORMANCE_THRESHOLDS.SLIGHT_OVER) return 'slight-over';
    if (diff < PERFORMANCE_THRESHOLDS.STRONG_UNDER) return 'strong-under';
    if (diff < PERFORMANCE_THRESHOLDS.SLIGHT_UNDER) return 'slight-under';
    return 'neutral';
}

/**
 * Calculate bar width percentage for visualization
 * @param {number} value - xG value
 * @param {number} max - Maximum value for scaling
 * @returns {number} Width percentage (0-100)
 */
function calculateBarWidth(value, max = MAX_XG_FOR_SCALE) {
    if (value == null || isNaN(value) || max <= 0) return 0;
    return Math.min(100, Math.max(0, (value / max) * 100));
}

/**
 * Animate a number counter from start to end
 * @param {HTMLElement} element - Element to animate
 * @param {number} start - Start value
 * @param {number} end - End value
 * @param {number} duration - Animation duration in ms
 * @param {number} decimals - Decimal places
 */
function animateCounter(element, start, end, duration, decimals = 2) {
    if (!element) return;

    const startTime = performance.now();
    const difference = end - start;

    function updateCounter(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);

        // Ease out cubic
        const easeProgress = 1 - Math.pow(1 - progress, 3);
        const currentValue = start + (difference * easeProgress);

        element.textContent = formatNumber(currentValue, decimals);

        if (progress < 1) {
            requestAnimationFrame(updateCounter);
        }
    }

    requestAnimationFrame(updateCounter);
}

// ══════════════════════════════════════════════════════════════════════
// COMPONENT CREATION FUNCTIONS
// ══════════════════════════════════════════════════════════════════════

/**
 * Create the card header element
 * @param {Object} data - xG stats data
 * @returns {HTMLElement} Header element
 */
function createHeader(data) {
    const header = document.createElement('div');
    header.className = 'xg-card__header';

    const title = document.createElement('h3');
    title.className = 'xg-card__title';
    title.innerHTML = `🎯 ${escapeHtml(data.teamName || 'Team')} Expected Goals (xG)`;

    const badge = document.createElement('span');
    badge.className = `xg-card__venue-badge xg-card__venue-badge--${data.isHome === true ? 'home' : data.isHome === false ? 'away' : 'all'}`;
    badge.textContent = data.isHome === true ? 'HOME' : data.isHome === false ? 'AWAY' : 'ALL';

    header.appendChild(title);
    header.appendChild(badge);

    return header;
}

/**
 * Create the animated xG counter section
 * @param {Object} data - xG stats data
 * @returns {HTMLElement} Counter element
 */
function createXGCounter(data) {
    const container = document.createElement('div');
    container.className = 'xg-card__counter-section';

    const xg = Number(data.expectedGoals) || 0;
    const actual = Number(data.actualGoals) || 0;
    const diff = Number(data.xGDifference) || 0;
    const perfClass = getPerformanceColorClass(diff);

    container.innerHTML = `
        <div class="xg-card__counter-grid">
            <div class="xg-card__counter-item">
                <span class="xg-card__counter-label">Expected Goals (xG)</span>
                <span class="xg-card__counter-value xg-card__counter-value--xg" data-target="${xg}">0.00</span>
                <span class="xg-card__counter-sublabel">per match</span>
            </div>
            <div class="xg-card__counter-item">
                <span class="xg-card__counter-label">Actual Goals</span>
                <span class="xg-card__counter-value xg-card__counter-value--actual" data-target="${actual}">0.00</span>
                <span class="xg-card__counter-sublabel">per match</span>
            </div>
            <div class="xg-card__counter-item xg-card__counter-item--diff">
                <span class="xg-card__counter-label">xG Difference</span>
                <span class="xg-card__counter-value xg-card__counter-value--${perfClass}" data-target="${diff}">0.00</span>
                <span class="xg-card__counter-sublabel xg-card__performance-badge xg-card__performance-badge--${perfClass}">
                    ${escapeHtml(data.performance || '')}
                </span>
            </div>
        </div>
    `;

    // Animate counters after render
    requestAnimationFrame(() => {
        setTimeout(() => {
            const xgEl = container.querySelector('.xg-card__counter-value--xg');
            const actualEl = container.querySelector('.xg-card__counter-value--actual');
            const diffEl = container.querySelector(`.xg-card__counter-value--${perfClass}`);

            if (xgEl) animateCounter(xgEl, 0, xg, COUNTER_ANIMATION_DURATION);
            if (actualEl) animateCounter(actualEl, 0, actual, COUNTER_ANIMATION_DURATION);
            if (diffEl) {
                animateCounter(diffEl, 0, diff, COUNTER_ANIMATION_DURATION);
                // Add +/- sign after animation
                setTimeout(() => {
                    if (diffEl && diff > 0) {
                        diffEl.textContent = '+' + diffEl.textContent;
                    }
                }, COUNTER_ANIMATION_DURATION + 50);
            }
        }, 200);
    });

    return container;
}

/**
 * Create xG vs actual comparison bars
 * @param {Object} data - xG stats data
 * @returns {HTMLElement} Comparison section element
 */
function createComparisonBars(data) {
    const section = document.createElement('div');
    section.className = 'xg-card__comparison';

    const sectionTitle = document.createElement('h4');
    sectionTitle.className = 'xg-card__section-title';
    sectionTitle.textContent = 'xG vs Actual Comparison';

    section.appendChild(sectionTitle);

    // xG bar
    section.appendChild(createBarStat('Expected Goals (xG)', data.expectedGoals, 'xg'));
    // Actual bar
    section.appendChild(createBarStat('Actual Goals', data.actualGoals, 'actual'));

    // Add league average comparison
    const comparison = document.createElement('div');
    comparison.className = 'xg-card__league-comparison';

    const diffFromLeague = (data.expectedGoals || 0) - LEAGUE_AVERAGE_XG;
    const diffText = diffFromLeague >= 0 ? `+${formatNumber(diffFromLeague, 1)}` : formatNumber(diffFromLeague, 1);
    const diffClass = diffFromLeague > 0 ? 'positive' : diffFromLeague < 0 ? 'negative' : 'neutral';

    comparison.innerHTML = `
        <span class="xg-card__league-comparison-label">vs League Avg xG (${LEAGUE_AVERAGE_XG}):</span>
        <span class="xg-card__league-comparison-value xg-card__league-comparison-value--${diffClass}">${diffText}</span>
    `;

    section.appendChild(comparison);

    return section;
}

/**
 * Create a horizontal bar stat item
 * @param {string} label - Stat label
 * @param {number} value - Stat value
 * @param {string} barClass - CSS class for bar color
 * @returns {HTMLElement} Bar stat element
 */
function createBarStat(label, value, barClass = 'primary') {
    const container = document.createElement('div');
    container.className = 'xg-card__bar-stat';

    const labelRow = document.createElement('div');
    labelRow.className = 'xg-card__bar-label-row';

    const labelEl = document.createElement('span');
    labelEl.className = 'xg-card__bar-label';
    labelEl.textContent = label;

    const valueEl = document.createElement('span');
    valueEl.className = 'xg-card__bar-value';
    valueEl.textContent = formatNumber(value);

    labelRow.appendChild(labelEl);
    labelRow.appendChild(valueEl);

    const barContainer = document.createElement('div');
    barContainer.className = 'xg-card__bar-container';

    const bar = document.createElement('div');
    bar.className = `xg-card__bar xg-card__bar--${barClass}`;
    bar.style.width = '0%';

    barContainer.appendChild(bar);
    container.appendChild(labelRow);
    container.appendChild(barContainer);

    // Animate bar after render
    requestAnimationFrame(() => {
        setTimeout(() => {
            bar.style.width = `${calculateBarWidth(value)}%`;
        }, 100);
    });

    return container;
}

/**
 * Create shot conversion rate section
 * @param {Object} data - xG stats data
 * @returns {HTMLElement} Conversion rate element
 */
function createConversionRate(data) {
    const container = document.createElement('div');
    container.className = 'xg-card__conversion';

    const teamRate = data.conversionRate || 0;
    const leagueRate = data.leagueConversionRate || 0.28;
    const rateClass = teamRate > leagueRate ? 'above' : teamRate < leagueRate ? 'below' : 'average';

    container.innerHTML = `
        <div class="xg-card__conversion-header">
            <span class="xg-card__conversion-label">Shot Conversion Rate</span>
            <span class="xg-card__conversion-value xg-card__conversion-value--${rateClass}">
                ${formatPercentage(teamRate)}
            </span>
        </div>
        <div class="xg-card__conversion-bar-container">
            <div class="xg-card__conversion-bar xg-card__conversion-bar--${rateClass}"
                 style="width: 0%">
            </div>
            <div class="xg-card__conversion-marker" style="left: ${Math.min(100, leagueRate * 100 / 0.5)}%">
                <span class="xg-card__conversion-marker-label">League ${formatPercentage(leagueRate)}</span>
            </div>
        </div>
        <div class="xg-card__conversion-labels">
            <span>Low</span>
            <span>High</span>
        </div>
    `;

    // Animate bar
    requestAnimationFrame(() => {
        setTimeout(() => {
            const bar = container.querySelector('.xg-card__conversion-bar');
            if (bar) {
                bar.style.width = `${Math.min(100, teamRate * 100 / 0.5)}%`;
            }
        }, 300);
    });

    return container;
}

/**
 * Create the statistics summary grid
 * @param {Object} data - xG stats data
 * @returns {HTMLElement} Stats grid element
 */
function createStatsGrid(data) {
    const grid = document.createElement('div');
    grid.className = 'xg-card__stats-grid';

    const stats = [
        { label: 'Shots on Target', value: data.totalShotsOnTarget || 0, icon: '🎯' },
        { label: 'Goals Scored', value: data.totalGoals || 0, icon: '⚽' },
        { label: 'Matches', value: data.matchesAnalyzed || 0, icon: '📊' },
        { label: 'Weighted xG', value: formatNumber(data.weightedXG), icon: '📈' }
    ];

    stats.forEach(stat => {
        const item = document.createElement('div');
        item.className = 'xg-card__stat-item';
        item.innerHTML = `
            <span class="xg-card__stat-icon">${stat.icon}</span>
            <span class="xg-card__stat-value">${stat.value}</span>
            <span class="xg-card__stat-label">${stat.label}</span>
        `;
        grid.appendChild(item);
    });

    return grid;
}

/**
 * Create the defensive xG section
 * @param {Object} data - xG stats data
 * @returns {HTMLElement} Defensive section element
 */
function createDefensiveSection(data) {
    const section = document.createElement('div');
    section.className = 'xg-card__defensive';

    section.innerHTML = `
        <h4 class="xg-card__section-title">Defensive xG</h4>
        <div class="xg-card__defensive-grid">
            <div class="xg-card__defensive-item">
                <span class="xg-card__defensive-label">Avg SOT Against</span>
                <span class="xg-card__defensive-value">${formatNumber(data.avgShotsOnTargetAgainst)}</span>
            </div>
            <div class="xg-card__defensive-item">
                <span class="xg-card__defensive-label">xG Against</span>
                <span class="xg-card__defensive-value">${formatNumber(data.expectedGoalsAgainst)}</span>
            </div>
        </div>
    `;

    return section;
}

/**
 * Create the footer with confidence info
 * @param {Object} data - xG stats data
 * @returns {HTMLElement} Footer element
 */
function createFooter(data) {
    const footer = document.createElement('div');
    footer.className = 'xg-card__footer';

    const matchCount = data.matchesAnalyzed || 0;
    let confidenceText = 'Low confidence';
    let confidenceClass = 'low';

    if (matchCount >= 15) {
        confidenceText = 'High confidence';
        confidenceClass = 'high';
    } else if (matchCount >= 8) {
        confidenceText = 'Medium confidence';
        confidenceClass = 'medium';
    }

    footer.innerHTML = `
        <span class="xg-card__confidence xg-card__confidence--${confidenceClass}">
            ${confidenceText} (${matchCount} matches)
        </span>
    `;

    return footer;
}

// ══════════════════════════════════════════════════════════════════════
// MAIN RENDER FUNCTION
// ══════════════════════════════════════════════════════════════════════

/**
 * Render an expected goals card into a container
 * @param {HTMLElement} container - Container element to render into
 * @param {Object} data - Expected goals statistics data
 */
function renderExpectedGoalsCard(container, data) {
    if (!container) {
        console.error('[ExpectedGoalsCard] Container element not provided');
        return;
    }

    const safeData = {
        teamName: data?.teamName || 'Unknown Team',
        isHome: data?.isHome ?? null,
        avgShotsOnTarget: Number(data?.avgShotsOnTarget) || 0,
        expectedGoals: Number(data?.expectedGoals) || 0,
        actualGoals: Number(data?.actualGoals) || 0,
        xGDifference: Number(data?.xGDifference) || 0,
        conversionRate: Number(data?.conversionRate) || 0,
        leagueConversionRate: Number(data?.leagueConversionRate) || 0.28,
        performance: data?.performance || '',
        matchesAnalyzed: Number(data?.matchesAnalyzed) || 0,
        totalShotsOnTarget: Number(data?.totalShotsOnTarget) || 0,
        totalGoals: Number(data?.totalGoals) || 0,
        weightedXG: Number(data?.weightedXG) || 0,
        avgShotsOnTargetAgainst: Number(data?.avgShotsOnTargetAgainst) || 0,
        expectedGoalsAgainst: Number(data?.expectedGoalsAgainst) || 0
    };

    container.innerHTML = '';

    const card = document.createElement('div');
    card.className = 'xg-card';

    card.appendChild(createHeader(safeData));
    card.appendChild(createXGCounter(safeData));
    card.appendChild(createComparisonBars(safeData));
    card.appendChild(createConversionRate(safeData));
    card.appendChild(createDefensiveSection(safeData));
    card.appendChild(createStatsGrid(safeData));
    card.appendChild(createFooter(safeData));

    container.appendChild(card);
}

/**
 * Render loading state for expected goals card
 * @param {HTMLElement} container - Container element
 */
function renderExpectedGoalsLoading(container) {
    if (!container) return;

    container.innerHTML = `
        <div class="xg-card xg-card--loading">
            <div class="xg-card__header">
                <div class="xg-card__skeleton xg-card__skeleton--title"></div>
            </div>
            <div class="xg-card__skeleton-content">
                <div class="xg-card__skeleton xg-card__skeleton--counter"></div>
                <div class="xg-card__skeleton xg-card__skeleton--bar"></div>
                <div class="xg-card__skeleton xg-card__skeleton--bar"></div>
                <div class="xg-card__skeleton xg-card__skeleton--circle"></div>
            </div>
        </div>
    `;
}

/**
 * Render error state for expected goals card
 * @param {HTMLElement} container - Container element
 * @param {string} message - Error message
 */
function renderExpectedGoalsError(container, message) {
    if (!container) return;

    container.innerHTML = `
        <div class="xg-card xg-card--error">
            <div class="xg-card__error">
                <span class="xg-card__error-icon">⚠️</span>
                <p class="xg-card__error-message">${escapeHtml(message || 'Failed to load expected goals statistics')}</p>
            </div>
        </div>
    `;
}

/**
 * Fetch expected goals stats and render card
 * @param {HTMLElement} container - Container element
 * @param {string} teamName - Team name to fetch stats for
 * @param {boolean} isHome - Home/away filter
 * @returns {Promise<Object>} Expected goals data
 */
async function fetchAndRenderExpectedGoalsCard(container, teamName, isHome = null) {
    if (!container || !teamName) {
        console.error('[ExpectedGoalsCard] Missing container or team name');
        return null;
    }

    renderExpectedGoalsLoading(container);

    try {
        const params = new URLSearchParams();
        if (isHome !== null) {
            params.append('isHome', isHome);
        }

        const url = `${window.location.origin}/api/teams/${encodeURIComponent(teamName)}/expected-goals${params.toString() ? '?' + params.toString() : ''}`;

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        renderExpectedGoalsCard(container, data);
        return data;

    } catch (error) {
        console.error('[ExpectedGoalsCard] Failed to fetch expected goals stats:', error);
        renderExpectedGoalsError(container, error.message);
        return null;
    }
}

// ══════════════════════════════════════════════════════════════════════
// EXPORTS
// ══════════════════════════════════════════════════════════════════════

export {
    renderExpectedGoalsCard,
    renderExpectedGoalsLoading,
    renderExpectedGoalsError,
    fetchAndRenderExpectedGoalsCard,
    LEAGUE_AVERAGE_XG,
    PERFORMANCE_THRESHOLDS
};


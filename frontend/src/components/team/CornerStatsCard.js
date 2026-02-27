/**
 * Corner Statistics Card Module
 * ==============================
 *
 * Renders a corner kick statistics card with:
 * - Average corners won/conceded display
 * - Horizontal bar charts using styled div elements
 * - Corner dominance as percentage with color coding
 * - Success rate indicator
 * - Corner flag icon (⚑)
 *
 * @module CornerStatsCard
 * @author Football Forecaster Team
 * @version 1.0.0
 */

// ══════════════════════════════════════════════════════════════════════
// CONSTANTS
// ══════════════════════════════════════════════════════════════════════

/**
 * League average corners per team per match (approx 5.5 in Premier League)
 */
const LEAGUE_AVERAGE_CORNERS = 5.5;

/**
 * Maximum corners for bar chart scaling
 */
const MAX_CORNERS_FOR_SCALE = 10;

/**
 * Corner dominance thresholds for color coding
 */
const DOMINANCE_THRESHOLDS = {
    STRONG: 0.55,    // > 55% = Green
    WEAK: 0.45       // < 45% = Red, else Yellow
};

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
 * Get color class based on corner dominance value
 * @param {number} dominance - Dominance value (0 to 1)
 * @returns {string} CSS class name
 */
function getDominanceColorClass(dominance) {
    if (dominance == null || isNaN(dominance)) return 'average';
    if (dominance > DOMINANCE_THRESHOLDS.STRONG) return 'strong';
    if (dominance < DOMINANCE_THRESHOLDS.WEAK) return 'weak';
    return 'average';
}

/**
 * Calculate bar width percentage for visualization
 * @param {number} value - Corner value
 * @param {number} max - Maximum value for scaling
 * @returns {number} Width percentage (0-100)
 */
function calculateBarWidth(value, max = MAX_CORNERS_FOR_SCALE) {
    if (value == null || isNaN(value) || max <= 0) return 0;
    return Math.min(100, Math.max(0, (value / max) * 100));
}

// ══════════════════════════════════════════════════════════════════════
// COMPONENT CREATION FUNCTIONS
// ══════════════════════════════════════════════════════════════════════

/**
 * Create the card header element
 * @param {Object} data - Corner stats data
 * @returns {HTMLElement} Header element
 */
function createHeader(data) {
    const header = document.createElement('div');
    header.className = 'corner-stats-card__header';

    const title = document.createElement('h3');
    title.className = 'corner-stats-card__title';
    title.innerHTML = `⚑ ${escapeHtml(data.teamName || 'Team')} Corner Stats`;

    const badge = document.createElement('span');
    badge.className = `corner-stats-card__venue-badge corner-stats-card__venue-badge--${data.isHome === true ? 'home' : data.isHome === false ? 'away' : 'all'}`;
    badge.textContent = data.isHome === true ? 'HOME' : data.isHome === false ? 'AWAY' : 'ALL';

    header.appendChild(title);
    header.appendChild(badge);

    return header;
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
    container.className = 'corner-stats-card__bar-stat';

    const labelRow = document.createElement('div');
    labelRow.className = 'corner-stats-card__bar-label-row';

    const labelEl = document.createElement('span');
    labelEl.className = 'corner-stats-card__bar-label';
    labelEl.textContent = label;

    const valueEl = document.createElement('span');
    valueEl.className = 'corner-stats-card__bar-value';
    valueEl.textContent = formatNumber(value);

    labelRow.appendChild(labelEl);
    labelRow.appendChild(valueEl);

    const barContainer = document.createElement('div');
    barContainer.className = 'corner-stats-card__bar-container';

    const bar = document.createElement('div');
    bar.className = `corner-stats-card__bar corner-stats-card__bar--${barClass}`;
    bar.style.width = '0%'; // Start at 0 for animation

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
 * Create the corner averages section
 * @param {Object} data - Corner stats data
 * @returns {HTMLElement} Averages section element
 */
function createAveragesSection(data) {
    const section = document.createElement('div');
    section.className = 'corner-stats-card__averages';

    const sectionTitle = document.createElement('h4');
    sectionTitle.className = 'corner-stats-card__section-title';
    sectionTitle.textContent = 'Corner Averages';

    section.appendChild(sectionTitle);
    section.appendChild(createBarStat('Corners Won', data.avgCornersWon, 'won'));
    section.appendChild(createBarStat('Corners Against', data.avgCornersAgainst, 'against'));

    // Add comparison to league average
    const comparison = document.createElement('div');
    comparison.className = 'corner-stats-card__comparison';

    const diff = (data.avgCornersWon || 0) - LEAGUE_AVERAGE_CORNERS;
    const diffText = diff >= 0 ? `+${formatNumber(diff, 1)}` : formatNumber(diff, 1);
    const diffClass = diff > 0 ? 'positive' : diff < 0 ? 'negative' : 'neutral';

    comparison.innerHTML = `
        <span class="corner-stats-card__comparison-label">vs League Avg (${LEAGUE_AVERAGE_CORNERS}):</span>
        <span class="corner-stats-card__comparison-value corner-stats-card__comparison-value--${diffClass}">${diffText}</span>
    `;

    section.appendChild(comparison);

    return section;
}

/**
 * Create the corner dominance indicator
 * @param {Object} data - Corner stats data
 * @returns {HTMLElement} Dominance indicator element
 */
function createDominanceIndicator(data) {
    const container = document.createElement('div');
    container.className = 'corner-stats-card__dominance';

    const dominanceValue = data.cornerDominance || 0;
    const colorClass = getDominanceColorClass(dominanceValue);

    container.innerHTML = `
        <div class="corner-stats-card__dominance-header">
            <span class="corner-stats-card__dominance-label">Corner Dominance</span>
            <span class="corner-stats-card__dominance-value corner-stats-card__dominance-value--${colorClass}">
                ${formatPercentage(dominanceValue)}
            </span>
        </div>
        <div class="corner-stats-card__dominance-bar-container">
            <div class="corner-stats-card__dominance-bar corner-stats-card__dominance-bar--${colorClass}"
                 style="width: 0%">
            </div>
            <div class="corner-stats-card__dominance-marker" style="left: 50%">
                <span class="corner-stats-card__dominance-marker-label">50%</span>
            </div>
        </div>
        <div class="corner-stats-card__dominance-labels">
            <span>Concede More</span>
            <span>Win More</span>
        </div>
    `;

    // Animate dominance bar
    requestAnimationFrame(() => {
        setTimeout(() => {
            const bar = container.querySelector('.corner-stats-card__dominance-bar');
            if (bar) {
                bar.style.width = `${Math.min(100, dominanceValue * 100)}%`;
            }
        }, 200);
    });

    return container;
}

/**
 * Create the success rate indicator
 * @param {Object} data - Corner stats data
 * @returns {HTMLElement} Success rate element
 */
function createSuccessRate(data) {
    const container = document.createElement('div');
    container.className = 'corner-stats-card__success-rate';

    const successValue = data.successRate || 0;

    container.innerHTML = `
        <div class="corner-stats-card__success-header">
            <span class="corner-stats-card__success-label">Win Rate with Corner Dominance</span>
            <span class="corner-stats-card__success-value">${formatPercentage(successValue)}</span>
        </div>
        <div class="corner-stats-card__success-description">
            Percentage of matches won when having more corners than opponent
        </div>
    `;

    return container;
}

/**
 * Create the statistics summary grid
 * @param {Object} data - Corner stats data
 * @returns {HTMLElement} Stats grid element
 */
function createStatsGrid(data) {
    const grid = document.createElement('div');
    grid.className = 'corner-stats-card__stats-grid';

    const stats = [
        { label: 'Total Won', value: data.totalCornersWon || 0, icon: '⚑' },
        { label: 'Total Against', value: data.totalCornersAgainst || 0, icon: '⚐' },
        { label: 'Matches', value: data.matchesAnalyzed || 0, icon: '📊' },
        { label: 'Weighted Avg', value: formatNumber(data.weightedAvgCorners), icon: '📈' }
    ];

    stats.forEach(stat => {
        const item = document.createElement('div');
        item.className = 'corner-stats-card__stat-item';
        item.innerHTML = `
            <span class="corner-stats-card__stat-icon">${stat.icon}</span>
            <span class="corner-stats-card__stat-value">${stat.value}</span>
            <span class="corner-stats-card__stat-label">${stat.label}</span>
        `;
        grid.appendChild(item);
    });

    return grid;
}

/**
 * Create the footer with data freshness info
 * @param {Object} data - Corner stats data
 * @returns {HTMLElement} Footer element
 */
function createFooter(data) {
    const footer = document.createElement('div');
    footer.className = 'corner-stats-card__footer';

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
        <span class="corner-stats-card__confidence corner-stats-card__confidence--${confidenceClass}">
            ${confidenceText} (${matchCount} matches)
        </span>
    `;

    return footer;
}

// ══════════════════════════════════════════════════════════════════════
// MAIN RENDER FUNCTION
// ══════════════════════════════════════════════════════════════════════

/**
 * Render a corner statistics card into a container
 * @param {HTMLElement} container - Container element to render into
 * @param {Object} data - Corner statistics data
 * @param {string} data.teamName - Team name
 * @param {boolean} data.isHome - True for home, false for away, null for all
 * @param {number} data.avgCornersWon - Average corners won per match
 * @param {number} data.avgCornersAgainst - Average corners conceded per match
 * @param {number} data.cornerDominance - Corner dominance ratio (0-1)
 * @param {number} data.successRate - Win rate with corner dominance (0-1)
 * @param {number} data.matchesAnalyzed - Number of matches analyzed
 * @param {number} data.totalCornersWon - Total corners won
 * @param {number} data.totalCornersAgainst - Total corners conceded
 * @param {number} data.weightedAvgCorners - Weighted average corners
 */
function renderCornerStatsCard(container, data) {
    if (!container) {
        console.error('[CornerStatsCard] Container element not provided');
        return;
    }

    // Defensive: handle missing or invalid data
    const safeData = {
        teamName: data?.teamName || 'Unknown Team',
        isHome: data?.isHome ?? null,
        avgCornersWon: Number(data?.avgCornersWon) || 0,
        avgCornersAgainst: Number(data?.avgCornersAgainst) || 0,
        cornerDominance: Number(data?.cornerDominance) || 0,
        successRate: Number(data?.successRate) || 0,
        matchesAnalyzed: Number(data?.matchesAnalyzed) || 0,
        totalCornersWon: Number(data?.totalCornersWon) || 0,
        totalCornersAgainst: Number(data?.totalCornersAgainst) || 0,
        weightedAvgCorners: Number(data?.weightedAvgCorners) || 0
    };

    // Clear container
    container.innerHTML = '';

    // Create card element
    const card = document.createElement('div');
    card.className = 'corner-stats-card';

    // Build card structure
    card.appendChild(createHeader(safeData));
    card.appendChild(createAveragesSection(safeData));
    card.appendChild(createDominanceIndicator(safeData));
    card.appendChild(createSuccessRate(safeData));
    card.appendChild(createStatsGrid(safeData));
    card.appendChild(createFooter(safeData));

    container.appendChild(card);
}

/**
 * Render loading state for corner stats card
 * @param {HTMLElement} container - Container element
 */
function renderCornerStatsLoading(container) {
    if (!container) return;

    container.innerHTML = `
        <div class="corner-stats-card corner-stats-card--loading">
            <div class="corner-stats-card__header">
                <div class="corner-stats-card__skeleton corner-stats-card__skeleton--title"></div>
            </div>
            <div class="corner-stats-card__skeleton-content">
                <div class="corner-stats-card__skeleton corner-stats-card__skeleton--bar"></div>
                <div class="corner-stats-card__skeleton corner-stats-card__skeleton--bar"></div>
                <div class="corner-stats-card__skeleton corner-stats-card__skeleton--circle"></div>
            </div>
        </div>
    `;
}

/**
 * Render error state for corner stats card
 * @param {HTMLElement} container - Container element
 * @param {string} message - Error message
 */
function renderCornerStatsError(container, message) {
    if (!container) return;

    container.innerHTML = `
        <div class="corner-stats-card corner-stats-card--error">
            <div class="corner-stats-card__error">
                <span class="corner-stats-card__error-icon">⚠️</span>
                <p class="corner-stats-card__error-message">${escapeHtml(message || 'Failed to load corner statistics')}</p>
            </div>
        </div>
    `;
}

/**
 * Fetch corner stats and render card
 * @param {HTMLElement} container - Container element
 * @param {string} teamName - Team name to fetch stats for
 * @param {boolean} isHome - Home/away filter
 * @returns {Promise<Object>} Corner stats data
 */
async function fetchAndRenderCornerStatsCard(container, teamName, isHome = null) {
    if (!container || !teamName) {
        console.error('[CornerStatsCard] Missing container or team name');
        return null;
    }

    renderCornerStatsLoading(container);

    try {
        const params = new URLSearchParams();
        if (isHome !== null) {
            params.append('isHome', isHome);
        }

        const url = `${window.location.origin}/api/teams/${encodeURIComponent(teamName)}/corner-stats${params.toString() ? '?' + params.toString() : ''}`;

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        renderCornerStatsCard(container, data);
        return data;

    } catch (error) {
        console.error('[CornerStatsCard] Failed to fetch corner stats:', error);
        renderCornerStatsError(container, error.message);
        return null;
    }
}

// ══════════════════════════════════════════════════════════════════════
// EXPORTS
// ══════════════════════════════════════════════════════════════════════

export {
    renderCornerStatsCard,
    renderCornerStatsLoading,
    renderCornerStatsError,
    fetchAndRenderCornerStatsCard,
    LEAGUE_AVERAGE_CORNERS,
    DOMINANCE_THRESHOLDS
};


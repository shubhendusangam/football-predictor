/**
 * Kickoff Time Analysis Card Module
 * ===================================
 *
 * Renders a kick-off time performance card with:
 * - Bar chart showing win% by time slot
 * - Horizontal baseline for team's overall win%
 * - Highlight best/worst time slots
 * - Clock icons for each time slot
 * - Color-coded bars: Green (above avg), Red (below avg)
 * - Sample size below each bar
 *
 * @module KickoffTimeCard
 * @author Football Forecaster Team
 * @version 1.0.0
 */

// ══════════════════════════════════════════════════════════════════════
// CONSTANTS
// ══════════════════════════════════════════════════════════════════════

/**
 * Clock icons for each time slot
 */
const SLOT_ICONS = {
    early: '🕐',
    afternoon: '🕑',
    late: '🕔',
    evening: '🕖'
};

/**
 * Maximum win percentage for bar scaling
 */
const MAX_WIN_PCT_FOR_SCALE = 100;

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
 * @param {number} decimals - Number of decimal places
 * @returns {string} Formatted number
 */
function formatNumber(value, decimals = 1) {
    if (value == null || isNaN(value)) return '0';
    return Number(value).toFixed(decimals);
}

/**
 * Get performance CSS class based on performance label
 * @param {string} performance - Performance classification
 * @returns {string} CSS modifier
 */
function getPerformanceClass(performance) {
    if (!performance) return 'nodata';
    switch (performance) {
        case 'Strong': return 'strong';
        case 'Weak': return 'weak';
        case 'Average': return 'average';
        default: return 'nodata';
    }
}

// ══════════════════════════════════════════════════════════════════════
// COMPONENT CREATION FUNCTIONS
// ══════════════════════════════════════════════════════════════════════

/**
 * Create the card header
 * @param {Object} data - Analysis data
 * @returns {HTMLElement} Header element
 */
function createHeader(data) {
    const header = document.createElement('div');
    header.className = 'kickoff-time-card__header';

    const title = document.createElement('h3');
    title.className = 'kickoff-time-card__title';
    title.innerHTML = `🕐 ${escapeHtml(data.teamName || 'Team')} Kick-off Time Analysis`;

    const badge = document.createElement('span');
    badge.className = 'kickoff-time-card__badge';
    badge.textContent = `${data.matchesWithTimeData || 0} MATCHES`;

    header.appendChild(title);
    header.appendChild(badge);
    return header;
}

/**
 * Create the summary section showing best/worst times
 * @param {Object} data - Analysis data
 * @returns {HTMLElement} Summary element
 */
function createSummary(data) {
    const summary = document.createElement('div');
    summary.className = 'kickoff-time-card__summary';

    const items = [
        { label: 'Best Time', value: data.bestTime || 'N/A', cssClass: 'best' },
        { label: 'Worst Time', value: data.worstTime || 'N/A', cssClass: 'worst' },
        { label: 'Overall Win Rate', value: `${formatNumber(data.overallWinRate)}%`, cssClass: '' },
        { label: 'Avg Goals/Match', value: formatNumber(data.overallAvgGoalsScored), cssClass: '' }
    ];

    items.forEach(item => {
        const el = document.createElement('div');
        el.className = 'kickoff-time-card__summary-item';
        el.innerHTML = `
            <span class="kickoff-time-card__summary-label">${item.label}</span>
            <span class="kickoff-time-card__summary-value ${item.cssClass ? 'kickoff-time-card__summary-value--' + item.cssClass : ''}">${escapeHtml(String(item.value))}</span>
        `;
        summary.appendChild(el);
    });

    return summary;
}

/**
 * Create a single time slot row with bar chart
 * @param {Object} slot - Slot statistics
 * @param {number} overallWinRate - Overall win rate for baseline
 * @param {string} bestTime - Best time slot label
 * @param {string} worstTime - Worst time slot label
 * @returns {HTMLElement} Slot element
 */
function createSlotRow(slot, overallWinRate, bestTime, worstTime) {
    const container = document.createElement('div');
    container.className = 'kickoff-time-card__slot';

    const perfClass = getPerformanceClass(slot.performance);
    const icon = SLOT_ICONS[slot.slotKey] || '🕐';
    const isBest = slot.timeSlot === bestTime;
    const isWorst = slot.timeSlot === worstTime;

    // Slot header with label and win%
    const header = document.createElement('div');
    header.className = 'kickoff-time-card__slot-header';

    let labelSuffix = '';
    if (isBest) labelSuffix = ' ⭐';
    if (isWorst) labelSuffix = ' ⚠️';

    header.innerHTML = `
        <span class="kickoff-time-card__slot-label">
            <span class="kickoff-time-card__slot-icon">${icon}</span>
            ${escapeHtml(slot.timeSlot || '')}${labelSuffix}
        </span>
        <span class="kickoff-time-card__slot-win-pct kickoff-time-card__slot-win-pct--${perfClass}">
            ${slot.matchesPlayed > 0 ? formatNumber(slot.winPercentage) + '%' : 'No data'}
        </span>
    `;
    container.appendChild(header);

    // Bar chart with baseline
    const barContainer = document.createElement('div');
    barContainer.className = 'kickoff-time-card__bar-container';

    const bar = document.createElement('div');
    bar.className = `kickoff-time-card__bar kickoff-time-card__bar--${perfClass}`;
    bar.style.width = '0%'; // Start at 0 for animation

    barContainer.appendChild(bar);

    // Add baseline marker for overall win rate
    if (overallWinRate > 0) {
        const baseline = document.createElement('div');
        baseline.className = 'kickoff-time-card__baseline';
        baseline.style.left = `${Math.min(100, overallWinRate)}%`;
        baseline.title = `Overall win rate: ${formatNumber(overallWinRate)}%`;
        barContainer.appendChild(baseline);
    }

    container.appendChild(barContainer);

    // Details row (W/D/L and matches played)
    const details = document.createElement('div');
    details.className = 'kickoff-time-card__slot-details';

    if (slot.matchesPlayed > 0) {
        details.innerHTML = `
            <span class="kickoff-time-card__slot-detail kickoff-time-card__slot-detail--wins">W: ${slot.wins}</span>
            <span class="kickoff-time-card__slot-detail kickoff-time-card__slot-detail--draws">D: ${slot.draws}</span>
            <span class="kickoff-time-card__slot-detail kickoff-time-card__slot-detail--losses">L: ${slot.losses}</span>
            <span class="kickoff-time-card__slot-detail">📊 ${slot.matchesPlayed} matches</span>
            <span class="kickoff-time-card__slot-detail">⚽ ${formatNumber(slot.avgGoalsScored)} avg</span>
        `;
    } else {
        details.innerHTML = `<span class="kickoff-time-card__slot-detail">No matches in this slot</span>`;
    }

    container.appendChild(details);

    // Animate bar after render
    requestAnimationFrame(() => {
        setTimeout(() => {
            const width = slot.matchesPlayed > 0
                ? Math.min(100, Math.max(2, slot.winPercentage))
                : 0;
            bar.style.width = `${width}%`;
        }, 100);
    });

    return container;
}

/**
 * Create the chart section with all time slot bars
 * @param {Object} data - Analysis data
 * @returns {HTMLElement} Chart section element
 */
function createChartSection(data) {
    const section = document.createElement('div');
    section.className = 'kickoff-time-card__chart';

    const title = document.createElement('h4');
    title.className = 'kickoff-time-card__chart-title';
    title.textContent = 'Win Rate by Kick-off Time';
    section.appendChild(title);

    // Render each time slot
    const slots = data.timeSlots || [];
    slots.forEach(slot => {
        section.appendChild(createSlotRow(slot, data.overallWinRate, data.bestTime, data.worstTime));
    });

    // Legend for baseline
    const legend = document.createElement('div');
    legend.className = 'kickoff-time-card__legend';
    legend.innerHTML = `
        <span class="kickoff-time-card__legend-line"></span>
        <span>Overall win rate (${formatNumber(data.overallWinRate)}%)</span>
    `;
    section.appendChild(legend);

    return section;
}

/**
 * Create the footer with confidence and scope
 * @param {Object} data - Analysis data
 * @returns {HTMLElement} Footer element
 */
function createFooter(data) {
    const footer = document.createElement('div');
    footer.className = 'kickoff-time-card__footer';

    const confidence = data.confidence || 'Low';
    const confidenceClass = confidence.toLowerCase();

    footer.innerHTML = `
        <span class="kickoff-time-card__confidence kickoff-time-card__confidence--${confidenceClass}">
            ${confidence} confidence
        </span>
        <span class="kickoff-time-card__data-scope">
            ${escapeHtml(data.dataScope || '')} · ${data.matchesWithTimeData || 0} with time data
        </span>
    `;

    return footer;
}

// ══════════════════════════════════════════════════════════════════════
// MAIN RENDER FUNCTIONS
// ══════════════════════════════════════════════════════════════════════

/**
 * Render a kick-off time analysis card into a container.
 * @param {HTMLElement} container - Container element to render into
 * @param {Object} data - Kick-off time analysis data
 */
function renderKickoffTimeCard(container, data) {
    if (!container) {
        console.error('[KickoffTimeCard] Container element not provided');
        return;
    }

    const safeData = {
        teamName: data?.teamName || 'Unknown Team',
        dataScope: data?.dataScope || 'Recent Matches',
        matchesAnalyzed: Number(data?.matchesAnalyzed) || 0,
        matchesWithTimeData: Number(data?.matchesWithTimeData) || 0,
        timeSlots: Array.isArray(data?.timeSlots) ? data.timeSlots : [],
        bestTime: data?.bestTime || 'N/A',
        worstTime: data?.worstTime || 'N/A',
        overallWinRate: Number(data?.overallWinRate) || 0,
        overallAvgGoalsScored: Number(data?.overallAvgGoalsScored) || 0,
        confidence: data?.confidence || 'Low'
    };

    container.innerHTML = '';

    const card = document.createElement('div');
    card.className = 'kickoff-time-card';

    card.appendChild(createHeader(safeData));
    card.appendChild(createSummary(safeData));
    card.appendChild(createChartSection(safeData));
    card.appendChild(createFooter(safeData));

    container.appendChild(card);
}

/**
 * Render loading state for kick-off time card.
 * @param {HTMLElement} container - Container element
 */
function renderKickoffTimeLoading(container) {
    if (!container) return;

    container.innerHTML = `
        <div class="kickoff-time-card kickoff-time-card--loading">
            <div class="kickoff-time-card__header">
                <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--title"></div>
            </div>
            <div class="kickoff-time-card__skeleton-content">
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.75rem;">
                    <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--summary"></div>
                    <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--summary"></div>
                </div>
                <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--bar"></div>
                <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--bar"></div>
                <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--bar"></div>
                <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--bar"></div>
            </div>
        </div>
    `;
}

/**
 * Render error state for kick-off time card.
 * @param {HTMLElement} container - Container element
 * @param {string} message - Error message
 */
function renderKickoffTimeError(container, message) {
    if (!container) return;

    container.innerHTML = `
        <div class="kickoff-time-card kickoff-time-card--error">
            <div class="kickoff-time-card__error">
                <span class="kickoff-time-card__error-icon">⚠️</span>
                <p class="kickoff-time-card__error-message">${escapeHtml(message || 'Failed to load kick-off time analysis')}</p>
            </div>
        </div>
    `;
}

/**
 * Fetch kick-off time analysis and render card.
 * @param {HTMLElement} container - Container element
 * @param {string} teamName - Team name to fetch analysis for
 * @returns {Promise<Object>} Analysis data
 */
async function fetchAndRenderKickoffTimeCard(container, teamName) {
    if (!container || !teamName) {
        console.error('[KickoffTimeCard] Missing container or team name');
        return null;
    }

    renderKickoffTimeLoading(container);

    try {
        const url = `${window.location.origin}/api/teams/${encodeURIComponent(teamName)}/kickoff-analysis`;
        const response = await fetch(url);

        if (!response.ok) {
            if (response.status === 404) {
                renderKickoffTimeError(container, 'No kick-off time data available for this team');
                return null;
            }
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();

        // Handle case where team exists but has no time data (pre-2019/20 teams)
        if (data.dataAvailable === false) {
            renderKickoffTimeError(container, data.message || 'No kick-off time data available for this team');
            return null;
        }

        renderKickoffTimeCard(container, data);
        return data;

    } catch (error) {
        console.error('[KickoffTimeCard] Failed to fetch kick-off time analysis:', error);
        renderKickoffTimeError(container, error.message);
        return null;
    }
}

// ══════════════════════════════════════════════════════════════════════
// EXPORTS
// ══════════════════════════════════════════════════════════════════════

export {
    renderKickoffTimeCard,
    renderKickoffTimeLoading,
    renderKickoffTimeError,
    fetchAndRenderKickoffTimeCard
};


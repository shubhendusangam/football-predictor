/**
 * Fouls & Discipline Analysis Card Module
 * ========================================
 *
 * Renders a fouls and discipline analytics card with:
 * - Discipline score indicator (0-10 scale, color coded)
 * - Average fouls committed and drawn statistics
 * - Fouls differential visualization
 * - Horizontal bar comparison
 * - Win rate insights based on foul counts
 *
 * @module FoulsAnalysisCard
 * @author Football Forecaster Team
 * @version 1.0.0
 */

/**
 * Discipline score thresholds and their corresponding ratings
 */
const DISCIPLINE_LEVELS = {
    EXCELLENT: { min: 8, class: 'excellent', label: 'Excellent', color: '#22c55e' },
    GOOD: { min: 6, class: 'good', label: 'Good', color: '#84cc16' },
    AVERAGE: { min: 4, class: 'average', label: 'Average', color: '#f59e0b' },
    POOR: { min: 0, class: 'poor', label: 'Poor', color: '#ef4444' }
};

/**
 * Maximum fouls for bar visualization (for percentage calculation)
 */
const MAX_FOULS_FOR_BAR = 20;

/**
 * Get discipline level based on score
 * @param {number} score - Discipline score (0-10)
 * @returns {Object} Discipline level object
 */
function getDisciplineLevel(score) {
    if (score >= DISCIPLINE_LEVELS.EXCELLENT.min) return DISCIPLINE_LEVELS.EXCELLENT;
    if (score >= DISCIPLINE_LEVELS.GOOD.min) return DISCIPLINE_LEVELS.GOOD;
    if (score >= DISCIPLINE_LEVELS.AVERAGE.min) return DISCIPLINE_LEVELS.AVERAGE;
    return DISCIPLINE_LEVELS.POOR;
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
 * Create the discipline score indicator
 * @param {number} score - Discipline score (0-10)
 * @returns {HTMLElement} Score indicator element
 */
function createScoreIndicator(score) {
    const container = document.createElement('div');
    container.className = 'fouls-analysis-card__score-container';

    const indicator = document.createElement('div');
    indicator.className = 'fouls-analysis-card__score-indicator';

    const level = getDisciplineLevel(score);

    // Create the ring background
    const ring = document.createElement('div');
    ring.className = 'fouls-analysis-card__score-ring';

    // Create the fill (colored border based on score)
    const fill = document.createElement('div');
    fill.className = `fouls-analysis-card__score-fill fouls-analysis-card__score-fill--${level.class}`;

    // Calculate rotation based on score (0-10 mapped to 0-270 degrees)
    const rotation = (score / 10) * 270 - 45;
    fill.style.transform = `rotate(${rotation}deg)`;

    // Create center content
    const content = document.createElement('div');
    content.className = 'fouls-analysis-card__score-content';

    const valueEl = document.createElement('div');
    valueEl.className = 'fouls-analysis-card__score-value';
    valueEl.textContent = score.toFixed(1);

    const labelEl = document.createElement('div');
    labelEl.className = 'fouls-analysis-card__score-label';
    labelEl.textContent = 'Discipline';

    content.appendChild(valueEl);
    content.appendChild(labelEl);

    indicator.appendChild(ring);
    indicator.appendChild(fill);
    indicator.appendChild(content);
    container.appendChild(indicator);

    return container;
}

/**
 * Create discipline badge
 * @param {string} rating - Discipline rating text
 * @param {number} score - Discipline score
 * @returns {HTMLElement} Badge element
 */
function createDisciplineBadge(rating, score) {
    const level = getDisciplineLevel(score);

    const badge = document.createElement('div');
    badge.className = `fouls-analysis-card__discipline-badge fouls-analysis-card__discipline-badge--${level.class}`;
    badge.textContent = rating || level.label;

    return badge;
}

/**
 * Create statistics grid
 * @param {Object} foulsData - Fouls analysis data
 * @returns {HTMLElement} Stats grid element
 */
function createStatsGrid(foulsData) {
    const grid = document.createElement('div');
    grid.className = 'fouls-analysis-card__stats';

    // Average Committed
    const committedStat = createStatItem(
        foulsData.avgFoulsCommitted?.toFixed(1) || '0.0',
        'Avg Committed',
        null
    );

    // Average Drawn
    const drawnStat = createStatItem(
        foulsData.avgFoulsDrawn?.toFixed(1) || '0.0',
        'Avg Drawn',
        null
    );

    // Differential
    const diff = foulsData.foulsDifferential || 0;
    const diffClass = diff > 0 ? 'positive' : diff < 0 ? 'negative' : null;
    const diffStat = createStatItem(
        (diff > 0 ? '+' : '') + diff.toFixed(1),
        'Differential',
        diffClass
    );

    // Total Matches
    const matchesStat = createStatItem(
        foulsData.matchesAnalyzed?.toString() || '0',
        'Matches',
        null
    );

    grid.appendChild(committedStat);
    grid.appendChild(drawnStat);
    grid.appendChild(diffStat);
    grid.appendChild(matchesStat);

    return grid;
}

/**
 * Create a single stat item
 * @param {string} value - Stat value
 * @param {string} label - Stat label
 * @param {string|null} valueClass - Optional value modifier class
 * @returns {HTMLElement} Stat item element
 */
function createStatItem(value, label, valueClass) {
    const stat = document.createElement('div');
    stat.className = 'fouls-analysis-card__stat';

    const valueEl = document.createElement('div');
    valueEl.className = 'fouls-analysis-card__stat-value';
    if (valueClass) {
        valueEl.classList.add(`fouls-analysis-card__stat-value--${valueClass}`);
    }
    valueEl.textContent = value;

    const labelEl = document.createElement('div');
    labelEl.className = 'fouls-analysis-card__stat-label';
    labelEl.textContent = label;

    stat.appendChild(valueEl);
    stat.appendChild(labelEl);

    return stat;
}

/**
 * Create horizontal bar comparison
 * @param {number} avgCommitted - Average fouls committed
 * @param {number} avgDrawn - Average fouls drawn
 * @returns {HTMLElement} Bar comparison element
 */
function createBarComparison(avgCommitted, avgDrawn) {
    const section = document.createElement('div');
    section.className = 'fouls-analysis-card__bar-section';

    const title = document.createElement('div');
    title.className = 'fouls-analysis-card__bar-title';
    title.textContent = 'Fouls Comparison';

    const container = document.createElement('div');
    container.className = 'fouls-analysis-card__bar-container';

    // Committed bar
    const committedItem = createBarItem('Committed', avgCommitted, 'committed');

    // Drawn bar
    const drawnItem = createBarItem('Drawn', avgDrawn, 'drawn');

    container.appendChild(committedItem);
    container.appendChild(drawnItem);
    section.appendChild(title);
    section.appendChild(container);

    return section;
}

/**
 * Create a single bar item
 * @param {string} label - Bar label
 * @param {number} value - Bar value
 * @param {string} type - Bar type ('committed' or 'drawn')
 * @returns {HTMLElement} Bar item element
 */
function createBarItem(label, value, type) {
    const item = document.createElement('div');
    item.className = 'fouls-analysis-card__bar-item';

    const labelEl = document.createElement('div');
    labelEl.className = 'fouls-analysis-card__bar-label';
    labelEl.textContent = label;

    const track = document.createElement('div');
    track.className = 'fouls-analysis-card__bar-track';

    const fill = document.createElement('div');
    fill.className = `fouls-analysis-card__bar-fill fouls-analysis-card__bar-fill--${type}`;

    // Calculate width percentage (capped at MAX_FOULS_FOR_BAR)
    const percentage = Math.min((value / MAX_FOULS_FOR_BAR) * 100, 100);

    // Animate the fill
    requestAnimationFrame(() => {
        setTimeout(() => {
            fill.style.width = `${percentage}%`;
        }, 100);
    });

    track.appendChild(fill);

    const valueEl = document.createElement('div');
    valueEl.className = 'fouls-analysis-card__bar-value';
    valueEl.textContent = value.toFixed(1);

    item.appendChild(labelEl);
    item.appendChild(track);
    item.appendChild(valueEl);

    return item;
}

/**
 * Create win rate insights section
 * @param {Object} foulsData - Fouls analysis data
 * @returns {HTMLElement} Insights element
 */
function createWinRateInsights(foulsData) {
    const section = document.createElement('div');
    section.className = 'fouls-analysis-card__insights';

    const title = document.createElement('div');
    title.className = 'fouls-analysis-card__insights-title';
    title.innerHTML = '<span>📈</span> Win Rate by Foul Count';

    const grid = document.createElement('div');
    grid.className = 'fouls-analysis-card__insights-grid';

    // Low fouls win rate
    const lowItem = createInsightItem(
        `${(foulsData.winRateWhenLowFouls || 0).toFixed(0)}%`,
        `Low (<${10})`
    );

    // Controlled win rate
    const controlledItem = createInsightItem(
        `${(foulsData.winRateWhenControlled || 0).toFixed(0)}%`,
        `Controlled (<${12})`
    );

    // High fouls win rate
    const highItem = createInsightItem(
        `${(foulsData.winRateWhenHighFouls || 0).toFixed(0)}%`,
        `High (>${15})`
    );

    grid.appendChild(lowItem);
    grid.appendChild(controlledItem);
    grid.appendChild(highItem);
    section.appendChild(title);
    section.appendChild(grid);

    return section;
}

/**
 * Create a single insight item
 * @param {string} value - Insight value
 * @param {string} label - Insight label
 * @returns {HTMLElement} Insight item element
 */
function createInsightItem(value, label) {
    const item = document.createElement('div');
    item.className = 'fouls-analysis-card__insight-item';

    const valueEl = document.createElement('div');
    valueEl.className = 'fouls-analysis-card__insight-value';
    valueEl.textContent = value;

    const labelEl = document.createElement('div');
    labelEl.className = 'fouls-analysis-card__insight-label';
    labelEl.textContent = label;

    item.appendChild(valueEl);
    item.appendChild(labelEl);

    return item;
}

/**
 * Create data scope footer
 * @param {string} dataScope - Data scope text
 * @returns {HTMLElement} Footer element
 */
function createFooter(dataScope) {
    const footer = document.createElement('div');
    footer.className = 'fouls-analysis-card__footer';

    const scope = document.createElement('div');
    scope.className = 'fouls-analysis-card__data-scope';
    scope.innerHTML = `<span class="fouls-analysis-card__data-scope-icon">📊</span> ${escapeHtml(dataScope)}`;

    footer.appendChild(scope);
    return footer;
}

/**
 * Create loading state
 * @returns {HTMLElement} Loading element
 */
function createLoadingState() {
    const card = document.createElement('div');
    card.className = 'fouls-analysis-card fouls-analysis-card--loading';

    const loading = document.createElement('div');
    loading.className = 'fouls-analysis-card__loading';

    const spinner = document.createElement('div');
    spinner.className = 'fouls-analysis-card__loading-spinner';

    const text = document.createElement('p');
    text.className = 'fouls-analysis-card__loading-text';
    text.textContent = 'Loading discipline analysis...';

    loading.appendChild(spinner);
    loading.appendChild(text);
    card.appendChild(loading);

    return card;
}

/**
 * Create error state
 * @param {string} message - Error message
 * @returns {HTMLElement} Error element
 */
function createErrorState(message) {
    const card = document.createElement('div');
    card.className = 'fouls-analysis-card fouls-analysis-card--error';

    const error = document.createElement('div');
    error.className = 'fouls-analysis-card__error';

    const icon = document.createElement('span');
    icon.className = 'fouls-analysis-card__error-icon';
    icon.textContent = '⚠️';

    const title = document.createElement('h4');
    title.className = 'fouls-analysis-card__error-title';
    title.textContent = 'Unable to Load Data';

    const msg = document.createElement('p');
    msg.className = 'fouls-analysis-card__error-message';
    msg.textContent = message || 'Please try again later';

    error.appendChild(icon);
    error.appendChild(title);
    error.appendChild(msg);
    card.appendChild(error);

    return card;
}

/**
 * Create no data state
 * @param {string} teamName - Team name
 * @returns {HTMLElement} No data element
 */
function createNoDataState(teamName) {
    const card = document.createElement('div');
    card.className = 'fouls-analysis-card fouls-analysis-card--no-data';

    const noData = document.createElement('div');
    noData.className = 'fouls-analysis-card__no-data';

    const icon = document.createElement('span');
    icon.className = 'fouls-analysis-card__no-data-icon';
    icon.textContent = '📋';

    const text = document.createElement('p');
    text.className = 'fouls-analysis-card__no-data-text';
    text.textContent = `No fouls data available for ${teamName}`;

    noData.appendChild(icon);
    noData.appendChild(text);
    card.appendChild(noData);

    return card;
}

/**
 * Render the fouls analysis card
 * @param {HTMLElement} container - Container element to render into
 * @param {Object} foulsData - Fouls analysis data from API
 * @returns {HTMLElement} The rendered card element
 */
export function renderFoulsAnalysisCard(container, foulsData) {
    if (!container) {
        console.error('[FoulsAnalysisCard] Container element is required');
        return null;
    }

    // Clear container
    container.innerHTML = '';

    // Handle loading state
    if (foulsData === null || foulsData === undefined) {
        const loadingCard = createLoadingState();
        container.appendChild(loadingCard);
        return loadingCard;
    }

    // Handle error state
    if (foulsData.error) {
        const errorCard = createErrorState(foulsData.message || foulsData.error);
        container.appendChild(errorCard);
        return errorCard;
    }

    // Handle no data state
    if (foulsData.matchesAnalyzed === 0) {
        const noDataCard = createNoDataState(foulsData.teamName || 'Team');
        container.appendChild(noDataCard);
        return noDataCard;
    }

    // Create main card
    const card = document.createElement('div');
    card.className = 'fouls-analysis-card';

    // Header
    const header = document.createElement('div');
    header.className = 'fouls-analysis-card__header';

    const title = document.createElement('h3');
    title.className = 'fouls-analysis-card__title';
    title.innerHTML = `<span>⚖️</span> ${escapeHtml(foulsData.teamName || 'Team')}`;

    const venueBadge = document.createElement('span');
    venueBadge.className = `fouls-analysis-card__venue-badge fouls-analysis-card__venue-badge--${foulsData.isHome ? 'home' : 'away'}`;
    venueBadge.textContent = foulsData.isHome ? 'HOME' : 'AWAY';

    header.appendChild(title);
    header.appendChild(venueBadge);

    // Score indicator
    const scoreIndicator = createScoreIndicator(foulsData.disciplineScore || 5);

    // Discipline badge
    const disciplineBadge = createDisciplineBadge(foulsData.disciplineRating, foulsData.disciplineScore || 5);

    // Stats grid
    const statsGrid = createStatsGrid(foulsData);

    // Bar comparison
    const barComparison = createBarComparison(
        foulsData.avgFoulsCommitted || 0,
        foulsData.avgFoulsDrawn || 0
    );

    // Win rate insights
    const insights = createWinRateInsights(foulsData);

    // Footer
    const footer = createFooter(foulsData.dataScope || 'Last 20 Matches');

    // Assemble card
    card.appendChild(header);
    card.appendChild(scoreIndicator);
    card.appendChild(disciplineBadge);
    card.appendChild(statsGrid);
    card.appendChild(barComparison);
    card.appendChild(insights);
    card.appendChild(footer);

    container.appendChild(card);
    return card;
}

/**
 * Render side-by-side fouls analysis cards for two teams
 * @param {HTMLElement} container - Container element to render into
 * @param {Object} homeTeamData - Home team fouls data
 * @param {Object} awayTeamData - Away team fouls data
 * @returns {HTMLElement} The rendered container element
 */
export function renderFoulsAnalysisComparison(container, homeTeamData, awayTeamData) {
    if (!container) {
        console.error('[FoulsAnalysisCard] Container element is required');
        return null;
    }

    // Clear container
    container.innerHTML = '';

    // Create wrapper
    const wrapper = document.createElement('div');
    wrapper.className = 'fouls-analysis-cards-container';

    // Create containers for each card
    const homeContainer = document.createElement('div');
    const awayContainer = document.createElement('div');

    // Render individual cards
    renderFoulsAnalysisCard(homeContainer, homeTeamData);
    renderFoulsAnalysisCard(awayContainer, awayTeamData);

    wrapper.appendChild(homeContainer);
    wrapper.appendChild(awayContainer);

    // Add prediction section if both teams have data
    if (homeTeamData && awayTeamData &&
        homeTeamData.matchesAnalyzed > 0 && awayTeamData.matchesAnalyzed > 0) {
        const prediction = createPredictionSection(homeTeamData, awayTeamData);
        wrapper.appendChild(prediction);
    }

    container.appendChild(wrapper);
    return wrapper;
}

/**
 * Create prediction section based on discipline scores
 * @param {Object} homeTeamData - Home team fouls data
 * @param {Object} awayTeamData - Away team fouls data
 * @returns {HTMLElement} Prediction section element
 */
function createPredictionSection(homeTeamData, awayTeamData) {
    const section = document.createElement('div');
    section.className = 'fouls-prediction-section';
    section.style.gridColumn = '1 / -1'; // Span full width

    const header = document.createElement('div');
    header.className = 'fouls-prediction-section__header';

    const icon = document.createElement('span');
    icon.className = 'fouls-prediction-section__icon';
    icon.textContent = '🔮';

    const title = document.createElement('h4');
    title.className = 'fouls-prediction-section__title';
    title.textContent = 'Discipline Advantage';

    header.appendChild(icon);
    header.appendChild(title);

    const content = document.createElement('div');
    content.className = 'fouls-prediction-section__content';

    const homeDiscipline = homeTeamData.disciplineScore || 0;
    const awayDiscipline = awayTeamData.disciplineScore || 0;
    const homeTeam = homeTeamData.teamName || 'Home Team';
    const awayTeam = awayTeamData.teamName || 'Away Team';

    let predictionText;
    if (Math.abs(homeDiscipline - awayDiscipline) < 0.5) {
        predictionText = `Both teams have similar discipline levels. Expect an evenly contested match with balanced physicality.`;
    } else if (homeDiscipline > awayDiscipline) {
        const diff = (homeDiscipline - awayDiscipline).toFixed(1);
        predictionText = `<span class="fouls-prediction-section__highlight">${escapeHtml(homeTeam)}</span> has better discipline (+${diff} score). They may gain advantage through fewer fouls and better game control.`;
    } else {
        const diff = (awayDiscipline - homeDiscipline).toFixed(1);
        predictionText = `<span class="fouls-prediction-section__highlight">${escapeHtml(awayTeam)}</span> has better discipline (+${diff} score). They may gain advantage through fewer fouls and better game control.`;
    }

    content.innerHTML = predictionText;

    section.appendChild(header);
    section.appendChild(content);

    return section;
}

/**
 * Fetch fouls analysis data from API
 * @param {string} teamName - Team name
 * @param {boolean} isHome - Whether to fetch home or away data
 * @returns {Promise<Object>} Fouls analysis data
 */
export async function fetchFoulsAnalysis(teamName, isHome = true) {
    try {
        const response = await fetch(
            `/api/teams/${encodeURIComponent(teamName)}/fouls-analysis?isHome=${isHome}`
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `HTTP ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        console.error('[FoulsAnalysisCard] Failed to fetch fouls analysis:', error);
        return { error: true, message: error.message };
    }
}

// Export default for module compatibility
export default {
    renderFoulsAnalysisCard,
    renderFoulsAnalysisComparison,
    fetchFoulsAnalysis
};


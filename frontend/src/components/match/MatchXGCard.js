/**
 * Match xG Prediction Card Module
 * =================================
 *
 * Renders a match xG prediction card with:
 * - Expected goals for both teams
 * - Total xG prediction with animated counter
 * - Over/under goal probabilities (1.5, 2.5, 3.5)
 * - Recommendation text
 * - Target icon (🎯) for xG
 *
 * @module MatchXGCard
 * @author Football Forecaster Team
 * @version 1.0.0
 */

// ══════════════════════════════════════════════════════════════════════
// CONSTANTS
// ══════════════════════════════════════════════════════════════════════

/**
 * Animation duration for counter in milliseconds
 */
const COUNTER_ANIMATION_DURATION = 1500;

/**
 * Probability thresholds for color coding
 */
const PROBABILITY_THRESHOLDS = {
    HIGH: 0.6,
    MEDIUM: 0.4,
    LOW: 0
};

/**
 * Total xG thresholds for match categorization
 */
const XG_MATCH_THRESHOLDS = {
    HIGH_SCORING: 3.5,
    MODERATE: 2.5,
    LOW_SCORING: 1.5
};

// ══════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ══════════════════════════════════════════════════════════════════════

function escapeHtml(str) {
    if (str == null) return '';
    const div = document.createElement('div');
    div.textContent = String(str);
    return div.innerHTML;
}

function formatNumber(value, decimals = 1) {
    if (value == null || isNaN(value)) return '0.0';
    return Number(value).toFixed(decimals);
}

function formatProbability(value) {
    if (value == null || isNaN(value)) return '0%';
    return `${Math.round(value * 100)}%`;
}

function getProbabilityColorClass(probability) {
    if (probability == null || isNaN(probability)) return 'low';
    if (probability >= PROBABILITY_THRESHOLDS.HIGH) return 'high';
    if (probability >= PROBABILITY_THRESHOLDS.MEDIUM) return 'medium';
    return 'low';
}

function getMatchScoringClass(totalXG) {
    if (totalXG >= XG_MATCH_THRESHOLDS.HIGH_SCORING) return 'high-scoring';
    if (totalXG >= XG_MATCH_THRESHOLDS.MODERATE) return 'moderate';
    if (totalXG >= XG_MATCH_THRESHOLDS.LOW_SCORING) return 'low-scoring';
    return 'very-low';
}

function animateCounter(element, start, end, duration, decimals = 1) {
    if (!element) return;

    const startTime = performance.now();
    const difference = end - start;

    function updateCounter(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);
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
 * Create the match preview header
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Header element
 */
function createMatchHeader(data) {
    const header = document.createElement('div');
    header.className = 'match-xg-card__header';

    header.innerHTML = `
        <div class="match-xg-card__match-info">
            <span class="match-xg-card__team match-xg-card__team--home">
                ${escapeHtml(data.homeTeam)}
            </span>
            <span class="match-xg-card__vs">vs</span>
            <span class="match-xg-card__team match-xg-card__team--away">
                ${escapeHtml(data.awayTeam)}
            </span>
        </div>
        <div class="match-xg-card__badge">
            <span class="match-xg-card__badge-icon">🎯</span>
            <span class="match-xg-card__badge-text">xG Prediction</span>
        </div>
    `;

    return header;
}

/**
 * Create the animated total xG counter
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Counter element
 */
function createTotalXGCounter(data) {
    const container = document.createElement('div');
    container.className = 'match-xg-card__total-counter';

    const totalXG = Number(data.totalXG) || 0;
    const scoringClass = getMatchScoringClass(totalXG);

    container.innerHTML = `
        <div class="match-xg-card__counter-label">Expected Total Goals</div>
        <div class="match-xg-card__counter-value match-xg-card__counter-value--${scoringClass}" data-target="${totalXG}">0.0</div>
        <div class="match-xg-card__counter-subtitle">${escapeHtml(data.recommendation || '')}</div>
    `;

    // Animate counter after render
    requestAnimationFrame(() => {
        setTimeout(() => {
            const counterEl = container.querySelector('.match-xg-card__counter-value');
            if (counterEl) {
                animateCounter(counterEl, 0, totalXG, COUNTER_ANIMATION_DURATION, 1);
            }
        }, 300);
    });

    return container;
}

/**
 * Create the home vs away xG comparison section
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Comparison element
 */
function createTeamXGComparison(data) {
    const container = document.createElement('div');
    container.className = 'match-xg-card__comparison';

    const homeXG = Number(data.homeXG) || 0;
    const awayXG = Number(data.awayXG) || 0;
    const totalXG = homeXG + awayXG;

    const homePercent = totalXG > 0 ? (homeXG / totalXG) * 100 : 50;
    const awayPercent = 100 - homePercent;

    container.innerHTML = `
        <div class="match-xg-card__comparison-header">
            <span class="match-xg-card__comparison-title">Expected Goals Distribution</span>
        </div>
        <div class="match-xg-card__comparison-teams">
            <div class="match-xg-card__comparison-team match-xg-card__comparison-team--home">
                <span class="match-xg-card__comparison-team-name">${escapeHtml(data.homeTeam)}</span>
                <span class="match-xg-card__comparison-team-value">${formatNumber(homeXG)}</span>
            </div>
            <div class="match-xg-card__comparison-team match-xg-card__comparison-team--away">
                <span class="match-xg-card__comparison-team-name">${escapeHtml(data.awayTeam)}</span>
                <span class="match-xg-card__comparison-team-value">${formatNumber(awayXG)}</span>
            </div>
        </div>
        <div class="match-xg-card__stacked-bar">
            <div class="match-xg-card__stacked-bar-home" style="width: 0%"></div>
            <div class="match-xg-card__stacked-bar-away" style="width: 0%"></div>
        </div>
        <div class="match-xg-card__comparison-percentages">
            <span class="match-xg-card__comparison-percent">${Math.round(homePercent)}%</span>
            <span class="match-xg-card__comparison-percent">${Math.round(awayPercent)}%</span>
        </div>
    `;

    // Animate bars
    requestAnimationFrame(() => {
        setTimeout(() => {
            const homeBar = container.querySelector('.match-xg-card__stacked-bar-home');
            const awayBar = container.querySelector('.match-xg-card__stacked-bar-away');
            if (homeBar) homeBar.style.width = `${homePercent}%`;
            if (awayBar) awayBar.style.width = `${awayPercent}%`;
        }, 400);
    });

    return container;
}

/**
 * Create shot on target info section
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} SOT info element
 */
function createSOTInfo(data) {
    const container = document.createElement('div');
    container.className = 'match-xg-card__sot-info';

    container.innerHTML = `
        <div class="match-xg-card__section-header">
            <span class="match-xg-card__section-title">Shots on Target Average</span>
            <span class="match-xg-card__section-icon">🎯</span>
        </div>
        <div class="match-xg-card__sot-grid">
            <div class="match-xg-card__sot-item">
                <span class="match-xg-card__sot-team">${escapeHtml(data.homeTeam)}</span>
                <span class="match-xg-card__sot-value">${formatNumber(data.homeShotsOnTarget)}</span>
            </div>
            <div class="match-xg-card__sot-item">
                <span class="match-xg-card__sot-team">${escapeHtml(data.awayTeam)}</span>
                <span class="match-xg-card__sot-value">${formatNumber(data.awayShotsOnTarget)}</span>
            </div>
        </div>
    `;

    return container;
}

/**
 * Create a probability bar item
 * @param {string} label - Probability label
 * @param {number} probability - Probability value (0-1)
 * @param {number} delay - Animation delay in ms
 * @returns {HTMLElement} Probability bar element
 */
function createProbabilityBar(label, probability, delay = 0) {
    const container = document.createElement('div');
    container.className = 'match-xg-card__prob-item';

    const probValue = Number(probability) || 0;
    const colorClass = getProbabilityColorClass(probValue);

    container.innerHTML = `
        <div class="match-xg-card__prob-header">
            <span class="match-xg-card__prob-label">${escapeHtml(label)}</span>
            <span class="match-xg-card__prob-value match-xg-card__prob-value--${colorClass}">
                ${formatProbability(probValue)}
            </span>
        </div>
        <div class="match-xg-card__prob-bar-container">
            <div class="match-xg-card__prob-bar match-xg-card__prob-bar--${colorClass}"
                 style="width: 0%">
            </div>
        </div>
    `;

    // Animate bar
    requestAnimationFrame(() => {
        setTimeout(() => {
            const bar = container.querySelector('.match-xg-card__prob-bar');
            if (bar) {
                bar.style.width = `${Math.min(100, probValue * 100)}%`;
            }
        }, 500 + delay);
    });

    return container;
}

/**
 * Create the probabilities section
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Probabilities section element
 */
function createProbabilitiesSection(data) {
    const section = document.createElement('div');
    section.className = 'match-xg-card__probabilities';

    const sectionHeader = document.createElement('div');
    sectionHeader.className = 'match-xg-card__section-header';
    sectionHeader.innerHTML = `
        <span class="match-xg-card__section-title">Goal Over/Under Probabilities</span>
        <span class="match-xg-card__section-icon">📊</span>
    `;

    const probsContainer = document.createElement('div');
    probsContainer.className = 'match-xg-card__probs-container';

    probsContainer.appendChild(createProbabilityBar('Over 1.5 Goals', data.probOver1_5, 0));
    probsContainer.appendChild(createProbabilityBar('Over 2.5 Goals', data.probOver2_5, 100));
    probsContainer.appendChild(createProbabilityBar('Over 3.5 Goals', data.probOver3_5, 200));

    section.appendChild(sectionHeader);
    section.appendChild(probsContainer);

    return section;
}

/**
 * Create the prediction summary section
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Prediction summary element
 */
function createPredictionSummary(data) {
    const container = document.createElement('div');
    container.className = 'match-xg-card__prediction-summary';

    const totalXG = Number(data.totalXG) || 0;
    const scoringClass = getMatchScoringClass(totalXG);

    container.innerHTML = `
        <div class="match-xg-card__prediction-box match-xg-card__prediction-box--${scoringClass}">
            <span class="match-xg-card__prediction-icon">⚽</span>
            <span class="match-xg-card__prediction-text">${escapeHtml(data.prediction || '')}</span>
        </div>
    `;

    return container;
}

/**
 * Create the confidence indicator
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Confidence indicator element
 */
function createConfidenceIndicator(data) {
    const container = document.createElement('div');
    container.className = 'match-xg-card__confidence';

    const confidence = Number(data.confidence) || 0;
    let confidenceLabel = 'Low';
    let confidenceClass = 'low';

    if (confidence >= 0.7) {
        confidenceLabel = 'High';
        confidenceClass = 'high';
    } else if (confidence >= 0.5) {
        confidenceLabel = 'Medium';
        confidenceClass = 'medium';
    }

    container.innerHTML = `
        <div class="match-xg-card__confidence-header">
            <span class="match-xg-card__confidence-label">Model Confidence</span>
            <span class="match-xg-card__confidence-badge match-xg-card__confidence-badge--${confidenceClass}">
                ${confidenceLabel} (${formatProbability(confidence)})
            </span>
        </div>
        <div class="match-xg-card__confidence-details">
            <span class="match-xg-card__confidence-detail">
                📈 ${data.homeMatchesAnalyzed || 0} home matches analyzed
            </span>
            <span class="match-xg-card__confidence-detail">
                📈 ${data.awayMatchesAnalyzed || 0} away matches analyzed
            </span>
        </div>
    `;

    return container;
}

/**
 * Create the footer
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Footer element
 */
function createFooter(data) {
    const footer = document.createElement('div');
    footer.className = 'match-xg-card__footer';

    footer.innerHTML = `
        <div class="match-xg-card__footer-note">
            Based on shots on target × league conversion rate with recency weighting
        </div>
    `;

    return footer;
}

// ══════════════════════════════════════════════════════════════════════
// MAIN RENDER FUNCTION
// ══════════════════════════════════════════════════════════════════════

/**
 * Render a match xG prediction card into a container
 * @param {HTMLElement} container - Container element to render into
 * @param {Object} predictionData - Match xG prediction data
 */
function renderMatchXGCard(container, predictionData) {
    if (!container) {
        console.error('[MatchXGCard] Container element not provided');
        return;
    }

    const safeData = {
        homeTeam: predictionData?.homeTeam || 'Home Team',
        awayTeam: predictionData?.awayTeam || 'Away Team',
        homeXG: Number(predictionData?.homeXG) || 0,
        awayXG: Number(predictionData?.awayXG) || 0,
        totalXG: Number(predictionData?.totalXG) || 0,
        prediction: predictionData?.prediction || '',
        probOver1_5: Number(predictionData?.probOver1_5) || 0,
        probOver2_5: Number(predictionData?.probOver2_5) || 0,
        probOver3_5: Number(predictionData?.probOver3_5) || 0,
        confidence: Number(predictionData?.confidence) || 0,
        homeShotsOnTarget: Number(predictionData?.homeShotsOnTarget) || 0,
        awayShotsOnTarget: Number(predictionData?.awayShotsOnTarget) || 0,
        homeMatchesAnalyzed: Number(predictionData?.homeMatchesAnalyzed) || 0,
        awayMatchesAnalyzed: Number(predictionData?.awayMatchesAnalyzed) || 0,
        recommendation: predictionData?.recommendation || ''
    };

    // Validate totalXG = homeXG + awayXG
    const calculatedTotal = safeData.homeXG + safeData.awayXG;
    if (Math.abs(safeData.totalXG - calculatedTotal) > 0.1) {
        console.warn('[MatchXGCard] Total xG mismatch:', {
            expected: safeData.totalXG,
            calculated: calculatedTotal
        });
    }

    container.innerHTML = '';

    const card = document.createElement('div');
    card.className = 'match-xg-card';

    card.appendChild(createMatchHeader(safeData));
    card.appendChild(createTotalXGCounter(safeData));
    card.appendChild(createTeamXGComparison(safeData));
    card.appendChild(createSOTInfo(safeData));
    card.appendChild(createProbabilitiesSection(safeData));
    card.appendChild(createPredictionSummary(safeData));
    card.appendChild(createConfidenceIndicator(safeData));
    card.appendChild(createFooter(safeData));

    container.appendChild(card);
}

/**
 * Render loading state for match xG card
 * @param {HTMLElement} container - Container element
 */
function renderMatchXGLoading(container) {
    if (!container) return;

    container.innerHTML = `
        <div class="match-xg-card match-xg-card--loading">
            <div class="match-xg-card__header">
                <div class="match-xg-card__skeleton match-xg-card__skeleton--teams"></div>
            </div>
            <div class="match-xg-card__skeleton-content">
                <div class="match-xg-card__skeleton match-xg-card__skeleton--counter"></div>
                <div class="match-xg-card__skeleton match-xg-card__skeleton--bar"></div>
                <div class="match-xg-card__skeleton match-xg-card__skeleton--bar"></div>
                <div class="match-xg-card__skeleton match-xg-card__skeleton--bar"></div>
            </div>
        </div>
    `;
}

/**
 * Render error state for match xG card
 * @param {HTMLElement} container - Container element
 * @param {string} message - Error message
 */
function renderMatchXGError(container, message) {
    if (!container) return;

    container.innerHTML = `
        <div class="match-xg-card match-xg-card--error">
            <div class="match-xg-card__error">
                <span class="match-xg-card__error-icon">⚠️</span>
                <h4 class="match-xg-card__error-title">xG Prediction Unavailable</h4>
                <p class="match-xg-card__error-message">${escapeHtml(message || 'Failed to load xG prediction')}</p>
            </div>
        </div>
    `;
}

/**
 * Fetch match xG prediction and render card
 * @param {HTMLElement} container - Container element
 * @param {string} homeTeam - Home team name
 * @param {string} awayTeam - Away team name
 * @returns {Promise<Object>} Prediction data
 */
async function fetchAndRenderMatchXGCard(container, homeTeam, awayTeam) {
    if (!container || !homeTeam || !awayTeam) {
        console.error('[MatchXGCard] Missing container or team names');
        return null;
    }

    renderMatchXGLoading(container);

    try {
        const params = new URLSearchParams({
            home: homeTeam,
            away: awayTeam
        });

        const url = `${window.location.origin}/api/matches/predict-xg?${params.toString()}`;

        const response = await fetch(url);

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        renderMatchXGCard(container, data);
        return data;

    } catch (error) {
        console.error('[MatchXGCard] Failed to fetch xG prediction:', error);
        renderMatchXGError(container, error.message);
        return null;
    }
}

// ══════════════════════════════════════════════════════════════════════
// EXPORTS
// ══════════════════════════════════════════════════════════════════════

export {
    renderMatchXGCard,
    renderMatchXGLoading,
    renderMatchXGError,
    fetchAndRenderMatchXGCard,
    PROBABILITY_THRESHOLDS,
    XG_MATCH_THRESHOLDS
};


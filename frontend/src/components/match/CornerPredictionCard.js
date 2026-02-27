/**
 * Corner Prediction Card Module
 * ==============================
 *
 * Renders a match corner prediction card with:
 * - Animated expected total corners counter
 * - Home vs Away comparison bars
 * - Probability bars for over 9.5 / 10.5 / 11.5
 * - Clean match preview layout
 *
 * @module CornerPredictionCard
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
    HIGH: 0.6,      // > 60% = Green
    MEDIUM: 0.4,    // 40-60% = Yellow
    LOW: 0          // < 40% = Red
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
 * @param {number} decimals - Number of decimal places
 * @returns {string} Formatted number
 */
function formatNumber(value, decimals = 1) {
    if (value == null || isNaN(value)) return '0.0';
    return Number(value).toFixed(decimals);
}

/**
 * Format probability as percentage
 * @param {number} value - Value between 0 and 1
 * @returns {string} Formatted percentage
 */
function formatProbability(value) {
    if (value == null || isNaN(value)) return '0%';
    return `${Math.round(value * 100)}%`;
}

/**
 * Get color class based on probability value
 * @param {number} probability - Probability value (0-1)
 * @returns {string} CSS class name
 */
function getProbabilityColorClass(probability) {
    if (probability == null || isNaN(probability)) return 'low';
    if (probability >= PROBABILITY_THRESHOLDS.HIGH) return 'high';
    if (probability >= PROBABILITY_THRESHOLDS.MEDIUM) return 'medium';
    return 'low';
}

/**
 * Animate a number counter from start to end
 * @param {HTMLElement} element - Element to animate
 * @param {number} start - Start value
 * @param {number} end - End value
 * @param {number} duration - Animation duration in ms
 * @param {number} decimals - Decimal places
 */
function animateCounter(element, start, end, duration, decimals = 1) {
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
 * Create the match preview header
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Header element
 */
function createMatchHeader(data) {
    const header = document.createElement('div');
    header.className = 'corner-prediction-card__header';

    header.innerHTML = `
        <div class="corner-prediction-card__match-info">
            <span class="corner-prediction-card__team corner-prediction-card__team--home">
                ${escapeHtml(data.homeTeam)}
            </span>
            <span class="corner-prediction-card__vs">vs</span>
            <span class="corner-prediction-card__team corner-prediction-card__team--away">
                ${escapeHtml(data.awayTeam)}
            </span>
        </div>
        <div class="corner-prediction-card__badge">
            <span class="corner-prediction-card__badge-icon">⚑</span>
            <span class="corner-prediction-card__badge-text">Corner Prediction</span>
        </div>
    `;

    return header;
}

/**
 * Create the animated total corners counter
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Counter element
 */
function createTotalCounter(data) {
    const container = document.createElement('div');
    container.className = 'corner-prediction-card__total-counter';

    const expectedTotal = Number(data.expectedTotalCorners) || 0;

    container.innerHTML = `
        <div class="corner-prediction-card__counter-label">Expected Total Corners</div>
        <div class="corner-prediction-card__counter-value" data-target="${expectedTotal}">0.0</div>
        <div class="corner-prediction-card__counter-subtitle">Combined prediction for both teams</div>
    `;

    // Animate counter after render
    requestAnimationFrame(() => {
        setTimeout(() => {
            const counterEl = container.querySelector('.corner-prediction-card__counter-value');
            if (counterEl) {
                animateCounter(counterEl, 0, expectedTotal, COUNTER_ANIMATION_DURATION, 1);
            }
        }, 300);
    });

    return container;
}

/**
 * Create the home vs away comparison section
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Comparison element
 */
function createTeamComparison(data) {
    const container = document.createElement('div');
    container.className = 'corner-prediction-card__comparison';

    const homeCorners = Number(data.expectedHomeCorners) || 0;
    const awayCorners = Number(data.expectedAwayCorners) || 0;
    const totalCorners = homeCorners + awayCorners;

    // Calculate percentages for the stacked bar
    const homePercent = totalCorners > 0 ? (homeCorners / totalCorners) * 100 : 50;
    const awayPercent = 100 - homePercent;

    container.innerHTML = `
        <div class="corner-prediction-card__comparison-header">
            <span class="corner-prediction-card__comparison-title">Expected Distribution</span>
        </div>
        <div class="corner-prediction-card__comparison-teams">
            <div class="corner-prediction-card__comparison-team corner-prediction-card__comparison-team--home">
                <span class="corner-prediction-card__comparison-team-name">${escapeHtml(data.homeTeam)}</span>
                <span class="corner-prediction-card__comparison-team-value">${formatNumber(homeCorners)}</span>
            </div>
            <div class="corner-prediction-card__comparison-team corner-prediction-card__comparison-team--away">
                <span class="corner-prediction-card__comparison-team-name">${escapeHtml(data.awayTeam)}</span>
                <span class="corner-prediction-card__comparison-team-value">${formatNumber(awayCorners)}</span>
            </div>
        </div>
        <div class="corner-prediction-card__stacked-bar">
            <div class="corner-prediction-card__stacked-bar-home" style="width: 0%"></div>
            <div class="corner-prediction-card__stacked-bar-away" style="width: 0%"></div>
        </div>
        <div class="corner-prediction-card__comparison-percentages">
            <span class="corner-prediction-card__comparison-percent">${Math.round(homePercent)}%</span>
            <span class="corner-prediction-card__comparison-percent">${Math.round(awayPercent)}%</span>
        </div>
    `;

    // Animate bars after render
    requestAnimationFrame(() => {
        setTimeout(() => {
            const homeBar = container.querySelector('.corner-prediction-card__stacked-bar-home');
            const awayBar = container.querySelector('.corner-prediction-card__stacked-bar-away');
            if (homeBar) homeBar.style.width = `${homePercent}%`;
            if (awayBar) awayBar.style.width = `${awayPercent}%`;
        }, 400);
    });

    return container;
}

/**
 * Create a probability bar item
 * @param {string} label - Probability label (e.g., "Over 10.5")
 * @param {number} probability - Probability value (0-1)
 * @param {number} delay - Animation delay in ms
 * @returns {HTMLElement} Probability bar element
 */
function createProbabilityBar(label, probability, delay = 0) {
    const container = document.createElement('div');
    container.className = 'corner-prediction-card__prob-item';

    const probValue = Number(probability) || 0;
    const colorClass = getProbabilityColorClass(probValue);

    container.innerHTML = `
        <div class="corner-prediction-card__prob-header">
            <span class="corner-prediction-card__prob-label">${escapeHtml(label)}</span>
            <span class="corner-prediction-card__prob-value corner-prediction-card__prob-value--${colorClass}">
                ${formatProbability(probValue)}
            </span>
        </div>
        <div class="corner-prediction-card__prob-bar-container">
            <div class="corner-prediction-card__prob-bar corner-prediction-card__prob-bar--${colorClass}"
                 style="width: 0%">
            </div>
        </div>
    `;

    // Animate bar after render
    requestAnimationFrame(() => {
        setTimeout(() => {
            const bar = container.querySelector('.corner-prediction-card__prob-bar');
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
    section.className = 'corner-prediction-card__probabilities';

    const sectionHeader = document.createElement('div');
    sectionHeader.className = 'corner-prediction-card__section-header';
    sectionHeader.innerHTML = `
        <span class="corner-prediction-card__section-title">Over/Under Probabilities</span>
        <span class="corner-prediction-card__section-icon">📊</span>
    `;

    const probsContainer = document.createElement('div');
    probsContainer.className = 'corner-prediction-card__probs-container';

    // Create probability bars with staggered animation
    probsContainer.appendChild(createProbabilityBar('Over 9.5', data.probOver9_5, 0));
    probsContainer.appendChild(createProbabilityBar('Over 10.5', data.probOver10_5, 100));
    probsContainer.appendChild(createProbabilityBar('Over 11.5', data.probOver11_5, 200));

    section.appendChild(sectionHeader);
    section.appendChild(probsContainer);

    return section;
}

/**
 * Create the confidence indicator
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Confidence indicator element
 */
function createConfidenceIndicator(data) {
    const container = document.createElement('div');
    container.className = 'corner-prediction-card__confidence';

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
        <div class="corner-prediction-card__confidence-header">
            <span class="corner-prediction-card__confidence-label">Model Confidence</span>
            <span class="corner-prediction-card__confidence-badge corner-prediction-card__confidence-badge--${confidenceClass}">
                ${confidenceLabel} (${formatProbability(confidence)})
            </span>
        </div>
        <div class="corner-prediction-card__confidence-details">
            <span class="corner-prediction-card__confidence-detail">
                📈 ${data.homeMatchesAnalyzed || 0} home matches analyzed
            </span>
            <span class="corner-prediction-card__confidence-detail">
                📈 ${data.awayMatchesAnalyzed || 0} away matches analyzed
            </span>
        </div>
    `;

    return container;
}

/**
 * Create the footer with additional info
 * @param {Object} data - Prediction data
 * @returns {HTMLElement} Footer element
 */
function createFooter(data) {
    const footer = document.createElement('div');
    footer.className = 'corner-prediction-card__footer';

    footer.innerHTML = `
        <div class="corner-prediction-card__footer-note">
            Based on weighted historical averages with recency factor
        </div>
    `;

    return footer;
}

// ══════════════════════════════════════════════════════════════════════
// MAIN RENDER FUNCTION
// ══════════════════════════════════════════════════════════════════════

/**
 * Render a corner prediction card into a container
 * @param {HTMLElement} container - Container element to render into
 * @param {Object} predictionData - Corner prediction data
 * @param {string} predictionData.homeTeam - Home team name
 * @param {string} predictionData.awayTeam - Away team name
 * @param {number} predictionData.expectedTotalCorners - Expected total corners
 * @param {number} predictionData.expectedHomeCorners - Expected home corners
 * @param {number} predictionData.expectedAwayCorners - Expected away corners
 * @param {number} predictionData.probOver9_5 - Probability of over 9.5 corners
 * @param {number} predictionData.probOver10_5 - Probability of over 10.5 corners
 * @param {number} predictionData.probOver11_5 - Probability of over 11.5 corners
 * @param {number} predictionData.confidence - Model confidence (0-1)
 * @param {number} predictionData.homeMatchesAnalyzed - Home team matches analyzed
 * @param {number} predictionData.awayMatchesAnalyzed - Away team matches analyzed
 */
function renderCornerPredictionCard(container, predictionData) {
    if (!container) {
        console.error('[CornerPredictionCard] Container element not provided');
        return;
    }

    // Defensive: handle missing or invalid data
    const safeData = {
        homeTeam: predictionData?.homeTeam || 'Home Team',
        awayTeam: predictionData?.awayTeam || 'Away Team',
        expectedTotalCorners: Number(predictionData?.expectedTotalCorners) || 0,
        expectedHomeCorners: Number(predictionData?.expectedHomeCorners) || 0,
        expectedAwayCorners: Number(predictionData?.expectedAwayCorners) || 0,
        probOver9_5: Number(predictionData?.probOver9_5) || 0,
        probOver10_5: Number(predictionData?.probOver10_5) || 0,
        probOver11_5: Number(predictionData?.probOver11_5) || 0,
        confidence: Number(predictionData?.confidence) || 0,
        homeMatchesAnalyzed: Number(predictionData?.homeMatchesAnalyzed) || 0,
        awayMatchesAnalyzed: Number(predictionData?.awayMatchesAnalyzed) || 0,
        homeWeightedCorners: Number(predictionData?.homeWeightedCorners) || 0,
        awayWeightedCorners: Number(predictionData?.awayWeightedCorners) || 0
    };

    // Validate: expectedTotalCorners should equal home + away
    const calculatedTotal = safeData.expectedHomeCorners + safeData.expectedAwayCorners;
    if (Math.abs(safeData.expectedTotalCorners - calculatedTotal) > 0.1) {
        console.warn('[CornerPredictionCard] Total corners mismatch:', {
            expected: safeData.expectedTotalCorners,
            calculated: calculatedTotal
        });
    }

    // Clear container
    container.innerHTML = '';

    // Create card element
    const card = document.createElement('div');
    card.className = 'corner-prediction-card';

    // Build card structure
    card.appendChild(createMatchHeader(safeData));
    card.appendChild(createTotalCounter(safeData));
    card.appendChild(createTeamComparison(safeData));
    card.appendChild(createProbabilitiesSection(safeData));
    card.appendChild(createConfidenceIndicator(safeData));
    card.appendChild(createFooter(safeData));

    container.appendChild(card);
}

/**
 * Render loading state for corner prediction card
 * @param {HTMLElement} container - Container element
 */
function renderCornerPredictionLoading(container) {
    if (!container) return;

    container.innerHTML = `
        <div class="corner-prediction-card corner-prediction-card--loading">
            <div class="corner-prediction-card__header">
                <div class="corner-prediction-card__skeleton corner-prediction-card__skeleton--teams"></div>
            </div>
            <div class="corner-prediction-card__skeleton-content">
                <div class="corner-prediction-card__skeleton corner-prediction-card__skeleton--counter"></div>
                <div class="corner-prediction-card__skeleton corner-prediction-card__skeleton--bar"></div>
                <div class="corner-prediction-card__skeleton corner-prediction-card__skeleton--bar"></div>
                <div class="corner-prediction-card__skeleton corner-prediction-card__skeleton--bar"></div>
            </div>
        </div>
    `;
}

/**
 * Render error state for corner prediction card
 * @param {HTMLElement} container - Container element
 * @param {string} message - Error message
 */
function renderCornerPredictionError(container, message) {
    if (!container) return;

    container.innerHTML = `
        <div class="corner-prediction-card corner-prediction-card--error">
            <div class="corner-prediction-card__error">
                <span class="corner-prediction-card__error-icon">⚠️</span>
                <h4 class="corner-prediction-card__error-title">Prediction Unavailable</h4>
                <p class="corner-prediction-card__error-message">${escapeHtml(message || 'Failed to load corner prediction')}</p>
            </div>
        </div>
    `;
}

/**
 * Fetch corner prediction and render card
 * @param {HTMLElement} container - Container element
 * @param {string} homeTeam - Home team name
 * @param {string} awayTeam - Away team name
 * @returns {Promise<Object>} Prediction data
 */
async function fetchAndRenderCornerPredictionCard(container, homeTeam, awayTeam) {
    if (!container || !homeTeam || !awayTeam) {
        console.error('[CornerPredictionCard] Missing container or team names');
        return null;
    }

    renderCornerPredictionLoading(container);

    try {
        const params = new URLSearchParams({
            home: homeTeam,
            away: awayTeam
        });

        const url = `${window.location.origin}/api/matches/predict-corners?${params.toString()}`;

        const response = await fetch(url);

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        renderCornerPredictionCard(container, data);
        return data;

    } catch (error) {
        console.error('[CornerPredictionCard] Failed to fetch corner prediction:', error);
        renderCornerPredictionError(container, error.message);
        return null;
    }
}

// ══════════════════════════════════════════════════════════════════════
// EXPORTS
// ══════════════════════════════════════════════════════════════════════

export {
    renderCornerPredictionCard,
    renderCornerPredictionLoading,
    renderCornerPredictionError,
    fetchAndRenderCornerPredictionCard,
    PROBABILITY_THRESHOLDS
};


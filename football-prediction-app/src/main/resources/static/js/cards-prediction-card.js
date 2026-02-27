/**
 * Cards Prediction Card Component
 * ================================
 *
 * Renders a cards prediction card with:
 * - Expected yellow cards display (home/away)
 * - Red card probability indicator
 * - Referee strictness impact
 * - Discipline warning badges
 *
 * Usage:
 *   window.CardsPredictionCard.render(container, data)
 *   window.CardsPredictionCard.fetchAndRender(container, homeTeam, awayTeam, referee)
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */

(function() {
    'use strict';

    // ══════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * High card risk threshold.
     */
    var HIGH_CARD_THRESHOLD = 5.0;

    /**
     * High red card risk threshold.
     */
    var HIGH_RED_THRESHOLD = 0.20;

    /**
     * Maximum yellow cards for bar scaling.
     */
    var MAX_YELLOWS_FOR_SCALE = 4;

    // ══════════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Escape HTML to prevent XSS.
     */
    function escapeHtml(str) {
        if (str == null) return '';
        var div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    /**
     * Format number to specified decimal places.
     */
    function formatNumber(value, decimals) {
        decimals = decimals !== undefined ? decimals : 1;
        if (value == null || isNaN(value)) return '0.0';
        return Number(value).toFixed(decimals);
    }

    /**
     * Format percentage.
     */
    function formatPercentage(value) {
        if (value == null || isNaN(value)) return '0%';
        return Math.round(value * 100) + '%';
    }

    /**
     * Calculate bar width percentage.
     */
    function calculateBarWidth(value, max) {
        max = max || MAX_YELLOWS_FOR_SCALE;
        if (value == null || isNaN(value) || max <= 0) return 0;
        return Math.min(100, Math.max(0, (value / max) * 100));
    }

    /**
     * Get color for red card probability.
     */
    function getRedProbColor(prob) {
        if (prob >= HIGH_RED_THRESHOLD) return '#ef4444';
        if (prob >= 0.10) return '#fbbf24';
        return '#22c55e';
    }

    /**
     * Get referee strictness label.
     */
    function getStrictnessLabel(strictness) {
        if (strictness >= 0.6) return 'Strict';
        if (strictness <= 0.4) return 'Lenient';
        return 'Average';
    }

    /**
     * Get strictness color.
     */
    function getStrictnessColor(strictness) {
        if (strictness >= 0.6) return '#ef4444';
        if (strictness <= 0.4) return '#22c55e';
        return '#fbbf24';
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER CARDS PREDICTION CARD
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render a cards prediction card.
     * @param {HTMLElement} container - Container element
     * @param {Object} data - Cards prediction data
     */
    function render(container, data) {
        if (!container) {
            console.error('[CardsPredictionCard] Container not provided');
            return;
        }

        // Defensive: handle missing data
        var safeData = {
            homeTeam: data && data.homeTeam || 'Home Team',
            awayTeam: data && data.awayTeam || 'Away Team',
            referee: data && data.referee || null,
            expectedYellowCardsHome: Number(data && data.expectedYellowCardsHome) || 0,
            expectedYellowCardsAway: Number(data && data.expectedYellowCardsAway) || 0,
            expectedTotalYellowCards: Number(data && data.expectedTotalYellowCards) || 0,
            redCardProbability: Number(data && data.redCardProbability) || 0,
            disciplineWarning: data && data.disciplineWarning || null,
            refereeAvgYellowCards: Number(data && data.refereeAvgYellowCards) || 0,
            refereeStrictnessIndex: Number(data && data.refereeStrictnessIndex) || 0.5,
            refereeImpact: data && data.refereeImpact || 'Unknown',
            homeMatchesAnalyzed: Number(data && data.homeMatchesAnalyzed) || 0,
            awayMatchesAnalyzed: Number(data && data.awayMatchesAnalyzed) || 0,
            confidence: Number(data && data.confidence) || 0
        };

        var redProbColor = getRedProbColor(safeData.redCardProbability);
        var strictnessColor = getStrictnessColor(safeData.refereeStrictnessIndex);
        var hasHighCardRisk = safeData.expectedTotalYellowCards > HIGH_CARD_THRESHOLD;
        var hasHighRedRisk = safeData.redCardProbability >= HIGH_RED_THRESHOLD;

        var confidenceLabel = safeData.confidence >= 0.7 ? 'High' : safeData.confidence >= 0.5 ? 'Medium' : 'Low';
        var confidenceColor = safeData.confidence >= 0.7 ? '#22c55e' : safeData.confidence >= 0.5 ? '#fbbf24' : '#ef4444';

        var warningHtml = '';
        if (safeData.disciplineWarning) {
            warningHtml = '\
                <div style="margin-bottom: 1rem; padding: 0.75rem 1rem; background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 0.5rem; display: flex; align-items: center; gap: 0.75rem;">\
                    <span style="font-size: 1.5rem;">⚠️</span>\
                    <div>\
                        <div style="font-size: 0.875rem; font-weight: 600; color: #ef4444;">' + escapeHtml(safeData.disciplineWarning) + '</div>\
                        <div style="font-size: 0.75rem; color: var(--text-muted, #94a3b8);">This match may see more cards than average</div>\
                    </div>\
                </div>';
        }

        var refereeHtml = '';
        if (safeData.referee) {
            refereeHtml = '\
                <div style="padding: 1rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem; margin-bottom: 1rem;">\
                    <div style="font-size: 0.8rem; font-weight: 600; color: var(--text-secondary, #cbd5e1); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.75rem;">👨‍⚖️ Referee Impact</div>\
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">\
                        <span style="font-size: 0.875rem; color: var(--text-primary, #f1f5f9);">' + escapeHtml(safeData.refereeImpact) + '</span>\
                    </div>\
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem;">\
                        <div style="text-align: center; padding: 0.5rem; background: var(--bg-secondary, #1e293b); border-radius: 0.375rem;">\
                            <div style="font-size: 1.125rem; font-weight: 700; color: var(--text-primary, #f1f5f9);">' + formatNumber(safeData.refereeAvgYellowCards) + '</div>\
                            <div style="font-size: 0.65rem; color: var(--text-muted, #94a3b8); text-transform: uppercase;">Avg Cards/Game</div>\
                        </div>\
                        <div style="text-align: center; padding: 0.5rem; background: var(--bg-secondary, #1e293b); border-radius: 0.375rem;">\
                            <div style="font-size: 1.125rem; font-weight: 700; color: ' + strictnessColor + ';">' + getStrictnessLabel(safeData.refereeStrictnessIndex) + '</div>\
                            <div style="font-size: 0.65rem; color: var(--text-muted, #94a3b8); text-transform: uppercase;">Strictness</div>\
                        </div>\
                    </div>\
                </div>';
        }

        container.innerHTML = '\
            <div class="cards-prediction-card">\
                <div class="cards-prediction-card__header">\
                    <div class="cards-prediction-card__title">\
                        <span class="cards-prediction-card__icon">🟨</span>\
                        <h3>Cards Prediction</h3>\
                    </div>\
                    <div class="cards-prediction-card__teams">\
                        <span class="cards-prediction-card__home-team">' + escapeHtml(safeData.homeTeam) + '</span>\
                        <span class="cards-prediction-card__vs">VS</span>\
                        <span class="cards-prediction-card__away-team">' + escapeHtml(safeData.awayTeam) + '</span>\
                    </div>\
                </div>\
                \
                ' + warningHtml + '\
                \
                <div class="cards-prediction-card__total">\
                    <div class="cards-prediction-card__total-label">Expected Total Yellow Cards</div>\
                    <div class="cards-prediction-card__total-value ' + (hasHighCardRisk ? 'cards-prediction-card__total-value--high' : '') + '">\
                        🟨 ' + formatNumber(safeData.expectedTotalYellowCards) + '\
                    </div>\
                </div>\
                \
                <div class="cards-prediction-card__breakdown">\
                    <div class="cards-prediction-card__section-title">Expected Cards by Team</div>\
                    \
                    <div class="cards-prediction-card__team-row">\
                        <div class="cards-prediction-card__team-info">\
                            <span class="cards-prediction-card__team-label">' + escapeHtml(safeData.homeTeam) + '</span>\
                            <span class="cards-prediction-card__team-value">🟨 ' + formatNumber(safeData.expectedYellowCardsHome) + '</span>\
                        </div>\
                        <div class="cards-prediction-card__bar-container">\
                            <div class="cards-prediction-card__bar cards-prediction-card__bar--home" style="width: ' + calculateBarWidth(safeData.expectedYellowCardsHome) + '%;"></div>\
                        </div>\
                    </div>\
                    \
                    <div class="cards-prediction-card__team-row">\
                        <div class="cards-prediction-card__team-info">\
                            <span class="cards-prediction-card__team-label">' + escapeHtml(safeData.awayTeam) + '</span>\
                            <span class="cards-prediction-card__team-value">🟨 ' + formatNumber(safeData.expectedYellowCardsAway) + '</span>\
                        </div>\
                        <div class="cards-prediction-card__bar-container">\
                            <div class="cards-prediction-card__bar cards-prediction-card__bar--away" style="width: ' + calculateBarWidth(safeData.expectedYellowCardsAway) + '%;"></div>\
                        </div>\
                    </div>\
                </div>\
                \
                <div class="cards-prediction-card__red-card">\
                    <div class="cards-prediction-card__red-header">\
                        <span>🟥 Red Card Probability</span>\
                        <span class="cards-prediction-card__red-value" style="color: ' + redProbColor + ';">' + formatPercentage(safeData.redCardProbability) + '</span>\
                    </div>\
                    <div class="cards-prediction-card__red-bar-container">\
                        <div class="cards-prediction-card__red-bar" style="width: ' + (safeData.redCardProbability * 100) + '%; background: ' + redProbColor + ';"></div>\
                    </div>\
                    <div class="cards-prediction-card__red-note">\
                        ' + (hasHighRedRisk ? '⚠️ Above average red card risk' : 'Average or below red card risk') + '\
                    </div>\
                </div>\
                \
                ' + refereeHtml + '\
                \
                <div class="cards-prediction-card__footer">\
                    <span class="cards-prediction-card__confidence" style="background: rgba(' + (confidenceColor === '#22c55e' ? '34, 197, 94' : confidenceColor === '#fbbf24' ? '251, 191, 36' : '239, 68, 68') + ', 0.15); color: ' + confidenceColor + ';">\
                        ' + confidenceLabel + ' confidence\
                    </span>\
                    <span class="cards-prediction-card__matches">\
                        📊 ' + safeData.homeMatchesAnalyzed + ' / ' + safeData.awayMatchesAnalyzed + ' matches analyzed\
                    </span>\
                </div>\
            </div>';
    }

    /**
     * Render loading state.
     */
    function renderLoading(container) {
        if (!container) return;
        container.innerHTML = '\
            <div class="cards-prediction-card cards-prediction-card--loading">\
                <div class="cards-prediction-card__loading-spinner"></div>\
                <span class="cards-prediction-card__loading-text">Loading cards prediction...</span>\
            </div>';
    }

    /**
     * Render error state.
     */
    function renderError(container, message) {
        if (!container) return;
        container.innerHTML = '\
            <div class="cards-prediction-card cards-prediction-card--error">\
                <span class="cards-prediction-card__error-icon">⚠️</span>\
                <span class="cards-prediction-card__error-text">' + escapeHtml(message || 'Failed to load cards prediction') + '</span>\
            </div>';
    }

    /**
     * Fetch cards prediction and render.
     */
    function fetchAndRender(container, homeTeam, awayTeam, referee) {
        if (!container || !homeTeam || !awayTeam) {
            console.error('[CardsPredictionCard] Missing container or team names');
            return Promise.resolve(null);
        }

        renderLoading(container);

        var url = '/api/matches/predict-cards?home=' + encodeURIComponent(homeTeam) +
                  '&away=' + encodeURIComponent(awayTeam);

        if (referee) {
            url += '&referee=' + encodeURIComponent(referee);
        }

        return fetch(url)
            .then(function(response) {
                if (!response.ok) {
                    return response.json().then(function(err) {
                        throw new Error(err.message || 'HTTP ' + response.status);
                    }).catch(function() {
                        throw new Error('HTTP ' + response.status);
                    });
                }
                return response.json();
            })
            .then(function(data) {
                render(container, data);
                return data;
            })
            .catch(function(error) {
                console.error('[CardsPredictionCard] Fetch error:', error);
                renderError(container, error.message);
                return null;
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT TO GLOBAL SCOPE
    // ══════════════════════════════════════════════════════════════════════

    window.CardsPredictionCard = {
        render: render,
        renderLoading: renderLoading,
        renderError: renderError,
        fetchAndRender: fetchAndRender,
        HIGH_CARD_THRESHOLD: HIGH_CARD_THRESHOLD,
        HIGH_RED_THRESHOLD: HIGH_RED_THRESHOLD
    };

    console.log('[CardsPredictionCard] Module initialized');

})();


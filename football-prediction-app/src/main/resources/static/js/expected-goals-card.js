/**
 * Expected Goals (xG) Card Component
 * =====================================
 *
 * Renders expected goals statistics cards with:
 * - Animated counters for xG, actual goals, and difference
 * - Comparison bars (xG vs actual)
 * - Color coding: green (overperforming), red (underperforming)
 * - Shot conversion rate with league average marker
 * - Defensive xG section
 *
 * Usage:
 *   window.ExpectedGoalsCard.render(container, data)
 *   window.ExpectedGoalsCard.fetchAndRender(container, teamName, isHome)
 *   window.ExpectedGoalsCard.renderLoading(container)
 *   window.ExpectedGoalsCard.renderError(container, message)
 *   window.ExpectedGoalsCard.fetchAndRenderPrediction(container, homeTeam, awayTeam)
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */

(function() {
    'use strict';

    // ══════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════

    var LEAGUE_AVERAGE_XG = 1.3;
    var MAX_XG_FOR_SCALE = 3.5;
    var COUNTER_ANIMATION_DURATION = 1500;

    var PERFORMANCE_THRESHOLDS = {
        STRONG_OVER: 0.3,
        SLIGHT_OVER: 0.05,
        SLIGHT_UNDER: -0.05,
        STRONG_UNDER: -0.3
    };

    // ══════════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    function escapeHtml(str) {
        if (str == null) return '';
        var div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    function formatNumber(value, decimals) {
        decimals = decimals != null ? decimals : 2;
        if (value == null || isNaN(value)) return '0.00';
        return Number(value).toFixed(decimals);
    }

    function formatPercentage(value) {
        if (value == null || isNaN(value)) return '0%';
        return Math.round(value * 100) + '%';
    }

    function getPerformanceColorClass(diff) {
        if (diff == null || isNaN(diff)) return 'neutral';
        if (diff > PERFORMANCE_THRESHOLDS.STRONG_OVER) return 'strong-over';
        if (diff > PERFORMANCE_THRESHOLDS.SLIGHT_OVER) return 'slight-over';
        if (diff < PERFORMANCE_THRESHOLDS.STRONG_UNDER) return 'strong-under';
        if (diff < PERFORMANCE_THRESHOLDS.SLIGHT_UNDER) return 'slight-under';
        return 'neutral';
    }

    function calculateBarWidth(value, max) {
        max = max || MAX_XG_FOR_SCALE;
        if (value == null || isNaN(value) || max <= 0) return 0;
        return Math.min(100, Math.max(0, (value / max) * 100));
    }

    function animateCounter(element, start, end, duration, decimals) {
        if (!element) return;
        decimals = decimals != null ? decimals : 2;
        var startTime = performance.now();
        var difference = end - start;

        function updateCounter(currentTime) {
            var elapsed = currentTime - startTime;
            var progress = Math.min(elapsed / duration, 1);
            var easeProgress = 1 - Math.pow(1 - progress, 3);
            var currentValue = start + (difference * easeProgress);
            element.textContent = formatNumber(currentValue, decimals);
            if (progress < 1) {
                requestAnimationFrame(updateCounter);
            }
        }
        requestAnimationFrame(updateCounter);
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER - TEAM XG CARD
    // ══════════════════════════════════════════════════════════════════════

    function renderExpectedGoalsCard(container, data) {
        if (!container) {
            console.error('[ExpectedGoalsCard] Container is required');
            return;
        }

        var d = {
            teamName: (data && data.teamName) || 'Unknown Team',
            isHome: data ? data.isHome : null,
            avgShotsOnTarget: Number((data && data.avgShotsOnTarget) || 0),
            expectedGoals: Number((data && data.expectedGoals) || 0),
            actualGoals: Number((data && data.actualGoals) || 0),
            xGDifference: Number((data && data.xGDifference) || 0),
            conversionRate: Number((data && data.conversionRate) || 0),
            leagueConversionRate: Number((data && data.leagueConversionRate) || 0.28),
            performance: (data && data.performance) || '',
            matchesAnalyzed: Number((data && data.matchesAnalyzed) || 0),
            totalShotsOnTarget: Number((data && data.totalShotsOnTarget) || 0),
            totalGoals: Number((data && data.totalGoals) || 0),
            weightedXG: Number((data && data.weightedXG) || 0),
            avgShotsOnTargetAgainst: Number((data && data.avgShotsOnTargetAgainst) || 0),
            expectedGoalsAgainst: Number((data && data.expectedGoalsAgainst) || 0)
        };

        var perfClass = getPerformanceColorClass(d.xGDifference);
        var venueBadge = d.isHome === true ? 'home' : d.isHome === false ? 'away' : 'all';
        var venueText = d.isHome === true ? 'HOME' : d.isHome === false ? 'AWAY' : 'ALL';

        var rateClass = d.conversionRate > d.leagueConversionRate ? 'above' : d.conversionRate < d.leagueConversionRate ? 'below' : 'average';
        var diffFromLeague = d.expectedGoals - LEAGUE_AVERAGE_XG;
        var diffText = diffFromLeague >= 0 ? '+' + formatNumber(diffFromLeague, 1) : formatNumber(diffFromLeague, 1);
        var diffClass = diffFromLeague > 0 ? 'positive' : diffFromLeague < 0 ? 'negative' : 'neutral';

        var confidenceText = 'Low confidence';
        var confidenceClass = 'low';
        if (d.matchesAnalyzed >= 15) { confidenceText = 'High confidence'; confidenceClass = 'high'; }
        else if (d.matchesAnalyzed >= 8) { confidenceText = 'Medium confidence'; confidenceClass = 'medium'; }

        container.innerHTML = '';

        var card = document.createElement('div');
        card.className = 'xg-card';
        card.innerHTML = '\
            <div class="xg-card__header">\
                <h3 class="xg-card__title">🎯 ' + escapeHtml(d.teamName) + ' Expected Goals (xG)</h3>\
                <span class="xg-card__venue-badge xg-card__venue-badge--' + venueBadge + '">' + venueText + '</span>\
            </div>\
            <div class="xg-card__counter-section">\
                <div class="xg-card__counter-grid">\
                    <div class="xg-card__counter-item">\
                        <span class="xg-card__counter-label">Expected Goals (xG)</span>\
                        <span class="xg-card__counter-value xg-card__counter-value--xg" data-target="' + d.expectedGoals + '">0.00</span>\
                        <span class="xg-card__counter-sublabel">per match</span>\
                    </div>\
                    <div class="xg-card__counter-item">\
                        <span class="xg-card__counter-label">Actual Goals</span>\
                        <span class="xg-card__counter-value xg-card__counter-value--actual" data-target="' + d.actualGoals + '">0.00</span>\
                        <span class="xg-card__counter-sublabel">per match</span>\
                    </div>\
                    <div class="xg-card__counter-item xg-card__counter-item--diff">\
                        <span class="xg-card__counter-label">xG Difference</span>\
                        <span class="xg-card__counter-value xg-card__counter-value--' + perfClass + '" data-target="' + d.xGDifference + '">0.00</span>\
                        <span class="xg-card__counter-sublabel xg-card__performance-badge xg-card__performance-badge--' + perfClass + '">\
                            ' + escapeHtml(d.performance) + '\
                        </span>\
                    </div>\
                </div>\
            </div>\
            <div class="xg-card__comparison">\
                <h4 class="xg-card__section-title">xG vs Actual Comparison</h4>\
                <div class="xg-card__bar-stat">\
                    <div class="xg-card__bar-label-row">\
                        <span class="xg-card__bar-label">Expected Goals (xG)</span>\
                        <span class="xg-card__bar-value">' + formatNumber(d.expectedGoals) + '</span>\
                    </div>\
                    <div class="xg-card__bar-container">\
                        <div class="xg-card__bar xg-card__bar--xg" style="width: 0%"></div>\
                    </div>\
                </div>\
                <div class="xg-card__bar-stat">\
                    <div class="xg-card__bar-label-row">\
                        <span class="xg-card__bar-label">Actual Goals</span>\
                        <span class="xg-card__bar-value">' + formatNumber(d.actualGoals) + '</span>\
                    </div>\
                    <div class="xg-card__bar-container">\
                        <div class="xg-card__bar xg-card__bar--actual" style="width: 0%"></div>\
                    </div>\
                </div>\
                <div class="xg-card__league-comparison">\
                    <span class="xg-card__league-comparison-label">vs League Avg xG (' + LEAGUE_AVERAGE_XG + '):</span>\
                    <span class="xg-card__league-comparison-value xg-card__league-comparison-value--' + diffClass + '">' + diffText + '</span>\
                </div>\
            </div>\
            <div class="xg-card__conversion">\
                <div class="xg-card__conversion-header">\
                    <span class="xg-card__conversion-label">Shot Conversion Rate</span>\
                    <span class="xg-card__conversion-value xg-card__conversion-value--' + rateClass + '">' + formatPercentage(d.conversionRate) + '</span>\
                </div>\
                <div class="xg-card__conversion-bar-container">\
                    <div class="xg-card__conversion-bar xg-card__conversion-bar--' + rateClass + '" style="width: 0%"></div>\
                    <div class="xg-card__conversion-marker" style="left: ' + Math.min(100, d.leagueConversionRate * 100 / 0.5) + '%">\
                        <span class="xg-card__conversion-marker-label">League ' + formatPercentage(d.leagueConversionRate) + '</span>\
                    </div>\
                </div>\
                <div class="xg-card__conversion-labels"><span>Low</span><span>High</span></div>\
            </div>\
            <div class="xg-card__defensive">\
                <h4 class="xg-card__section-title">Defensive xG</h4>\
                <div class="xg-card__defensive-grid">\
                    <div class="xg-card__defensive-item">\
                        <span class="xg-card__defensive-label">Avg SOT Against</span>\
                        <span class="xg-card__defensive-value">' + formatNumber(d.avgShotsOnTargetAgainst) + '</span>\
                    </div>\
                    <div class="xg-card__defensive-item">\
                        <span class="xg-card__defensive-label">xG Against</span>\
                        <span class="xg-card__defensive-value">' + formatNumber(d.expectedGoalsAgainst) + '</span>\
                    </div>\
                </div>\
            </div>\
            <div class="xg-card__stats-grid">\
                <div class="xg-card__stat-item"><span class="xg-card__stat-icon">🎯</span><span class="xg-card__stat-value">' + d.totalShotsOnTarget + '</span><span class="xg-card__stat-label">Shots on Target</span></div>\
                <div class="xg-card__stat-item"><span class="xg-card__stat-icon">⚽</span><span class="xg-card__stat-value">' + d.totalGoals + '</span><span class="xg-card__stat-label">Goals Scored</span></div>\
                <div class="xg-card__stat-item"><span class="xg-card__stat-icon">📊</span><span class="xg-card__stat-value">' + d.matchesAnalyzed + '</span><span class="xg-card__stat-label">Matches</span></div>\
                <div class="xg-card__stat-item"><span class="xg-card__stat-icon">📈</span><span class="xg-card__stat-value">' + formatNumber(d.weightedXG) + '</span><span class="xg-card__stat-label">Weighted xG</span></div>\
            </div>\
            <div class="xg-card__footer">\
                <span class="xg-card__confidence xg-card__confidence--' + confidenceClass + '">' + confidenceText + ' (' + d.matchesAnalyzed + ' matches)</span>\
            </div>';

        container.appendChild(card);

        // Animate counters
        requestAnimationFrame(function() {
            setTimeout(function() {
                var xgEl = card.querySelector('.xg-card__counter-value--xg');
                var actualEl = card.querySelector('.xg-card__counter-value--actual');
                var diffEl = card.querySelector('.xg-card__counter-value--' + perfClass);

                if (xgEl) animateCounter(xgEl, 0, d.expectedGoals, COUNTER_ANIMATION_DURATION);
                if (actualEl) animateCounter(actualEl, 0, d.actualGoals, COUNTER_ANIMATION_DURATION);
                if (diffEl) {
                    animateCounter(diffEl, 0, d.xGDifference, COUNTER_ANIMATION_DURATION);
                    setTimeout(function() {
                        if (diffEl && d.xGDifference > 0) {
                            diffEl.textContent = '+' + diffEl.textContent;
                        }
                    }, COUNTER_ANIMATION_DURATION + 50);
                }
            }, 200);
        });

        // Animate bars
        requestAnimationFrame(function() {
            setTimeout(function() {
                var xgBar = card.querySelector('.xg-card__bar--xg');
                var actualBar = card.querySelector('.xg-card__bar--actual');
                var convBar = card.querySelector('.xg-card__conversion-bar');
                if (xgBar) xgBar.style.width = calculateBarWidth(d.expectedGoals) + '%';
                if (actualBar) actualBar.style.width = calculateBarWidth(d.actualGoals) + '%';
                if (convBar) convBar.style.width = Math.min(100, d.conversionRate * 100 / 0.5) + '%';
            }, 100);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER - MATCH XG PREDICTION
    // ══════════════════════════════════════════════════════════════════════

    function renderMatchXGPrediction(container, data) {
        if (!container) {
            console.error('[ExpectedGoalsCard] Container is required');
            return;
        }

        var d = {
            homeTeam: (data && data.homeTeam) || 'Home Team',
            awayTeam: (data && data.awayTeam) || 'Away Team',
            homeXG: Number((data && data.homeXG) || 0),
            awayXG: Number((data && data.awayXG) || 0),
            totalXG: Number((data && data.totalXG) || 0),
            prediction: (data && data.prediction) || '',
            probOver1_5: Number((data && data.probOver1_5) || 0),
            probOver2_5: Number((data && data.probOver2_5) || 0),
            probOver3_5: Number((data && data.probOver3_5) || 0),
            confidence: Number((data && data.confidence) || 0),
            homeShotsOnTarget: Number((data && data.homeShotsOnTarget) || 0),
            awayShotsOnTarget: Number((data && data.awayShotsOnTarget) || 0),
            homeMatchesAnalyzed: Number((data && data.homeMatchesAnalyzed) || 0),
            awayMatchesAnalyzed: Number((data && data.awayMatchesAnalyzed) || 0),
            recommendation: (data && data.recommendation) || ''
        };

        var totalXG = d.totalXG || (d.homeXG + d.awayXG);
        var homePercent = totalXG > 0 ? (d.homeXG / totalXG) * 100 : 50;
        var awayPercent = 100 - homePercent;

        var scoringClass = totalXG >= 3.5 ? 'high-scoring' : totalXG >= 2.5 ? 'moderate' : totalXG >= 1.5 ? 'low-scoring' : 'very-low';

        function probColor(p) { return p >= 0.6 ? 'high' : p >= 0.4 ? 'medium' : 'low'; }

        var confLabel = 'Low';
        var confClass = 'low';
        if (d.confidence >= 0.7) { confLabel = 'High'; confClass = 'high'; }
        else if (d.confidence >= 0.5) { confLabel = 'Medium'; confClass = 'medium'; }

        container.innerHTML = '';

        var card = document.createElement('div');
        card.className = 'match-xg-card';
        card.innerHTML = '\
            <div class="match-xg-card__header">\
                <div class="match-xg-card__match-info">\
                    <span class="match-xg-card__team match-xg-card__team--home">' + escapeHtml(d.homeTeam) + '</span>\
                    <span class="match-xg-card__vs">vs</span>\
                    <span class="match-xg-card__team match-xg-card__team--away">' + escapeHtml(d.awayTeam) + '</span>\
                </div>\
                <div class="match-xg-card__badge">\
                    <span class="match-xg-card__badge-icon">🎯</span>\
                    <span class="match-xg-card__badge-text">xG Prediction</span>\
                </div>\
            </div>\
            <div class="match-xg-card__total-counter">\
                <div class="match-xg-card__counter-label">Expected Total Goals</div>\
                <div class="match-xg-card__counter-value match-xg-card__counter-value--' + scoringClass + '">0.0</div>\
                <div class="match-xg-card__counter-subtitle">' + escapeHtml(d.recommendation) + '</div>\
            </div>\
            <div class="match-xg-card__comparison">\
                <div class="match-xg-card__comparison-header">\
                    <span class="match-xg-card__comparison-title">Expected Goals Distribution</span>\
                </div>\
                <div class="match-xg-card__comparison-teams">\
                    <div class="match-xg-card__comparison-team match-xg-card__comparison-team--home">\
                        <span class="match-xg-card__comparison-team-name">' + escapeHtml(d.homeTeam) + '</span>\
                        <span class="match-xg-card__comparison-team-value">' + formatNumber(d.homeXG, 1) + '</span>\
                    </div>\
                    <div class="match-xg-card__comparison-team match-xg-card__comparison-team--away">\
                        <span class="match-xg-card__comparison-team-name">' + escapeHtml(d.awayTeam) + '</span>\
                        <span class="match-xg-card__comparison-team-value">' + formatNumber(d.awayXG, 1) + '</span>\
                    </div>\
                </div>\
                <div class="match-xg-card__stacked-bar">\
                    <div class="match-xg-card__stacked-bar-home" style="width: 0%"></div>\
                    <div class="match-xg-card__stacked-bar-away" style="width: 0%"></div>\
                </div>\
                <div class="match-xg-card__comparison-percentages">\
                    <span class="match-xg-card__comparison-percent">' + Math.round(homePercent) + '%</span>\
                    <span class="match-xg-card__comparison-percent">' + Math.round(awayPercent) + '%</span>\
                </div>\
            </div>\
            <div class="match-xg-card__sot-info">\
                <div class="match-xg-card__section-header">\
                    <span class="match-xg-card__section-title">Shots on Target Average</span>\
                    <span class="match-xg-card__section-icon">🎯</span>\
                </div>\
                <div class="match-xg-card__sot-grid">\
                    <div class="match-xg-card__sot-item">\
                        <span class="match-xg-card__sot-team">' + escapeHtml(d.homeTeam) + '</span>\
                        <span class="match-xg-card__sot-value">' + formatNumber(d.homeShotsOnTarget, 1) + '</span>\
                    </div>\
                    <div class="match-xg-card__sot-item">\
                        <span class="match-xg-card__sot-team">' + escapeHtml(d.awayTeam) + '</span>\
                        <span class="match-xg-card__sot-value">' + formatNumber(d.awayShotsOnTarget, 1) + '</span>\
                    </div>\
                </div>\
            </div>\
            <div class="match-xg-card__probabilities">\
                <div class="match-xg-card__section-header">\
                    <span class="match-xg-card__section-title">Goal Over/Under Probabilities</span>\
                    <span class="match-xg-card__section-icon">📊</span>\
                </div>\
                <div class="match-xg-card__probs-container">\
                    <div class="match-xg-card__prob-item"><div class="match-xg-card__prob-header"><span class="match-xg-card__prob-label">Over 1.5 Goals</span><span class="match-xg-card__prob-value match-xg-card__prob-value--' + probColor(d.probOver1_5) + '">' + formatPercentage(d.probOver1_5) + '</span></div><div class="match-xg-card__prob-bar-container"><div class="match-xg-card__prob-bar match-xg-card__prob-bar--' + probColor(d.probOver1_5) + '" style="width: 0%" data-width="' + Math.min(100, d.probOver1_5 * 100) + '"></div></div></div>\
                    <div class="match-xg-card__prob-item"><div class="match-xg-card__prob-header"><span class="match-xg-card__prob-label">Over 2.5 Goals</span><span class="match-xg-card__prob-value match-xg-card__prob-value--' + probColor(d.probOver2_5) + '">' + formatPercentage(d.probOver2_5) + '</span></div><div class="match-xg-card__prob-bar-container"><div class="match-xg-card__prob-bar match-xg-card__prob-bar--' + probColor(d.probOver2_5) + '" style="width: 0%" data-width="' + Math.min(100, d.probOver2_5 * 100) + '"></div></div></div>\
                    <div class="match-xg-card__prob-item"><div class="match-xg-card__prob-header"><span class="match-xg-card__prob-label">Over 3.5 Goals</span><span class="match-xg-card__prob-value match-xg-card__prob-value--' + probColor(d.probOver3_5) + '">' + formatPercentage(d.probOver3_5) + '</span></div><div class="match-xg-card__prob-bar-container"><div class="match-xg-card__prob-bar match-xg-card__prob-bar--' + probColor(d.probOver3_5) + '" style="width: 0%" data-width="' + Math.min(100, d.probOver3_5 * 100) + '"></div></div></div>\
                </div>\
            </div>\
            <div class="match-xg-card__prediction-summary">\
                <div class="match-xg-card__prediction-box match-xg-card__prediction-box--' + scoringClass + '">\
                    <span class="match-xg-card__prediction-icon">⚽</span>\
                    <span class="match-xg-card__prediction-text">' + escapeHtml(d.prediction) + '</span>\
                </div>\
            </div>\
            <div class="match-xg-card__confidence">\
                <div class="match-xg-card__confidence-header">\
                    <span class="match-xg-card__confidence-label">Model Confidence</span>\
                    <span class="match-xg-card__confidence-badge match-xg-card__confidence-badge--' + confClass + '">' + confLabel + ' (' + formatPercentage(d.confidence) + ')</span>\
                </div>\
                <div class="match-xg-card__confidence-details">\
                    <span class="match-xg-card__confidence-detail">📈 ' + d.homeMatchesAnalyzed + ' home matches analyzed</span>\
                    <span class="match-xg-card__confidence-detail">📈 ' + d.awayMatchesAnalyzed + ' away matches analyzed</span>\
                </div>\
            </div>\
            <div class="match-xg-card__footer">\
                <div class="match-xg-card__footer-note">Based on shots on target × league conversion rate with recency weighting</div>\
            </div>';

        container.appendChild(card);

        // Animate total counter
        requestAnimationFrame(function() {
            setTimeout(function() {
                var counterEl = card.querySelector('.match-xg-card__counter-value');
                if (counterEl) animateCounter(counterEl, 0, totalXG, COUNTER_ANIMATION_DURATION, 1);
            }, 300);
        });

        // Animate stacked bar
        requestAnimationFrame(function() {
            setTimeout(function() {
                var homeBar = card.querySelector('.match-xg-card__stacked-bar-home');
                var awayBar = card.querySelector('.match-xg-card__stacked-bar-away');
                if (homeBar) homeBar.style.width = homePercent + '%';
                if (awayBar) awayBar.style.width = awayPercent + '%';
            }, 400);
        });

        // Animate probability bars
        requestAnimationFrame(function() {
            setTimeout(function() {
                var probBars = card.querySelectorAll('.match-xg-card__prob-bar');
                probBars.forEach(function(bar, i) {
                    setTimeout(function() {
                        var width = bar.getAttribute('data-width');
                        if (width) bar.style.width = width + '%';
                    }, i * 100);
                });
            }, 500);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // LOADING / ERROR STATES
    // ══════════════════════════════════════════════════════════════════════

    function renderLoading(container) {
        if (!container) return;
        container.innerHTML = '\
            <div class="xg-card xg-card--loading">\
                <div class="xg-card__header"><div class="xg-card__skeleton xg-card__skeleton--title"></div></div>\
                <div class="xg-card__skeleton-content">\
                    <div class="xg-card__skeleton xg-card__skeleton--counter"></div>\
                    <div class="xg-card__skeleton xg-card__skeleton--bar"></div>\
                    <div class="xg-card__skeleton xg-card__skeleton--bar"></div>\
                    <div class="xg-card__skeleton xg-card__skeleton--circle"></div>\
                </div>\
            </div>';
    }

    function renderError(container, message) {
        if (!container) return;
        container.innerHTML = '\
            <div class="xg-card xg-card--error">\
                <div class="xg-card__error">\
                    <span class="xg-card__error-icon">⚠️</span>\
                    <p class="xg-card__error-message">' + escapeHtml(message || 'Failed to load expected goals statistics') + '</p>\
                </div>\
            </div>';
    }

    function renderPredictionLoading(container) {
        if (!container) return;
        container.innerHTML = '\
            <div class="match-xg-card match-xg-card--loading">\
                <div class="match-xg-card__header"><div class="match-xg-card__skeleton match-xg-card__skeleton--teams"></div></div>\
                <div class="match-xg-card__skeleton-content">\
                    <div class="match-xg-card__skeleton match-xg-card__skeleton--counter"></div>\
                    <div class="match-xg-card__skeleton match-xg-card__skeleton--bar"></div>\
                    <div class="match-xg-card__skeleton match-xg-card__skeleton--bar"></div>\
                </div>\
            </div>';
    }

    function renderPredictionError(container, message) {
        if (!container) return;
        container.innerHTML = '\
            <div class="match-xg-card match-xg-card--error">\
                <div class="match-xg-card__error">\
                    <span class="match-xg-card__error-icon">⚠️</span>\
                    <h4 class="match-xg-card__error-title">xG Prediction Unavailable</h4>\
                    <p class="match-xg-card__error-message">' + escapeHtml(message || 'Failed to load xG prediction') + '</p>\
                </div>\
            </div>';
    }

    // ══════════════════════════════════════════════════════════════════════
    // FETCH + RENDER
    // ══════════════════════════════════════════════════════════════════════

    async function fetchAndRender(container, teamName, isHome) {
        if (!container || !teamName) {
            console.error('[ExpectedGoalsCard] Missing container or team name');
            return null;
        }

        renderLoading(container);

        try {
            var params = '';
            if (isHome != null) params = '?isHome=' + isHome;

            var url = window.location.origin + '/api/teams/' + encodeURIComponent(teamName) + '/expected-goals' + params;
            var response = await fetch(url);

            if (!response.ok) {
                throw new Error('HTTP ' + response.status + ': ' + response.statusText);
            }

            var data = await response.json();
            renderExpectedGoalsCard(container, data);
            return data;
        } catch (error) {
            console.error('[ExpectedGoalsCard] Failed to fetch:', error);
            renderError(container, error.message);
            return null;
        }
    }

    async function fetchAndRenderPrediction(container, homeTeam, awayTeam) {
        if (!container || !homeTeam || !awayTeam) {
            console.error('[ExpectedGoalsCard] Missing container or team names');
            return null;
        }

        renderPredictionLoading(container);

        try {
            var url = window.location.origin + '/api/matches/predict-xg?home=' + encodeURIComponent(homeTeam) + '&away=' + encodeURIComponent(awayTeam);
            var response = await fetch(url);

            if (!response.ok) {
                var errorData = {};
                try { errorData = await response.json(); } catch(e) {}
                throw new Error(errorData.message || 'HTTP ' + response.status + ': ' + response.statusText);
            }

            var data = await response.json();
            renderMatchXGPrediction(container, data);
            return data;
        } catch (error) {
            console.error('[ExpectedGoalsCard] Failed to fetch prediction:', error);
            renderPredictionError(container, error.message);
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════════════════

    window.ExpectedGoalsCard = {
        render: renderExpectedGoalsCard,
        renderLoading: renderLoading,
        renderError: renderError,
        fetchAndRender: fetchAndRender,
        renderPrediction: renderMatchXGPrediction,
        renderPredictionLoading: renderPredictionLoading,
        renderPredictionError: renderPredictionError,
        fetchAndRenderPrediction: fetchAndRenderPrediction,
        LEAGUE_AVERAGE_XG: LEAGUE_AVERAGE_XG
    };

})();


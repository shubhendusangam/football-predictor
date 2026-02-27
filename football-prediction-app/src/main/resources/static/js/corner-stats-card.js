/**
 * Corner Statistics Card Component
 * ==================================
 *
 * Renders corner kick statistics cards with:
 * - Average corners won/conceded display
 * - Horizontal bar charts using styled div elements
 * - Corner dominance as percentage with color coding
 * - Success rate indicator
 *
 * Usage:
 *   window.CornerStatsCard.render(container, data)
 *   window.CornerStatsCard.fetchAndRender(container, teamName, isHome)
 *   window.CornerStatsCard.renderPrediction(container, predictionData)
 *   window.CornerStatsCard.fetchAndRenderPrediction(container, homeTeam, awayTeam)
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
     * League average corners per team per match (approx 5.5 in Premier League)
     */
    var LEAGUE_AVERAGE_CORNERS = 5.5;

    /**
     * Maximum corners for bar chart scaling
     */
    var MAX_CORNERS_FOR_SCALE = 10;

    /**
     * Corner dominance thresholds for color coding
     */
    var DOMINANCE_THRESHOLDS = {
        STRONG: 0.55,    // > 55% = Green
        WEAK: 0.45       // < 45% = Red, else Yellow
    };

    /**
     * Probability thresholds for color coding
     */
    var PROBABILITY_THRESHOLDS = {
        HIGH: 0.6,
        MEDIUM: 0.4
    };

    // ══════════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(str) {
        if (str == null) return '';
        var div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    /**
     * Format number to specified decimal places
     */
    function formatNumber(value, decimals) {
        decimals = decimals !== undefined ? decimals : 2;
        if (value == null || isNaN(value)) return '0.00';
        return Number(value).toFixed(decimals);
    }

    /**
     * Format percentage value
     */
    function formatPercentage(value) {
        if (value == null || isNaN(value)) return '0%';
        return Math.round(value * 100) + '%';
    }

    /**
     * Get color class based on corner dominance value
     */
    function getDominanceColorClass(dominance) {
        if (dominance == null || isNaN(dominance)) return 'average';
        if (dominance > DOMINANCE_THRESHOLDS.STRONG) return 'strong';
        if (dominance < DOMINANCE_THRESHOLDS.WEAK) return 'weak';
        return 'average';
    }

    /**
     * Get color for dominance
     */
    function getDominanceColor(dominance) {
        if (dominance == null || isNaN(dominance)) return '#fbbf24';
        if (dominance > DOMINANCE_THRESHOLDS.STRONG) return '#22c55e';
        if (dominance < DOMINANCE_THRESHOLDS.WEAK) return '#ef4444';
        return '#fbbf24';
    }

    /**
     * Get probability color
     */
    function getProbabilityColor(prob) {
        if (prob == null || isNaN(prob)) return '#ef4444';
        if (prob >= PROBABILITY_THRESHOLDS.HIGH) return '#22c55e';
        if (prob >= PROBABILITY_THRESHOLDS.MEDIUM) return '#fbbf24';
        return '#ef4444';
    }

    /**
     * Calculate bar width percentage for visualization
     */
    function calculateBarWidth(value, max) {
        max = max || MAX_CORNERS_FOR_SCALE;
        if (value == null || isNaN(value) || max <= 0) return 0;
        return Math.min(100, Math.max(0, (value / max) * 100));
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER CORNER STATS CARD
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render a corner statistics card
     * @param {HTMLElement} container - Container element
     * @param {Object} data - Corner stats data
     */
    function render(container, data) {
        if (!container) {
            console.error('[CornerStatsCard] Container not provided');
            return;
        }

        // Defensive: handle missing data
        var safeData = {
            teamName: data && data.teamName || 'Unknown Team',
            isHome: data && data.isHome,
            avgCornersWon: Number(data && data.avgCornersWon) || 0,
            avgCornersAgainst: Number(data && data.avgCornersAgainst) || 0,
            cornerDominance: Number(data && data.cornerDominance) || 0,
            successRate: Number(data && data.successRate) || 0,
            matchesAnalyzed: Number(data && data.matchesAnalyzed) || 0,
            totalCornersWon: Number(data && data.totalCornersWon) || 0,
            totalCornersAgainst: Number(data && data.totalCornersAgainst) || 0,
            weightedAvgCorners: Number(data && data.weightedAvgCorners) || 0
        };

        var dominanceColor = getDominanceColor(safeData.cornerDominance);
        var venueLabel = safeData.isHome === true ? 'HOME' : safeData.isHome === false ? 'AWAY' : 'ALL';
        var venueBgColor = safeData.isHome === true ? 'rgba(34, 197, 94, 0.15)' :
                           safeData.isHome === false ? 'rgba(59, 130, 246, 0.15)' : 'rgba(168, 85, 247, 0.15)';
        var venueTextColor = safeData.isHome === true ? '#22c55e' :
                             safeData.isHome === false ? '#3b82f6' : '#a855f7';

        var diff = safeData.avgCornersWon - LEAGUE_AVERAGE_CORNERS;
        var diffText = diff >= 0 ? '+' + formatNumber(diff, 1) : formatNumber(diff, 1);
        var diffColor = diff > 0 ? '#22c55e' : diff < 0 ? '#ef4444' : '#94a3b8';

        var confidenceLevel = safeData.matchesAnalyzed >= 15 ? 'High' :
                              safeData.matchesAnalyzed >= 8 ? 'Medium' : 'Low';
        var confidenceColor = safeData.matchesAnalyzed >= 15 ? '#22c55e' :
                              safeData.matchesAnalyzed >= 8 ? '#fbbf24' : '#ef4444';

        container.innerHTML = '\
            <div style="background: var(--bg-secondary, #1e293b); border: 1px solid var(--border-color, #334155); border-radius: 0.75rem; padding: 1.5rem;">\
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">\
                    <h3 style="font-size: 1.125rem; font-weight: 600; color: var(--text-primary, #f1f5f9); margin: 0;">⚑ ' + escapeHtml(safeData.teamName) + '</h3>\
                    <span style="padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; background: ' + venueBgColor + '; color: ' + venueTextColor + ';">' + venueLabel + '</span>\
                </div>\
                \
                <div style="margin-bottom: 1rem;">\
                    <div style="font-size: 0.875rem; font-weight: 600; color: var(--text-secondary, #cbd5e1); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.75rem;">Corner Averages</div>\
                    \
                    <div style="margin-bottom: 0.75rem;">\
                        <div style="display: flex; justify-content: space-between; margin-bottom: 0.25rem;">\
                            <span style="font-size: 0.8rem; color: var(--text-muted, #94a3b8);">Corners Won</span>\
                            <span style="font-size: 0.9rem; font-weight: 700; color: var(--text-primary, #f1f5f9);">' + formatNumber(safeData.avgCornersWon) + '</span>\
                        </div>\
                        <div style="height: 8px; background: var(--bg-tertiary, #334155); border-radius: 4px; overflow: hidden;">\
                            <div style="height: 100%; width: ' + calculateBarWidth(safeData.avgCornersWon) + '%; background: linear-gradient(90deg, #22c55e, #10b981); border-radius: 4px; transition: width 0.8s ease-out;"></div>\
                        </div>\
                    </div>\
                    \
                    <div style="margin-bottom: 0.75rem;">\
                        <div style="display: flex; justify-content: space-between; margin-bottom: 0.25rem;">\
                            <span style="font-size: 0.8rem; color: var(--text-muted, #94a3b8);">Corners Against</span>\
                            <span style="font-size: 0.9rem; font-weight: 700; color: var(--text-primary, #f1f5f9);">' + formatNumber(safeData.avgCornersAgainst) + '</span>\
                        </div>\
                        <div style="height: 8px; background: var(--bg-tertiary, #334155); border-radius: 4px; overflow: hidden;">\
                            <div style="height: 100%; width: ' + calculateBarWidth(safeData.avgCornersAgainst) + '%; background: linear-gradient(90deg, #ef4444, #f97316); border-radius: 4px; transition: width 0.8s ease-out;"></div>\
                        </div>\
                    </div>\
                    \
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0.75rem; background: var(--bg-tertiary, #334155); border-radius: 0.375rem;">\
                        <span style="font-size: 0.75rem; color: var(--text-muted, #94a3b8);">vs League Avg (' + LEAGUE_AVERAGE_CORNERS + '):</span>\
                        <span style="font-size: 0.875rem; font-weight: 700; color: ' + diffColor + ';">' + diffText + '</span>\
                    </div>\
                </div>\
                \
                <div style="padding: 1rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem; margin-bottom: 1rem;">\
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">\
                        <span style="font-size: 0.875rem; font-weight: 600; color: var(--text-secondary, #cbd5e1);">Corner Dominance</span>\
                        <span style="font-size: 1.5rem; font-weight: 700; color: ' + dominanceColor + ';">' + formatPercentage(safeData.cornerDominance) + '</span>\
                    </div>\
                    <div style="position: relative; height: 12px; background: linear-gradient(90deg, #ef4444 0%, #fbbf24 50%, #22c55e 100%); border-radius: 6px; margin-bottom: 0.5rem;">\
                        <div style="position: absolute; top: -4px; left: 50%; transform: translateX(-50%); width: 2px; height: 20px; background: var(--text-primary, #f1f5f9);"></div>\
                        <div style="position: absolute; top: 0; left: 0; height: 100%; width: ' + (safeData.cornerDominance * 100) + '%; background: rgba(255,255,255,0.3); border-radius: 6px;"></div>\
                    </div>\
                    <div style="display: flex; justify-content: space-between; font-size: 0.7rem; color: var(--text-muted, #94a3b8);">\
                        <span>Concede More</span>\
                        <span>Win More</span>\
                    </div>\
                </div>\
                \
                <div style="padding: 0.75rem 1rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem; margin-bottom: 1rem;">\
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.25rem;">\
                        <span style="font-size: 0.8rem; font-weight: 600; color: var(--text-secondary, #cbd5e1);">Win Rate with Corner Dominance</span>\
                        <span style="font-size: 1.125rem; font-weight: 700; color: #3b82f6;">' + formatPercentage(safeData.successRate) + '</span>\
                    </div>\
                    <div style="font-size: 0.7rem; color: var(--text-muted, #94a3b8);">Percentage of matches won when having more corners</div>\
                </div>\
                \
                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.75rem; margin-bottom: 0.75rem;">\
                    <div style="display: flex; flex-direction: column; align-items: center; padding: 0.75rem 0.5rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem;">\
                        <span style="font-size: 1.25rem;">⚑</span>\
                        <span style="font-size: 1rem; font-weight: 700; color: var(--text-primary, #f1f5f9);">' + safeData.totalCornersWon + '</span>\
                        <span style="font-size: 0.65rem; color: var(--text-muted, #94a3b8); text-transform: uppercase;">Total Won</span>\
                    </div>\
                    <div style="display: flex; flex-direction: column; align-items: center; padding: 0.75rem 0.5rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem;">\
                        <span style="font-size: 1.25rem;">⚐</span>\
                        <span style="font-size: 1rem; font-weight: 700; color: var(--text-primary, #f1f5f9);">' + safeData.totalCornersAgainst + '</span>\
                        <span style="font-size: 0.65rem; color: var(--text-muted, #94a3b8); text-transform: uppercase;">Total Against</span>\
                    </div>\
                    <div style="display: flex; flex-direction: column; align-items: center; padding: 0.75rem 0.5rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem;">\
                        <span style="font-size: 1.25rem;">📊</span>\
                        <span style="font-size: 1rem; font-weight: 700; color: var(--text-primary, #f1f5f9);">' + safeData.matchesAnalyzed + '</span>\
                        <span style="font-size: 0.65rem; color: var(--text-muted, #94a3b8); text-transform: uppercase;">Matches</span>\
                    </div>\
                    <div style="display: flex; flex-direction: column; align-items: center; padding: 0.75rem 0.5rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem;">\
                        <span style="font-size: 1.25rem;">📈</span>\
                        <span style="font-size: 1rem; font-weight: 700; color: var(--text-primary, #f1f5f9);">' + formatNumber(safeData.weightedAvgCorners) + '</span>\
                        <span style="font-size: 0.65rem; color: var(--text-muted, #94a3b8); text-transform: uppercase;">Weighted Avg</span>\
                    </div>\
                </div>\
                \
                <div style="display: flex; justify-content: center; padding-top: 0.75rem; border-top: 1px solid var(--border-color, #334155);">\
                    <span style="font-size: 0.75rem; padding: 0.25rem 0.75rem; border-radius: 9999px; background: rgba(' + (confidenceColor === '#22c55e' ? '34, 197, 94' : confidenceColor === '#fbbf24' ? '251, 191, 36' : '239, 68, 68') + ', 0.15); color: ' + confidenceColor + ';">' + confidenceLevel + ' confidence (' + safeData.matchesAnalyzed + ' matches)</span>\
                </div>\
            </div>';
    }

    /**
     * Render loading state
     */
    function renderLoading(container) {
        if (!container) return;
        container.innerHTML = '\
            <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 280px; background: var(--bg-secondary, #1e293b); border-radius: 0.75rem; border: 1px solid var(--border-color, #334155);">\
                <div style="width: 40px; height: 40px; border: 3px solid var(--bg-tertiary, #334155); border-top-color: #fbbf24; border-radius: 50%; animation: spin 1s linear infinite;"></div>\
                <span style="margin-top: 1rem; font-size: 0.875rem; color: var(--text-muted, #94a3b8);">Loading corner stats...</span>\
            </div>';
    }

    /**
     * Render error state
     */
    function renderError(container, message) {
        if (!container) return;
        container.innerHTML = '\
            <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 280px; background: var(--bg-secondary, #1e293b); border-radius: 0.75rem; border: 1px solid var(--border-color, #334155); text-align: center; padding: 1rem;">\
                <span style="font-size: 2.5rem; margin-bottom: 1rem;">⚠️</span>\
                <span style="font-size: 0.875rem; color: var(--text-muted, #94a3b8);">' + escapeHtml(message || 'Failed to load corner statistics') + '</span>\
            </div>';
    }

    /**
     * Fetch corner stats and render
     */
    function fetchAndRender(container, teamName, isHome) {
        if (!container || !teamName) {
            console.error('[CornerStatsCard] Missing container or team name');
            return Promise.resolve(null);
        }

        renderLoading(container);

        var url = '/api/teams/' + encodeURIComponent(teamName) + '/corner-stats';
        if (isHome !== null && isHome !== undefined) {
            url += '?isHome=' + isHome;
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
                console.error('[CornerStatsCard] Fetch error:', error);
                renderError(container, error.message);
                return null;
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER CORNER PREDICTION CARD
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render corner prediction card
     */
    function renderPrediction(container, data) {
        if (!container) {
            console.error('[CornerStatsCard] Container not provided');
            return;
        }

        var safeData = {
            homeTeam: data && data.homeTeam || 'Home Team',
            awayTeam: data && data.awayTeam || 'Away Team',
            expectedTotalCorners: Number(data && data.expectedTotalCorners) || 0,
            expectedHomeCorners: Number(data && data.expectedHomeCorners) || 0,
            expectedAwayCorners: Number(data && data.expectedAwayCorners) || 0,
            probOver9_5: Number(data && data.probOver9_5) || 0,
            probOver10_5: Number(data && data.probOver10_5) || 0,
            probOver11_5: Number(data && data.probOver11_5) || 0,
            confidence: Number(data && data.confidence) || 0,
            homeMatchesAnalyzed: Number(data && data.homeMatchesAnalyzed) || 0,
            awayMatchesAnalyzed: Number(data && data.awayMatchesAnalyzed) || 0
        };

        var totalCorners = safeData.expectedHomeCorners + safeData.expectedAwayCorners;
        var homePercent = totalCorners > 0 ? (safeData.expectedHomeCorners / totalCorners) * 100 : 50;
        var awayPercent = 100 - homePercent;

        var confidenceLabel = safeData.confidence >= 0.7 ? 'High' : safeData.confidence >= 0.5 ? 'Medium' : 'Low';
        var confidenceColor = safeData.confidence >= 0.7 ? '#22c55e' : safeData.confidence >= 0.5 ? '#fbbf24' : '#ef4444';

        container.innerHTML = '\
            <div style="background: var(--bg-secondary, #1e293b); border: 1px solid var(--border-color, #334155); border-radius: 0.75rem; padding: 1.5rem;">\
                <div style="text-align: center; margin-bottom: 1.5rem;">\
                    <div style="display: flex; align-items: center; justify-content: center; gap: 0.75rem; flex-wrap: wrap; margin-bottom: 0.75rem;">\
                        <span style="font-size: 1.125rem; font-weight: 700; color: #22c55e;">' + escapeHtml(safeData.homeTeam) + '</span>\
                        <span style="font-size: 0.875rem; font-weight: 600; color: var(--text-muted, #94a3b8);">VS</span>\
                        <span style="font-size: 1.125rem; font-weight: 700; color: #3b82f6;">' + escapeHtml(safeData.awayTeam) + '</span>\
                    </div>\
                    <span style="display: inline-flex; align-items: center; gap: 0.375rem; padding: 0.25rem 0.75rem; background: rgba(168, 85, 247, 0.15); border-radius: 9999px; font-size: 0.7rem; font-weight: 600; color: #a855f7; text-transform: uppercase;">⚑ Corner Prediction</span>\
                </div>\
                \
                <div style="text-align: center; padding: 1.5rem; background: linear-gradient(135deg, rgba(168, 85, 247, 0.1) 0%, rgba(59, 130, 246, 0.1) 100%); border-radius: 0.75rem; border: 1px solid rgba(168, 85, 247, 0.2); margin-bottom: 1.5rem;">\
                    <div style="font-size: 0.8rem; font-weight: 600; color: var(--text-secondary, #cbd5e1); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">Expected Total Corners</div>\
                    <div style="font-size: 3.5rem; font-weight: 800; line-height: 1; background: linear-gradient(135deg, #a855f7 0%, #3b82f6 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;">' + formatNumber(safeData.expectedTotalCorners, 1) + '</div>\
                    <div style="font-size: 0.7rem; color: var(--text-muted, #94a3b8); margin-top: 0.5rem;">Combined prediction for both teams</div>\
                </div>\
                \
                <div style="padding: 1rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem; margin-bottom: 1.5rem;">\
                    <div style="font-size: 0.8rem; font-weight: 600; color: var(--text-secondary, #cbd5e1); text-transform: uppercase; margin-bottom: 0.75rem;">Expected Distribution</div>\
                    <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">\
                        <div>\
                            <span style="font-size: 0.75rem; color: var(--text-muted, #94a3b8);">' + escapeHtml(safeData.homeTeam) + '</span><br>\
                            <span style="font-size: 1.25rem; font-weight: 700; color: #22c55e;">' + formatNumber(safeData.expectedHomeCorners, 1) + '</span>\
                        </div>\
                        <div style="text-align: right;">\
                            <span style="font-size: 0.75rem; color: var(--text-muted, #94a3b8);">' + escapeHtml(safeData.awayTeam) + '</span><br>\
                            <span style="font-size: 1.25rem; font-weight: 700; color: #3b82f6;">' + formatNumber(safeData.expectedAwayCorners, 1) + '</span>\
                        </div>\
                    </div>\
                    <div style="display: flex; height: 12px; border-radius: 6px; overflow: hidden; background: var(--bg-secondary, #1e293b);">\
                        <div style="height: 100%; width: ' + homePercent + '%; background: linear-gradient(90deg, #22c55e, #10b981);"></div>\
                        <div style="height: 100%; width: ' + awayPercent + '%; background: linear-gradient(90deg, #3b82f6, #60a5fa);"></div>\
                    </div>\
                    <div style="display: flex; justify-content: space-between; margin-top: 0.25rem; font-size: 0.7rem; font-weight: 600; color: var(--text-muted, #94a3b8);">\
                        <span>' + Math.round(homePercent) + '%</span>\
                        <span>' + Math.round(awayPercent) + '%</span>\
                    </div>\
                </div>\
                \
                <div style="margin-bottom: 1.5rem;">\
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">\
                        <span style="font-size: 0.875rem; font-weight: 600; color: var(--text-secondary, #cbd5e1); text-transform: uppercase;">Over/Under Probabilities</span>\
                        <span style="font-size: 1.125rem;">📊</span>\
                    </div>\
                    \
                    ' + renderProbabilityBar('Over 9.5', safeData.probOver9_5) + '\
                    ' + renderProbabilityBar('Over 10.5', safeData.probOver10_5) + '\
                    ' + renderProbabilityBar('Over 11.5', safeData.probOver11_5) + '\
                </div>\
                \
                <div style="padding: 1rem; background: var(--bg-tertiary, #334155); border-radius: 0.5rem; margin-bottom: 0.75rem;">\
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">\
                        <span style="font-size: 0.8rem; font-weight: 600; color: var(--text-secondary, #cbd5e1);">Model Confidence</span>\
                        <span style="padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; background: rgba(' + (confidenceColor === '#22c55e' ? '34, 197, 94' : confidenceColor === '#fbbf24' ? '251, 191, 36' : '239, 68, 68') + ', 0.15); color: ' + confidenceColor + ';">' + confidenceLabel + ' (' + formatPercentage(safeData.confidence) + ')</span>\
                    </div>\
                    <div style="font-size: 0.7rem; color: var(--text-muted, #94a3b8);">\
                        📈 ' + safeData.homeMatchesAnalyzed + ' home matches analyzed<br>\
                        📈 ' + safeData.awayMatchesAnalyzed + ' away matches analyzed\
                    </div>\
                </div>\
                \
                <div style="padding-top: 0.75rem; border-top: 1px solid var(--border-color, #334155); text-align: center;">\
                    <span style="font-size: 0.7rem; color: var(--text-muted, #94a3b8); font-style: italic;">Based on weighted historical averages with recency factor</span>\
                </div>\
            </div>';
    }

    /**
     * Helper to render probability bar HTML
     */
    function renderProbabilityBar(label, probability) {
        var probColor = getProbabilityColor(probability);
        return '\
            <div style="margin-bottom: 0.75rem;">\
                <div style="display: flex; justify-content: space-between; margin-bottom: 0.25rem;">\
                    <span style="font-size: 0.8rem; color: var(--text-muted, #94a3b8);">' + label + '</span>\
                    <span style="font-size: 0.9rem; font-weight: 700; color: ' + probColor + ';">' + formatPercentage(probability) + '</span>\
                </div>\
                <div style="height: 8px; background: var(--bg-tertiary, #334155); border-radius: 4px; overflow: hidden;">\
                    <div style="height: 100%; width: ' + (probability * 100) + '%; background: ' + probColor + '; border-radius: 4px; transition: width 0.8s ease-out;"></div>\
                </div>\
            </div>';
    }

    /**
     * Render prediction loading state
     */
    function renderPredictionLoading(container) {
        if (!container) return;
        container.innerHTML = '\
            <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 400px; background: var(--bg-secondary, #1e293b); border-radius: 0.75rem; border: 1px solid var(--border-color, #334155);">\
                <div style="width: 40px; height: 40px; border: 3px solid var(--bg-tertiary, #334155); border-top-color: #a855f7; border-radius: 50%; animation: spin 1s linear infinite;"></div>\
                <span style="margin-top: 1rem; font-size: 0.875rem; color: var(--text-muted, #94a3b8);">Loading corner prediction...</span>\
            </div>';
    }

    /**
     * Fetch prediction and render
     */
    function fetchAndRenderPrediction(container, homeTeam, awayTeam) {
        if (!container || !homeTeam || !awayTeam) {
            console.error('[CornerStatsCard] Missing container or team names');
            return Promise.resolve(null);
        }

        renderPredictionLoading(container);

        var url = '/api/matches/predict-corners?home=' + encodeURIComponent(homeTeam) + '&away=' + encodeURIComponent(awayTeam);

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
                renderPrediction(container, data);
                return data;
            })
            .catch(function(error) {
                console.error('[CornerStatsCard] Prediction fetch error:', error);
                renderError(container, error.message);
                return null;
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT TO GLOBAL SCOPE
    // ══════════════════════════════════════════════════════════════════════

    window.CornerStatsCard = {
        render: render,
        renderLoading: renderLoading,
        renderError: renderError,
        fetchAndRender: fetchAndRender,
        renderPrediction: renderPrediction,
        renderPredictionLoading: renderPredictionLoading,
        fetchAndRenderPrediction: fetchAndRenderPrediction,
        LEAGUE_AVERAGE_CORNERS: LEAGUE_AVERAGE_CORNERS,
        DOMINANCE_THRESHOLDS: DOMINANCE_THRESHOLDS
    };

    console.log('[CornerStatsCard] Module initialized');

})();


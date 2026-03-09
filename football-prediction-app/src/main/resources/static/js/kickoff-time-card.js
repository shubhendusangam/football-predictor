/**
 * Kickoff Time Analysis Card Component
 * ======================================
 *
 * Renders a kick-off time performance card with:
 * - Bar chart showing win% by time slot
 * - Horizontal baseline for team's overall win%
 * - Highlight best/worst time slots
 * - Clock icons for each time slot
 * - Color-coded bars: Green (above avg), Red (below avg)
 * - Sample size below each bar
 *
 * Usage:
 *   window.KickoffTimeCard.render(container, data)
 *   window.KickoffTimeCard.fetchAndRender(container, teamName)
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
     * Clock icons for each time slot
     */
    var SLOT_ICONS = {
        early: '🕐',
        afternoon: '🕑',
        late: '🕔',
        evening: '🕖'
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
        if (decimals === undefined) decimals = 1;
        if (value == null || isNaN(value)) return '0';
        return Number(value).toFixed(decimals);
    }

    /**
     * Get performance CSS class
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
    // RENDER FUNCTIONS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render the kick-off time card
     */
    function render(container, data) {
        if (!container) {
            console.error('[KickoffTimeCard] Container element not provided');
            return;
        }

        var safeData = {
            teamName: (data && data.teamName) || 'Unknown Team',
            dataScope: (data && data.dataScope) || 'Recent Matches',
            matchesAnalyzed: Number((data && data.matchesAnalyzed) || 0),
            matchesWithTimeData: Number((data && data.matchesWithTimeData) || 0),
            timeSlots: (data && Array.isArray(data.timeSlots)) ? data.timeSlots : [],
            bestTime: (data && data.bestTime) || 'N/A',
            worstTime: (data && data.worstTime) || 'N/A',
            overallWinRate: Number((data && data.overallWinRate) || 0),
            overallAvgGoalsScored: Number((data && data.overallAvgGoalsScored) || 0),
            confidence: (data && data.confidence) || 'Low'
        };

        var confidenceClass = safeData.confidence.toLowerCase();

        // Build slot rows HTML
        var slotsHtml = '';
        for (var i = 0; i < safeData.timeSlots.length; i++) {
            var slot = safeData.timeSlots[i];
            var perfClass = getPerformanceClass(slot.performance);
            var icon = SLOT_ICONS[slot.slotKey] || '🕐';
            var isBest = slot.timeSlot === safeData.bestTime;
            var isWorst = slot.timeSlot === safeData.worstTime;
            var labelSuffix = isBest ? ' ⭐' : (isWorst ? ' ⚠️' : '');

            var winPctText = slot.matchesPlayed > 0 ? formatNumber(slot.winPercentage) + '%' : 'No data';
            var barWidth = slot.matchesPlayed > 0 ? Math.min(100, Math.max(2, slot.winPercentage)) : 0;

            var detailsHtml = '';
            if (slot.matchesPlayed > 0) {
                detailsHtml = '\
                    <span class="kickoff-time-card__slot-detail kickoff-time-card__slot-detail--wins">W: ' + slot.wins + '</span>\
                    <span class="kickoff-time-card__slot-detail kickoff-time-card__slot-detail--draws">D: ' + slot.draws + '</span>\
                    <span class="kickoff-time-card__slot-detail kickoff-time-card__slot-detail--losses">L: ' + slot.losses + '</span>\
                    <span class="kickoff-time-card__slot-detail">📊 ' + slot.matchesPlayed + ' matches</span>\
                    <span class="kickoff-time-card__slot-detail">⚽ ' + formatNumber(slot.avgGoalsScored) + ' avg</span>';
            } else {
                detailsHtml = '<span class="kickoff-time-card__slot-detail">No matches in this slot</span>';
            }

            var baselineHtml = safeData.overallWinRate > 0
                ? '<div class="kickoff-time-card__baseline" style="left: ' + Math.min(100, safeData.overallWinRate) + '%" title="Overall win rate: ' + formatNumber(safeData.overallWinRate) + '%"></div>'
                : '';

            slotsHtml += '\
                <div class="kickoff-time-card__slot">\
                    <div class="kickoff-time-card__slot-header">\
                        <span class="kickoff-time-card__slot-label">\
                            <span class="kickoff-time-card__slot-icon">' + icon + '</span>\
                            ' + escapeHtml(slot.timeSlot || '') + labelSuffix + '\
                        </span>\
                        <span class="kickoff-time-card__slot-win-pct kickoff-time-card__slot-win-pct--' + perfClass + '">\
                            ' + winPctText + '\
                        </span>\
                    </div>\
                    <div class="kickoff-time-card__bar-container">\
                        <div class="kickoff-time-card__bar kickoff-time-card__bar--' + perfClass + '" data-width="' + barWidth + '" style="width: 0%;"></div>\
                        ' + baselineHtml + '\
                    </div>\
                    <div class="kickoff-time-card__slot-details">\
                        ' + detailsHtml + '\
                    </div>\
                </div>';
        }

        container.innerHTML = '\
            <div class="kickoff-time-card">\
                <div class="kickoff-time-card__header">\
                    <h3 class="kickoff-time-card__title">🕐 ' + escapeHtml(safeData.teamName) + ' Kick-off Time Analysis</h3>\
                    <span class="kickoff-time-card__badge">' + safeData.matchesWithTimeData + ' MATCHES</span>\
                </div>\
                \
                <div class="kickoff-time-card__summary">\
                    <div class="kickoff-time-card__summary-item">\
                        <span class="kickoff-time-card__summary-label">Best Time</span>\
                        <span class="kickoff-time-card__summary-value kickoff-time-card__summary-value--best">' + escapeHtml(safeData.bestTime) + '</span>\
                    </div>\
                    <div class="kickoff-time-card__summary-item">\
                        <span class="kickoff-time-card__summary-label">Worst Time</span>\
                        <span class="kickoff-time-card__summary-value kickoff-time-card__summary-value--worst">' + escapeHtml(safeData.worstTime) + '</span>\
                    </div>\
                    <div class="kickoff-time-card__summary-item">\
                        <span class="kickoff-time-card__summary-label">Overall Win Rate</span>\
                        <span class="kickoff-time-card__summary-value">' + formatNumber(safeData.overallWinRate) + '%</span>\
                    </div>\
                    <div class="kickoff-time-card__summary-item">\
                        <span class="kickoff-time-card__summary-label">Avg Goals/Match</span>\
                        <span class="kickoff-time-card__summary-value">' + formatNumber(safeData.overallAvgGoalsScored) + '</span>\
                    </div>\
                </div>\
                \
                <div class="kickoff-time-card__chart">\
                    <h4 class="kickoff-time-card__chart-title">Win Rate by Kick-off Time</h4>\
                    ' + slotsHtml + '\
                    <div class="kickoff-time-card__legend">\
                        <span class="kickoff-time-card__legend-line"></span>\
                        <span>Overall win rate (' + formatNumber(safeData.overallWinRate) + '%)</span>\
                    </div>\
                </div>\
                \
                <div class="kickoff-time-card__footer">\
                    <span class="kickoff-time-card__confidence kickoff-time-card__confidence--' + confidenceClass + '">\
                        ' + escapeHtml(safeData.confidence) + ' confidence\
                    </span>\
                    <span class="kickoff-time-card__data-scope">\
                        ' + escapeHtml(safeData.dataScope) + ' · ' + safeData.matchesWithTimeData + ' with time data\
                    </span>\
                </div>\
            </div>';

        // Animate bars after render
        requestAnimationFrame(function() {
            setTimeout(function() {
                var bars = container.querySelectorAll('.kickoff-time-card__bar');
                for (var j = 0; j < bars.length; j++) {
                    var targetWidth = bars[j].getAttribute('data-width');
                    if (targetWidth) {
                        bars[j].style.width = targetWidth + '%';
                    }
                }
            }, 100);
        });
    }

    /**
     * Render loading state
     */
    function renderLoading(container) {
        if (!container) return;

        container.innerHTML = '\
            <div class="kickoff-time-card kickoff-time-card--loading">\
                <div class="kickoff-time-card__header">\
                    <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--title"></div>\
                </div>\
                <div class="kickoff-time-card__skeleton-content">\
                    <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.75rem;">\
                        <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--summary"></div>\
                        <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--summary"></div>\
                    </div>\
                    <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--bar"></div>\
                    <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--bar"></div>\
                    <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--bar"></div>\
                    <div class="kickoff-time-card__skeleton kickoff-time-card__skeleton--bar"></div>\
                </div>\
            </div>';
    }

    /**
     * Render error state
     */
    function renderError(container, message) {
        if (!container) return;

        container.innerHTML = '\
            <div class="kickoff-time-card kickoff-time-card--error">\
                <div class="kickoff-time-card__error">\
                    <span class="kickoff-time-card__error-icon">⚠️</span>\
                    <p class="kickoff-time-card__error-message">' + escapeHtml(message || 'Failed to load kick-off time analysis') + '</p>\
                </div>\
            </div>';
    }

    /**
     * Fetch kick-off time analysis and render card
     */
    function fetchAndRender(container, teamName) {
        if (!container || !teamName) {
            console.error('[KickoffTimeCard] Missing container or team name');
            return Promise.resolve(null);
        }

        renderLoading(container);

        var url = '/api/teams/' + encodeURIComponent(teamName) + '/kickoff-analysis';

        return fetch(url)
            .then(function(response) {
                if (!response.ok) {
                    if (response.status === 404) {
                        renderError(container, 'No kick-off time data available for this team');
                        return null;
                    }
                    throw new Error('HTTP ' + response.status + ': ' + response.statusText);
                }
                return response.json();
            })
            .then(function(data) {
                if (data) {
                    // Handle case where team exists but has no time data (pre-2019/20 teams)
                    if (data.dataAvailable === false) {
                        renderError(container, data.message || 'No kick-off time data available for this team');
                        return null;
                    }
                    render(container, data);
                }
                return data;
            })
            .catch(function(error) {
                console.error('[KickoffTimeCard] Failed to fetch kick-off time analysis:', error);
                renderError(container, error.message || 'Failed to load data');
                return null;
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT TO GLOBAL SCOPE
    // ══════════════════════════════════════════════════════════════════════

    window.KickoffTimeCard = {
        render: render,
        renderLoading: renderLoading,
        renderError: renderError,
        fetchAndRender: fetchAndRender
    };

    console.log('[KickoffTimeCard] Module initialized');

})();


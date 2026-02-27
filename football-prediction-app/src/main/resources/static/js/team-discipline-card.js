/**
 * Team Discipline Card Component
 * ===============================
 *
 * Renders a team discipline card with:
 * - Season total yellow/red cards
 * - Discipline rating badge
 * - Recent bookings list
 * - Card averages
 *
 * Usage:
 *   window.TeamDisciplineCard.render(container, data)
 *   window.TeamDisciplineCard.fetchAndRender(container, teamName)
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
     * Discipline rating colors.
     */
    var RATING_COLORS = {
        'Excellent': '#22c55e',
        'Average': '#fbbf24',
        'Aggressive': '#ef4444',
        'Unknown': '#94a3b8'
    };

    /**
     * Result badge colors.
     */
    var RESULT_COLORS = {
        'W': '#22c55e',
        'D': '#94a3b8',
        'L': '#ef4444'
    };

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
     * Format number.
     */
    function formatNumber(value, decimals) {
        decimals = decimals !== undefined ? decimals : 2;
        if (value == null || isNaN(value)) return '0.00';
        return Number(value).toFixed(decimals);
    }

    /**
     * Get rating color.
     */
    function getRatingColor(rating) {
        return RATING_COLORS[rating] || RATING_COLORS['Unknown'];
    }

    /**
     * Get result color.
     */
    function getResultColor(result) {
        return RESULT_COLORS[result] || RESULT_COLORS['D'];
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER TEAM DISCIPLINE CARD
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render a team discipline card.
     * @param {HTMLElement} container - Container element
     * @param {Object} data - Team discipline data
     */
    function render(container, data) {
        if (!container) {
            console.error('[TeamDisciplineCard] Container not provided');
            return;
        }

        // Defensive: handle missing data
        var safeData = {
            teamName: data && data.teamName || 'Unknown Team',
            avgYellowCardsHome: Number(data && data.avgYellowCardsHome) || 0,
            avgYellowCardsAway: Number(data && data.avgYellowCardsAway) || 0,
            avgYellowCardsOverall: Number(data && data.avgYellowCardsOverall) || 0,
            avgRedCards: Number(data && data.avgRedCards) || 0,
            totalYellowCardsSeason: Number(data && data.totalYellowCardsSeason) || 0,
            totalRedCardsSeason: Number(data && data.totalRedCardsSeason) || 0,
            matchesAnalyzed: Number(data && data.matchesAnalyzed) || 0,
            disciplineRating: data && data.disciplineRating || 'Unknown',
            ratingColor: data && data.ratingColor || 'gray',
            recentBookings: data && data.recentBookings || [],
            avgOpponentYellowCards: Number(data && data.avgOpponentYellowCards) || 0,
            cardDifferential: Number(data && data.cardDifferential) || 0
        };

        var ratingColor = getRatingColor(safeData.disciplineRating);
        var diffColor = safeData.cardDifferential > 0 ? '#ef4444' : safeData.cardDifferential < 0 ? '#22c55e' : '#94a3b8';
        var diffSign = safeData.cardDifferential > 0 ? '+' : '';

        // Build recent bookings HTML
        var bookingsHtml = '';
        if (safeData.recentBookings.length > 0) {
            var bookingItems = safeData.recentBookings.map(function(booking) {
                var resultColor = getResultColor(booking.result);
                var venueIcon = booking.isHome ? '🏠' : '✈️';
                var cardsDisplay = '';

                if (booking.yellowCards > 0) {
                    cardsDisplay += '<span style="margin-right: 0.25rem;">🟨 ' + booking.yellowCards + '</span>';
                }
                if (booking.redCards > 0) {
                    cardsDisplay += '<span>🟥 ' + booking.redCards + '</span>';
                }
                if (booking.yellowCards === 0 && booking.redCards === 0) {
                    cardsDisplay = '<span style="color: var(--text-muted, #94a3b8);">No cards</span>';
                }

                return '\
                    <div class="team-discipline-card__booking-item">\
                        <div class="team-discipline-card__booking-date">' + escapeHtml(booking.matchDate) + '</div>\
                        <div class="team-discipline-card__booking-opponent">\
                            <span>' + venueIcon + '</span>\
                            <span>' + escapeHtml(booking.opponent) + '</span>\
                        </div>\
                        <div class="team-discipline-card__booking-cards">' + cardsDisplay + '</div>\
                        <div class="team-discipline-card__booking-result" style="background: ' + resultColor + ';">' + escapeHtml(booking.result) + '</div>\
                    </div>';
            }).join('');

            bookingsHtml = '\
                <div class="team-discipline-card__bookings">\
                    <div class="team-discipline-card__section-title">Recent Bookings</div>\
                    <div class="team-discipline-card__bookings-list">\
                        ' + bookingItems + '\
                    </div>\
                </div>';
        }

        container.innerHTML = '\
            <div class="team-discipline-card">\
                <div class="team-discipline-card__header">\
                    <div class="team-discipline-card__title">\
                        <span class="team-discipline-card__icon">📋</span>\
                        <h3>' + escapeHtml(safeData.teamName) + '</h3>\
                    </div>\
                    <div class="team-discipline-card__rating" style="background: ' + ratingColor + '20; color: ' + ratingColor + '; border: 1px solid ' + ratingColor + '40;">\
                        ' + escapeHtml(safeData.disciplineRating) + '\
                    </div>\
                </div>\
                \
                <div class="team-discipline-card__totals">\
                    <div class="team-discipline-card__total-item">\
                        <div class="team-discipline-card__total-icon">🟨</div>\
                        <div class="team-discipline-card__total-value">' + safeData.totalYellowCardsSeason + '</div>\
                        <div class="team-discipline-card__total-label">Yellow Cards</div>\
                    </div>\
                    <div class="team-discipline-card__total-item">\
                        <div class="team-discipline-card__total-icon">🟥</div>\
                        <div class="team-discipline-card__total-value">' + safeData.totalRedCardsSeason + '</div>\
                        <div class="team-discipline-card__total-label">Red Cards</div>\
                    </div>\
                    <div class="team-discipline-card__total-item">\
                        <div class="team-discipline-card__total-icon">📊</div>\
                        <div class="team-discipline-card__total-value">' + safeData.matchesAnalyzed + '</div>\
                        <div class="team-discipline-card__total-label">Matches</div>\
                    </div>\
                </div>\
                \
                <div class="team-discipline-card__averages">\
                    <div class="team-discipline-card__section-title">Card Averages per Match</div>\
                    <div class="team-discipline-card__avg-grid">\
                        <div class="team-discipline-card__avg-item">\
                            <div class="team-discipline-card__avg-label">🏠 Home</div>\
                            <div class="team-discipline-card__avg-value">🟨 ' + formatNumber(safeData.avgYellowCardsHome) + '</div>\
                        </div>\
                        <div class="team-discipline-card__avg-item">\
                            <div class="team-discipline-card__avg-label">✈️ Away</div>\
                            <div class="team-discipline-card__avg-value">🟨 ' + formatNumber(safeData.avgYellowCardsAway) + '</div>\
                        </div>\
                        <div class="team-discipline-card__avg-item">\
                            <div class="team-discipline-card__avg-label">📈 Overall</div>\
                            <div class="team-discipline-card__avg-value">🟨 ' + formatNumber(safeData.avgYellowCardsOverall) + '</div>\
                        </div>\
                        <div class="team-discipline-card__avg-item">\
                            <div class="team-discipline-card__avg-label">🟥 Red</div>\
                            <div class="team-discipline-card__avg-value">' + formatNumber(safeData.avgRedCards, 3) + '</div>\
                        </div>\
                    </div>\
                </div>\
                \
                <div class="team-discipline-card__comparison">\
                    <div class="team-discipline-card__section-title">Card Differential</div>\
                    <div class="team-discipline-card__comparison-content">\
                        <div class="team-discipline-card__comparison-item">\
                            <span>Team Avg:</span>\
                            <span>🟨 ' + formatNumber(safeData.avgYellowCardsOverall) + '</span>\
                        </div>\
                        <div class="team-discipline-card__comparison-item">\
                            <span>Opponent Avg:</span>\
                            <span>🟨 ' + formatNumber(safeData.avgOpponentYellowCards) + '</span>\
                        </div>\
                        <div class="team-discipline-card__comparison-diff" style="color: ' + diffColor + ';">\
                            <span>Differential:</span>\
                            <span>' + diffSign + formatNumber(safeData.cardDifferential) + '</span>\
                        </div>\
                    </div>\
                </div>\
                \
                ' + bookingsHtml + '\
            </div>';
    }

    /**
     * Render loading state.
     */
    function renderLoading(container) {
        if (!container) return;
        container.innerHTML = '\
            <div class="team-discipline-card team-discipline-card--loading">\
                <div class="team-discipline-card__loading-spinner"></div>\
                <span class="team-discipline-card__loading-text">Loading discipline stats...</span>\
            </div>';
    }

    /**
     * Render error state.
     */
    function renderError(container, message) {
        if (!container) return;
        container.innerHTML = '\
            <div class="team-discipline-card team-discipline-card--error">\
                <span class="team-discipline-card__error-icon">⚠️</span>\
                <span class="team-discipline-card__error-text">' + escapeHtml(message || 'Failed to load discipline stats') + '</span>\
            </div>';
    }

    /**
     * Fetch team discipline and render.
     */
    function fetchAndRender(container, teamName) {
        if (!container || !teamName) {
            console.error('[TeamDisciplineCard] Missing container or team name');
            return Promise.resolve(null);
        }

        renderLoading(container);

        var url = '/api/teams/' + encodeURIComponent(teamName) + '/discipline';

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
                console.error('[TeamDisciplineCard] Fetch error:', error);
                renderError(container, error.message);
                return null;
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPORT TO GLOBAL SCOPE
    // ══════════════════════════════════════════════════════════════════════

    window.TeamDisciplineCard = {
        render: render,
        renderLoading: renderLoading,
        renderError: renderError,
        fetchAndRender: fetchAndRender,
        RATING_COLORS: RATING_COLORS
    };

    console.log('[TeamDisciplineCard] Module initialized');

})();


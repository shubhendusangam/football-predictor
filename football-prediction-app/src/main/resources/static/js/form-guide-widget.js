/**
 * Form Guide Widget
 * =================
 * Displays a team's recent form as circular W-D-L badges with
 * trend arrow, form rating bar, and hover tooltips.
 *
 * Usage:
 *   window.FormGuideWidget.render(container, teamName, numMatches)
 *
 * Dependencies:
 *   - api.js  (window.api.getFormGuide)
 *   - form-guide.css
 */
(function () {
    'use strict';

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    function escapeHtml(str) {
        if (str == null) return '';
        var div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    function trendArrow(trend) {
        switch (trend) {
            case 'Improving': return '↑';
            case 'Declining': return '↓';
            default:          return '→';
        }
    }

    function ratingClass(rating) {
        if (rating >= 8)  return 'excellent';
        if (rating >= 6)  return 'good';
        if (rating >= 4)  return 'average';
        if (rating >= 2)  return 'poor';
        return 'terrible';
    }

    function formatDate(dateStr) {
        if (!dateStr) return '';
        try {
            var d = new Date(dateStr + 'T00:00:00');
            return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
        } catch (e) {
            return dateStr;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDERING
    // ══════════════════════════════════════════════════════════════════════

    function renderBadge(match) {
        var r = match.result || 'L';
        var score = match.goalsFor + '-' + match.goalsAgainst;
        var venue = match.venue === 'H' ? 'Home' : 'Away';

        return '<div class="form-guide-badge form-guide-badge--' + r + '">' +
                   r +
                   '<div class="form-guide-tooltip">' +
                       '<div class="form-guide-tooltip__date">' + formatDate(match.matchDate) + '</div>' +
                       '<div class="form-guide-tooltip__match">' +
                           (match.venue === 'H'
                               ? 'vs ' + escapeHtml(match.opponent)
                               : '@ ' + escapeHtml(match.opponent)) +
                       '</div>' +
                       '<div class="form-guide-tooltip__score">' + score + '</div>' +
                       '<div class="form-guide-tooltip__venue">' + venue + '</div>' +
                   '</div>' +
               '</div>';
    }

    function renderWidget(data) {
        var matches = data.recentMatches || [];
        var badgesHtml = matches.map(renderBadge).join('');
        var trend = data.formTrend || 'Stable';
        var rating = data.formRating != null ? data.formRating : 0;
        var rc = ratingClass(rating);

        return '<div class="form-guide-widget">' +
            '<div class="form-guide-widget__header">' +
                '<span class="form-guide-widget__title">📊 Form Guide</span>' +
                '<span class="form-guide-trend form-guide-trend--' + trend + '">' +
                    '<span class="form-guide-trend__arrow">' + trendArrow(trend) + '</span> ' + trend +
                '</span>' +
            '</div>' +

            '<div class="form-guide-badges">' + badgesHtml + '</div>' +

            '<div class="form-guide-rating">' +
                '<div class="form-guide-rating__bar">' +
                    '<div class="form-guide-rating__fill form-guide-rating__fill--' + rc + '" ' +
                        'style="width: ' + (rating * 10) + '%"></div>' +
                '</div>' +
                '<span class="form-guide-rating__value">' + rating.toFixed(1) + '/10</span>' +
            '</div>' +

            '<div class="form-guide-stats">' +
                '<div class="form-guide-stat">' +
                    '<span class="form-guide-stat__value">' + (data.pointsInLast5 || 0) + '</span>' +
                    '<span class="form-guide-stat__label">Pts (Last 5)</span>' +
                '</div>' +
                '<div class="form-guide-stat">' +
                    '<span class="form-guide-stat__value">' + (data.pointsInPrevious5 || 0) + '</span>' +
                    '<span class="form-guide-stat__label">Pts (Prev 5)</span>' +
                '</div>' +
                '<div class="form-guide-stat">' +
                    '<span class="form-guide-stat__value">' + escapeHtml(data.formString || '-') + '</span>' +
                    '<span class="form-guide-stat__label">Form String</span>' +
                '</div>' +
            '</div>' +
        '</div>';
    }

    function renderLoading() {
        return '<div class="form-guide-loading">' +
                   '<div class="form-guide-loading__spinner"></div>' +
                   'Loading form guide…' +
               '</div>';
    }

    function renderError(msg) {
        return '<div class="form-guide-error">' +
                   '<div class="form-guide-error__icon">⚠️</div>' +
                   '<div>' + escapeHtml(msg || 'Failed to load form guide') + '</div>' +
               '</div>';
    }

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render the form guide widget into a container.
     * @param {HTMLElement|string} container  Element or ID
     * @param {string}             teamName   Team name
     * @param {number}             [numMatches=10]  Number of recent matches
     * @returns {Promise<object|null>} The form guide data, or null on error
     */
    async function render(container, teamName, numMatches) {
        numMatches = numMatches || 10;
        var el = typeof container === 'string' ? document.getElementById(container) : container;
        if (!el) { console.warn('[FormGuideWidget] Container not found'); return null; }

        el.innerHTML = renderLoading();

        try {
            var data;
            if (window.api && typeof window.api.getFormGuide === 'function') {
                data = await window.api.getFormGuide(teamName, numMatches);
            } else {
                var resp = await fetch('/api/teams/' + encodeURIComponent(teamName) + '/form-guide?matches=' + numMatches);
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                data = await resp.json();
            }

            el.innerHTML = renderWidget(data);
            return data;
        } catch (err) {
            console.error('[FormGuideWidget] Error:', err);
            el.innerHTML = renderError(err.message);
            return null;
        }
    }

    // Export
    window.FormGuideWidget = { render: render };
    console.log('[FormGuideWidget] Initialized');
})();


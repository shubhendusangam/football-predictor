/**
 * Fixture Congestion Card Component
 * ===================================
 *
 * Renders fixture congestion / fatigue analysis with:
 * - Fatigue gauge (0–100)
 * - Timeline of last 5 match gaps
 * - Win-rate breakdown by rest days
 * - Impact summary
 *
 * Also renders a side-by-side comparison widget for match previews.
 *
 * Usage:
 *   window.FixtureCongestionCard.render(container, data)
 *   window.FixtureCongestionCard.fetchAndRender(container, teamName)
 *   window.FixtureCongestionCard.renderComparison(container, data)
 *   window.FixtureCongestionCard.fetchAndRenderComparison(container, home, away)
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */

(function () {
    'use strict';

    // ── helpers ──────────────────────────────────────────────────────────

    function esc(s) {
        if (s == null) return '';
        var d = document.createElement('div');
        d.textContent = String(s);
        return d.innerHTML;
    }

    function fmt(v, dec) {
        if (v == null || isNaN(v)) return '0';
        return Number(v).toFixed(dec === undefined ? 1 : dec);
    }

    function levelClass(level) {
        if (!level) return 'unknown';
        switch (level) {
            case 'High':   return 'high';
            case 'Medium': return 'medium';
            case 'Low':    return 'low';
            default:       return 'unknown';
        }
    }

    function gapClass(days) {
        if (days < 3) return 'short';
        if (days <= 5) return 'normal';
        return 'long';
    }

    // ── single-team card ─────────────────────────────────────────────────

    function render(container, data) {
        if (!container) return;

        var d = {
            teamName:      (data && data.teamName) || 'Unknown',
            gaps:          (data && Array.isArray(data.daysBetweenMatches)) ? data.daysBetweenMatches : [],
            avgDays:       Number((data && data.avgDaysBetween) || 0),
            fatigue:       Number((data && data.fatigueIndex) || 0),
            level:         (data && data.fatigueLevel) || 'Unknown',
            winShort:      Number((data && data.winRateShortRest) || 0),
            winNormal:     Number((data && data.winRateNormalRest) || 0),
            winLong:       Number((data && data.winRateLongRest) || 0),
            nShort:        Number((data && data.matchesShortRest) || 0),
            nNormal:       Number((data && data.matchesNormalRest) || 0),
            nLong:         Number((data && data.matchesLongRest) || 0),
            lastMatch:     (data && data.lastMatchDate) || '',
            daysSince:     Number((data && data.daysSinceLastMatch) || 0),
            impact:        (data && data.impactSummary) || ''
        };

        var lc = levelClass(d.level);

        // Timeline dots + gaps
        var timelineHtml = '';
        var dotCount = d.gaps.length + 1;
        for (var i = 0; i < dotCount; i++) {
            var isLatest = (i === 0);
            timelineHtml += '<div class="congestion-card__match-dot' + (isLatest ? ' congestion-card__match-dot--latest' : '') + '"></div>';
            if (i < d.gaps.length) {
                var gc = gapClass(d.gaps[i]);
                timelineHtml += '\
                    <div class="congestion-card__gap">\
                        <div class="congestion-card__gap-line"></div>\
                        <span class="congestion-card__gap-days congestion-card__gap-days--' + gc + '">' + d.gaps[i] + 'd</span>\
                    </div>';
            }
        }

        container.innerHTML = '\
        <div class="congestion-card">\
            <div class="congestion-card__header">\
                <h3 class="congestion-card__title">📅 ' + esc(d.teamName) + ' Fixture Congestion</h3>\
                <span class="congestion-card__badge congestion-card__badge--' + lc + '">' + esc(d.level) + ' Fatigue</span>\
            </div>\
            \
            <div class="congestion-card__gauge">\
                <span class="congestion-card__gauge-value">' + d.fatigue + '</span>\
                <span class="congestion-card__gauge-label">Fatigue Index</span>\
                <div class="congestion-card__gauge-track">\
                    <div class="congestion-card__gauge-fill congestion-card__gauge-fill--' + lc + '" data-width="' + d.fatigue + '" style="width:0%"></div>\
                </div>\
                <div class="congestion-card__gauge-labels"><span>Well Rested</span><span>Congested</span></div>\
            </div>\
            \
            <div class="congestion-card__timeline">' + timelineHtml + '</div>\
            <div style="text-align:center;font-size:0.7rem;color:var(--text-muted,#94a3b8);margin-top:-0.5rem;">\
                Recent matches · avg ' + fmt(d.avgDays) + ' days between · ' + d.daysSince + 'd since last\
            </div>\
            \
            <div class="congestion-card__rates">\
                <div class="congestion-card__rate-item">\
                    <span class="congestion-card__rate-label">&lt; 3 days rest</span>\
                    <span class="congestion-card__rate-value congestion-card__rate-value--high">' + fmt(d.winShort) + '%</span>\
                    <span class="congestion-card__rate-count">' + d.nShort + ' matches</span>\
                </div>\
                <div class="congestion-card__rate-item">\
                    <span class="congestion-card__rate-label">3–5 days rest</span>\
                    <span class="congestion-card__rate-value congestion-card__rate-value--normal">' + fmt(d.winNormal) + '%</span>\
                    <span class="congestion-card__rate-count">' + d.nNormal + ' matches</span>\
                </div>\
                <div class="congestion-card__rate-item">\
                    <span class="congestion-card__rate-label">&gt; 5 days rest</span>\
                    <span class="congestion-card__rate-value congestion-card__rate-value--low">' + fmt(d.winLong) + '%</span>\
                    <span class="congestion-card__rate-count">' + d.nLong + ' matches</span>\
                </div>\
            </div>\
            \
            ' + (d.impact ? '<div class="congestion-card__impact"><span class="congestion-card__impact-icon">💡</span> ' + esc(d.impact) + '</div>' : '') + '\
            \
            <div class="congestion-card__footer">\
                <span>Last match: ' + esc(d.lastMatch) + '</span>\
                <span>' + (d.gaps.length + 1) + ' recent matches analysed</span>\
            </div>\
        </div>';

        // Animate gauge
        requestAnimationFrame(function () {
            setTimeout(function () {
                var fill = container.querySelector('.congestion-card__gauge-fill');
                if (fill) fill.style.width = fill.getAttribute('data-width') + '%';
            }, 100);
        });
    }

    // ── comparison widget ────────────────────────────────────────────────

    function renderComparison(container, data) {
        if (!container || !data) return;

        var home = data.home;
        var away = data.away;
        var adv  = data.advantageSummary || '';

        var advTeam = data.advantageTeam || 'neutral';
        var advColor = advTeam === 'neutral' ? 'var(--accent-yellow,#fbbf24)'
                     : advTeam === 'home' ? 'var(--accent-green,#22c55e)' : 'var(--accent-red,#ef4444)';

        container.innerHTML = '\
        <div class="congestion-comparison">\
            <div class="congestion-comparison__advantage" style="border-left: 3px solid ' + advColor + ';">\
                📅 ' + esc(adv) + '\
            </div>\
            <div class="congestion-comparison__cards">\
                <div id="congestion-cmp-home"></div>\
                <div id="congestion-cmp-away"></div>\
            </div>\
        </div>';

        if (home) render(document.getElementById('congestion-cmp-home'), home);
        if (away) render(document.getElementById('congestion-cmp-away'), away);
    }

    // ── loading / error ──────────────────────────────────────────────────

    function renderLoading(container) {
        if (!container) return;
        container.innerHTML = '\
        <div class="congestion-card congestion-card--loading">\
            <div class="congestion-card__header"><div class="congestion-card__skeleton congestion-card__skeleton--title"></div></div>\
            <div class="congestion-card__skeleton-content">\
                <div class="congestion-card__skeleton congestion-card__skeleton--gauge"></div>\
                <div class="congestion-card__skeleton congestion-card__skeleton--bar"></div>\
                <div class="congestion-card__skeleton congestion-card__skeleton--bar"></div>\
            </div>\
        </div>';
    }

    function renderError(container, msg) {
        if (!container) return;
        container.innerHTML = '\
        <div class="congestion-card congestion-card--error">\
            <div class="congestion-card__error">\
                <span class="congestion-card__error-icon">⚠️</span>\
                <p class="congestion-card__error-message">' + esc(msg || 'Failed to load congestion data') + '</p>\
            </div>\
        </div>';
    }

    // ── fetch wrappers ───────────────────────────────────────────────────

    function fetchAndRender(container, teamName) {
        if (!container || !teamName) return Promise.resolve(null);
        renderLoading(container);
        return fetch('/api/teams/' + encodeURIComponent(teamName) + '/fixture-congestion')
            .then(function (r) {
                if (!r.ok) {
                    if (r.status === 404) { renderError(container, 'No congestion data for this team'); return null; }
                    throw new Error('HTTP ' + r.status);
                }
                return r.json();
            })
            .then(function (d) { if (d) render(container, d); return d; })
            .catch(function (e) { renderError(container, e.message); return null; });
    }

    function fetchAndRenderComparison(container, home, away) {
        if (!container || !home || !away) return Promise.resolve(null);
        renderLoading(container);
        return fetch('/api/matches/congestion-comparison?home=' + encodeURIComponent(home) + '&away=' + encodeURIComponent(away))
            .then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(function (d) { if (d) renderComparison(container, d); return d; })
            .catch(function (e) { renderError(container, e.message); return null; });
    }

    // ── export ───────────────────────────────────────────────────────────

    window.FixtureCongestionCard = {
        render: render,
        renderComparison: renderComparison,
        renderLoading: renderLoading,
        renderError: renderError,
        fetchAndRender: fetchAndRender,
        fetchAndRenderComparison: fetchAndRenderComparison
    };

    console.log('[FixtureCongestionCard] Module initialized');
})();


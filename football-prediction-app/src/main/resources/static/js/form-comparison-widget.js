/**
 * Form Comparison Widget — Premium Edition
 * ==========================================
 * Stunning side-by-side form comparison for match preview.
 * Fetches form-guide data for both teams and renders an animated,
 * dark-themed comparison card with W/D/L timeline, stat bars, and
 * derived metrics (goals avg, conceded avg, clean-sheet %).
 *
 * Usage:
 *   window.FormComparisonWidget.render(container, homeTeam, awayTeam, numMatches)
 *
 * Dependencies:
 *   - api.js  (window.api.getFormGuide)
 *   - form-guide.css  (fcw-* classes)
 */
(function () {
    'use strict';

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    function esc(str) {
        if (str == null) return '';
        var d = document.createElement('div');
        d.textContent = String(str);
        return d.innerHTML;
    }

    function fetchGuide(teamName, numMatches) {
        if (window.api && typeof window.api.getFormGuide === 'function') {
            return window.api.getFormGuide(teamName, numMatches);
        }
        return fetch('/api/teams/' + encodeURIComponent(teamName) + '/form-guide?matches=' + numMatches)
            .then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            });
    }

    /** Derive extra stats from recentMatches array */
    function deriveStats(data) {
        var matches = (data.recentMatches || []).slice(0, 5);
        var n = matches.length || 1;
        var gf = 0, ga = 0, cs = 0, wins = 0, draws = 0, losses = 0;
        matches.forEach(function (m) {
            gf += m.goalsFor || 0;
            ga += m.goalsAgainst || 0;
            if ((m.goalsAgainst || 0) === 0) cs++;
            if (m.result === 'W') wins++;
            else if (m.result === 'D') draws++;
            else losses++;
        });
        return {
            matches: matches,
            goalsAvg: (gf / n).toFixed(2),
            concededAvg: (ga / n).toFixed(2),
            cleanSheetPct: Math.round((cs / n) * 100),
            wins: wins,
            draws: draws,
            losses: losses,
            rating: data.formRating != null ? data.formRating : 0,
            points: data.pointsInLast5 || 0,
            trend: data.formTrend || 'Stable',
            teamName: data.teamName || ''
        };
    }

    function trendMeta(trend) {
        switch (trend) {
            case 'Improving': return { icon: '↗', cls: 'improving', label: 'Improving' };
            case 'Declining': return { icon: '↘', cls: 'declining', label: 'Declining' };
            default:          return { icon: '→', cls: 'stable',    label: 'Stable' };
        }
    }

    /** Build a percentage for a stat-bar, clamped 8-95% for visual balance */
    function barPct(val, max) {
        if (max <= 0) return 50;
        var pct = (val / max) * 100;
        return Math.max(8, Math.min(95, pct));
    }

    // ══════════════════════════════════════════════════════════════════════
    // RENDER ENGINE
    // ══════════════════════════════════════════════════════════════════════

    function renderWidget(homeData, awayData) {
        var h = deriveStats(homeData);
        var a = deriveStats(awayData);

        // Badge HTML (last 5 matches, chronological order oldest→newest)
        function badges(matches) {
            var ordered = matches.slice().reverse();
            return ordered.map(function (m, i) {
                var r = m.result || 'L';
                var opponent = esc(m.opponent || '');
                var score = (m.goalsFor || 0) + '-' + (m.goalsAgainst || 0);
                var venue = m.venue === 'H' ? 'Home' : 'Away';
                return '<div class="fcw-badge fcw-badge--' + r + '" style="animation-delay:' + (i * 0.08) + 's" title="' + opponent + ' (' + venue + ') ' + score + '">' +
                           r +
                       '</div>';
            }).join('');
        }

        // Record text (e.g. "3W 1D 1L")
        function record(s) {
            var parts = [];
            if (s.wins)   parts.push('<span class="fcw-rec fcw-rec--W">' + s.wins + 'W</span>');
            if (s.draws)  parts.push('<span class="fcw-rec fcw-rec--D">' + s.draws + 'D</span>');
            if (s.losses) parts.push('<span class="fcw-rec fcw-rec--L">' + s.losses + 'L</span>');
            return parts.join(' ');
        }

        // Stat-bar row helper
        // `inverse` = true means lower is better (goals conceded)
        function statRow(label, icon, hVal, aVal, hDisplay, aDisplay, inverse) {
            var hNum = parseFloat(hVal) || 0;
            var aNum = parseFloat(aVal) || 0;
            var maxVal = Math.max(hNum, aNum, 0.01);
            var hPct = barPct(hNum, maxVal);
            var aPct = barPct(aNum, maxVal);

            var hBetter, aBetter;
            if (inverse) {
                hBetter = hNum < aNum;
                aBetter = aNum < hNum;
            } else {
                hBetter = hNum > aNum;
                aBetter = aNum > hNum;
            }

            return '<div class="fcw-stat-row">' +
                '<div class="fcw-stat-val' + (hBetter ? ' fcw-stat-val--better' : '') + '">' + (hDisplay != null ? hDisplay : hVal) + '</div>' +
                '<div class="fcw-stat-bars">' +
                    '<div class="fcw-bar-track fcw-bar-track--home">' +
                        '<div class="fcw-bar-fill fcw-bar-fill--home' + (hBetter ? ' fcw-bar-fill--winner' : '') + '" data-width="' + hPct + '"></div>' +
                    '</div>' +
                    '<div class="fcw-stat-label"><span class="fcw-stat-icon">' + icon + '</span>' + label + '</div>' +
                    '<div class="fcw-bar-track fcw-bar-track--away">' +
                        '<div class="fcw-bar-fill fcw-bar-fill--away' + (aBetter ? ' fcw-bar-fill--winner' : '') + '" data-width="' + aPct + '"></div>' +
                    '</div>' +
                '</div>' +
                '<div class="fcw-stat-val' + (aBetter ? ' fcw-stat-val--better' : '') + '">' + (aDisplay != null ? aDisplay : aVal) + '</div>' +
            '</div>';
        }

        // Trend row
        var hT = trendMeta(h.trend);
        var aT = trendMeta(a.trend);

        return '' +
        '<div class="fcw">' +
            /* ── Header ─────────────────────────────────────── */
            '<div class="fcw-header">' +
                '<div class="fcw-header-glow"></div>' +
                '<span class="fcw-header-icon">⚡</span>' +
                '<h3 class="fcw-title">Form Comparison</h3>' +
                '<span class="fcw-subtitle">Last 5 Matches</span>' +
            '</div>' +

            /* ── Teams + Badges ─────────────────────────────── */
            '<div class="fcw-teams">' +
                '<div class="fcw-team">' +
                    '<div class="fcw-team-name">' + esc(h.teamName) + '</div>' +
                    '<div class="fcw-badges">' + badges(h.matches) + '</div>' +
                    '<div class="fcw-record">' + record(h) + '</div>' +
                '</div>' +
                '<div class="fcw-vs">' +
                    '<div class="fcw-vs-ring"><span>VS</span></div>' +
                '</div>' +
                '<div class="fcw-team">' +
                    '<div class="fcw-team-name">' + esc(a.teamName) + '</div>' +
                    '<div class="fcw-badges">' + badges(a.matches) + '</div>' +
                    '<div class="fcw-record">' + record(a) + '</div>' +
                '</div>' +
            '</div>' +

            /* ── Divider ────────────────────────────────────── */
            '<div class="fcw-divider"><span>STATS</span></div>' +

            /* ── Stat Bars ──────────────────────────────────── */
            '<div class="fcw-stats">' +
                statRow('Rating',  '📊', h.rating.toFixed(1), a.rating.toFixed(1)) +
                statRow('Points',  '🏆', h.points, a.points) +
                statRow('GF Avg',  '⚽', h.goalsAvg, a.goalsAvg) +
                statRow('GA Avg',  '🛡️', h.concededAvg, a.concededAvg, null, null, true) +
                statRow('CS %',    '🧤', h.cleanSheetPct, a.cleanSheetPct, h.cleanSheetPct + '%', a.cleanSheetPct + '%') +
            '</div>' +

            /* ── Trend Footer ───────────────────────────────── */
            '<div class="fcw-trends">' +
                '<div class="fcw-trend fcw-trend--' + hT.cls + '">' +
                    '<span class="fcw-trend-arrow">' + hT.icon + '</span>' +
                    '<span>' + hT.label + '</span>' +
                '</div>' +
                '<div class="fcw-trend-label">Trend</div>' +
                '<div class="fcw-trend fcw-trend--' + aT.cls + '">' +
                    '<span class="fcw-trend-arrow">' + aT.icon + '</span>' +
                    '<span>' + aT.label + '</span>' +
                '</div>' +
            '</div>' +
        '</div>';
    }

    /** Animate bars on next frame after DOM insert */
    function animateBars(container) {
        var fills = container.querySelectorAll('.fcw-bar-fill');
        requestAnimationFrame(function () {
            setTimeout(function () {
                fills.forEach(function (el) {
                    el.style.width = el.getAttribute('data-width') + '%';
                });
            }, 60);
        });
    }

    function renderLoading() {
        return '<div class="fcw fcw--loading">' +
                   '<div class="fcw-header">' +
                       '<div class="fcw-header-glow"></div>' +
                       '<span class="fcw-header-icon">⚡</span>' +
                       '<h3 class="fcw-title">Form Comparison</h3>' +
                       '<span class="fcw-subtitle">Last 5 Matches</span>' +
                   '</div>' +
                   '<div class="fcw-loading-body">' +
                       '<div class="fcw-loading-spinner"></div>' +
                       '<span>Comparing recent form…</span>' +
                   '</div>' +
               '</div>';
    }

    function renderError(msg) {
        return '<div class="fcw fcw--error">' +
                   '<div class="fcw-header">' +
                       '<div class="fcw-header-glow"></div>' +
                       '<span class="fcw-header-icon">⚡</span>' +
                       '<h3 class="fcw-title">Form Comparison</h3>' +
                   '</div>' +
                   '<div class="fcw-error-body">' +
                       '<div class="fcw-error-icon">⚠️</div>' +
                       '<div>' + esc(msg || 'Failed to load form comparison') + '</div>' +
                   '</div>' +
               '</div>';
    }

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Render form comparison into a container.
     * @param {HTMLElement|string} container   Element or ID
     * @param {string}             homeTeam    Home team name
     * @param {string}             awayTeam    Away team name
     * @param {number}             [numMatches=10]  Matches to fetch (widget displays last 5)
     * @returns {Promise<{home: object, away: object}|null>}
     */
    async function render(container, homeTeam, awayTeam, numMatches) {
        numMatches = numMatches || 10;
        var el = typeof container === 'string' ? document.getElementById(container) : container;
        if (!el) { console.warn('[FormComparisonWidget] Container not found'); return null; }

        el.innerHTML = renderLoading();

        try {
            var results = await Promise.all([
                fetchGuide(homeTeam, numMatches),
                fetchGuide(awayTeam, numMatches)
            ]);

            el.innerHTML = renderWidget(results[0], results[1]);
            animateBars(el);

            return { home: results[0], away: results[1] };
        } catch (err) {
            console.error('[FormComparisonWidget] Error:', err);
            el.innerHTML = renderError(err.message);
            return null;
        }
    }

    // Export
    window.FormComparisonWidget = { render: render };
    console.log('[FormComparisonWidget] Initialized');
})();


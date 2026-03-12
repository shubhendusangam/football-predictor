/**
 * Referee Statistics Page Module  (v2 – enhanced)
 * =================================================
 *
 * Features:
 *  - League-wide summary row at the top
 *  - Card-type filter buttons (All / Strict / Balanced / Lenient)
 *  - Search + sort controls
 *  - Animated staggered card grid
 *  - Click-to-expand detail view with:
 *       • SVG strictness gauge (radial arc)
 *       • Horizontal stacked results distribution bar (H/D/A)
 *       • Horizontal bar charts for every key stat
 *       • New metrics: over 2.5 goals %, cards-per-foul, seasons active
 *  - Referee comparison mode (pick two side-by-side)
 *  - Referee impact widget (for match preview)
 *
 * Usage:
 *   window.RefereesPage.init(container)
 *   window.RefereeStatsCard.render(container, refereeData)
 *   window.RefereeImpactWidget.fetchAndRender(container, referee, homeTeam, awayTeam)
 *
 * @author Football Forecaster Team
 * @version 2.0.0
 */

(function () {
    'use strict';

    // ══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════

    var API_BASE = '/api/referees';
    var SORT_OPTIONS = {
        matches:  { label: 'Matches Officiated', field: 'matchesOfficiated', desc: true },
        cards:    { label: 'Cards per Game',     field: 'avgYellowCards',    desc: true },
        redCards: { label: 'Red Card %',         field: 'redCardPercentage', desc: true },
        fouls:    { label: 'Fouls per Game',     field: 'avgFoulsPerGame',   desc: true },
        homeWin:  { label: 'Home Win %',         field: 'homeWinPercentage', desc: true },
        goals:    { label: 'Goals per Game',     field: 'avgGoalsPerGame',   desc: true },
        name:     { label: 'Name (A-Z)',         field: 'refereeName',       desc: false }
    };

    var BAR_METRICS = [
        { key: 'avgYellowCards',    label: 'Avg Yellow Cards',   max: 6,   color: '#eab308', icon: '🟨' },
        { key: 'avgRedCards',       label: 'Avg Red Cards',      max: 0.5, color: '#ef4444', icon: '🟥' },
        { key: 'avgFoulsPerGame',   label: 'Avg Fouls / Game',   max: 30,  color: '#f97316', icon: '⚡' },
        { key: 'avgGoalsPerGame',   label: 'Avg Goals / Game',   max: 5,   color: '#22c55e', icon: '⚽' },
        { key: 'over25GoalsRate',   label: 'Over 2.5 Goals %',   max: 100, color: '#06b6d4', icon: '📊' },
        { key: 'cardsPerFoul',      label: 'Cards / Foul Ratio', max: 0.5, color: '#a855f7', icon: '📋' },
        { key: 'redCardPercentage', label: 'Red Card %',         max: 30,  color: '#ef4444', icon: '🔴' }
    ];

    // ══════════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════════

    var state = {
        allReferees: [],
        filteredReferees: [],
        currentSort: 'matches',
        searchTerm: '',
        activeFilter: 'All',
        selectedReferee: null,
        isLoading: false,
        container: null,
        compareMode: false,
        summaryData: null
    };

    // ══════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ══════════════════════════════════════════════════════════════════

    function escapeHtml(str) {
        if (str == null) return '';
        var div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    function formatNumber(value, decimals) {
        decimals = decimals !== undefined ? decimals : 1;
        if (value == null || isNaN(value)) return '0.0';
        return Number(value).toFixed(decimals);
    }

    function formatPct(value) {
        if (value == null || isNaN(value)) return '0.0%';
        return Number(value).toFixed(1) + '%';
    }

    function getBadgeClass(refType) {
        if (!refType) return 'referee-stats-card__badge--unknown';
        switch (refType.toLowerCase()) {
            case 'strict':   return 'referee-stats-card__badge--strict';
            case 'lenient':  return 'referee-stats-card__badge--lenient';
            case 'balanced': return 'referee-stats-card__badge--balanced';
            default:         return 'referee-stats-card__badge--unknown';
        }
    }

    function getRiskClass(level) {
        if (!level) return 'referee-impact-widget__risk--low';
        switch (level.toLowerCase()) {
            case 'high':   return 'referee-impact-widget__risk--high';
            case 'medium': return 'referee-impact-widget__risk--medium';
            default:       return 'referee-impact-widget__risk--low';
        }
    }

    function getHomeAdvClass(value) {
        if (value > 1)  return 'referee-impact-widget__home-adv-value--positive';
        if (value < -1) return 'referee-impact-widget__home-adv-value--negative';
        return 'referee-impact-widget__home-adv-value--neutral';
    }

    function getBarWidth(value, max) {
        if (!value || !max || max <= 0) return 0;
        return Math.min(100, Math.max(0, (value / max) * 100));
    }

    function gaugeColor(v) {
        if (v <= 0.35) return '#22c55e';
        if (v <= 0.65) return '#eab308';
        return '#ef4444';
    }

    // ══════════════════════════════════════════════════════════════════
    // LEAGUE SUMMARY COMPONENT
    // ══════════════════════════════════════════════════════════════════

    function renderLeagueSummary(data) {
        if (!data) return '';

        var top3StrictHtml = '';
        if (data.top3Strictest && data.top3Strictest.length) {
            data.top3Strictest.forEach(function (r, i) {
                top3StrictHtml += '<div class="league-summary__panel-row">' +
                    '<span class="league-summary__rank">' + (i + 1) + '.</span>' +
                    '<span class="league-summary__name">' + escapeHtml(r.name) + '</span>' +
                    '<span class="league-summary__avg">' + escapeHtml(String(r.avgCards)) + ' cards/g</span></div>';
            });
        } else {
            top3StrictHtml = '<span class="league-summary__no-data">No data</span>';
        }

        var top3LenientHtml = '';
        if (data.top3Lenient && data.top3Lenient.length) {
            data.top3Lenient.forEach(function (r, i) {
                top3LenientHtml += '<div class="league-summary__panel-row">' +
                    '<span class="league-summary__rank">' + (i + 1) + '.</span>' +
                    '<span class="league-summary__name">' + escapeHtml(r.name) + '</span>' +
                    '<span class="league-summary__avg">' + escapeHtml(String(r.avgCards)) + ' cards/g</span></div>';
            });
        } else {
            top3LenientHtml = '<span class="league-summary__no-data">No data</span>';
        }

        var expHtml = '';
        if (data.mostExperienced) {
            expHtml = '<div class="league-summary__panel">' +
                '<div class="league-summary__panel-title">🏆 Most Experienced</div>' +
                '<div class="league-summary__panel-body"><strong>' + escapeHtml(data.mostExperienced.name) + '</strong>' +
                ' <span class="league-summary__panel-sub">' + data.mostExperienced.matches + ' matches</span></div></div>';
        }

        return '\
        <div class="league-summary">\
            <div class="league-summary__header">\
                <h3 class="league-summary__title">⚖️ League Referee Overview</h3>\
            </div>\
            <div class="league-summary__tiles">\
                <div class="league-summary__tile"><span class="league-summary__tile-icon">👨‍⚖️</span><span class="league-summary__tile-value">' + (data.totalReferees != null ? data.totalReferees : '-') + '</span><span class="league-summary__tile-label">Total Referees</span></div>\
                <div class="league-summary__tile"><span class="league-summary__tile-icon">📋</span><span class="league-summary__tile-value">' + (data.totalMatchesCovered != null ? data.totalMatchesCovered : '-') + '</span><span class="league-summary__tile-label">Total Matches</span></div>\
                <div class="league-summary__tile"><span class="league-summary__tile-icon">🟨</span><span class="league-summary__tile-value">' + (data.leagueAvgCardsPerGame != null ? data.leagueAvgCardsPerGame : '-') + '</span><span class="league-summary__tile-label">Avg Cards / Game</span></div>\
                <div class="league-summary__tile"><span class="league-summary__tile-icon">⚽</span><span class="league-summary__tile-value">' + (data.leagueAvgGoalsPerGame != null ? data.leagueAvgGoalsPerGame : '-') + '</span><span class="league-summary__tile-label">Avg Goals / Game</span></div>\
                <div class="league-summary__tile"><span class="league-summary__tile-icon">⚡</span><span class="league-summary__tile-value">' + (data.leagueAvgFoulsPerGame != null ? data.leagueAvgFoulsPerGame : '-') + '</span><span class="league-summary__tile-label">Avg Fouls / Game</span></div>\
                <div class="league-summary__tile"><span class="league-summary__tile-icon">🏠</span><span class="league-summary__tile-value">' + (data.leagueAvgHomeWinPct != null ? data.leagueAvgHomeWinPct : '-') + '</span><span class="league-summary__tile-label">Home Win %</span></div>\
            </div>\
            <div class="league-summary__type-row">\
                <span class="league-summary__type-badge league-summary__type-badge--strict">Strict ' + (data.strictCount || 0) + '</span>\
                <span class="league-summary__type-badge league-summary__type-badge--balanced">Balanced ' + (data.balancedCount || 0) + '</span>\
                <span class="league-summary__type-badge league-summary__type-badge--lenient">Lenient ' + (data.lenientCount || 0) + '</span>\
            </div>\
            <div class="league-summary__panels">\
                <div class="league-summary__panel"><div class="league-summary__panel-title">🔴 Top 3 Strictest</div><div class="league-summary__panel-body">' + top3StrictHtml + '</div></div>\
                <div class="league-summary__panel"><div class="league-summary__panel-title">🟢 Top 3 Lenient</div><div class="league-summary__panel-body">' + top3LenientHtml + '</div></div>\
                ' + expHtml + '\
            </div>\
        </div>';
    }

    // ══════════════════════════════════════════════════════════════════
    // SVG STRICTNESS GAUGE
    // ══════════════════════════════════════════════════════════════════

    function describeArc(cx, cy, r, fraction) {
        var startAngle = Math.PI;
        var endAngle = Math.PI - fraction * Math.PI;
        var x1 = cx + r * Math.cos(startAngle);
        var y1 = cy + r * Math.sin(startAngle);
        var x2 = cx + r * Math.cos(endAngle);
        var y2 = cy + r * Math.sin(endAngle);
        var largeArc = fraction > 0.5 ? 1 : 0;
        return 'M ' + x1 + ' ' + y1 + ' A ' + r + ' ' + r + ' 0 ' + largeArc + ' 0 ' + x2 + ' ' + y2;
    }

    function renderStrictnessGaugeHtml(value, label) {
        var v = Math.max(0, Math.min(1, value || 0));
        var color = gaugeColor(v);
        var cx = 100, cy = 110, r = 85;

        return '\
        <div class="strictness-gauge">\
            <svg width="200" height="130" viewBox="0 0 200 130">\
                <path d="' + describeArc(cx, cy, r, 1) + '" fill="none" stroke="rgba(255,255,255,0.08)" stroke-width="14" stroke-linecap="round"/>\
                <path d="' + describeArc(cx, cy, r, v) + '" fill="none" stroke="' + color + '" stroke-width="14" stroke-linecap="round"/>\
                <text x="18" y="120" fill="#94a3b8" font-size="11" text-anchor="middle">Lenient</text>\
                <text x="182" y="120" fill="#94a3b8" font-size="11" text-anchor="middle">Strict</text>\
                <text x="100" y="95" fill="#f1f5f9" font-size="28" font-weight="700" text-anchor="middle">' + (v * 100).toFixed(0) + '</text>\
                <text x="100" y="112" fill="#94a3b8" font-size="11" text-anchor="middle">' + escapeHtml(label || 'Strictness') + '</text>\
            </svg>\
        </div>';
    }

    // ══════════════════════════════════════════════════════════════════
    // RESULTS DISTRIBUTION BAR (H / D / A)
    // ══════════════════════════════════════════════════════════════════

    function renderResultsDistributionHtml(data) {
        var home = data.homeWinPercentage || 0;
        var draw = data.drawPercentage || 0;
        var away = data.awayWinPercentage || 0;

        var segments = [
            { pct: home, color: '#22c55e', label: 'H ' + home.toFixed(1) + '%' },
            { pct: draw, color: '#eab308', label: 'D ' + draw.toFixed(1) + '%' },
            { pct: away, color: '#ef4444', label: 'A ' + away.toFixed(1) + '%' }
        ];

        var barsHtml = '';
        segments.forEach(function (seg) {
            if (seg.pct <= 0) return;
            var fontSize = seg.pct < 15 ? 'font-size:0.6rem;' : '';
            barsHtml += '<div class="results-dist__segment" style="width:' + seg.pct + '%;background:' + seg.color + ';">' +
                '<span class="results-dist__segment-label" style="' + fontSize + '">' + seg.label + '</span></div>';
        });

        return '\
        <div class="results-dist">\
            <div class="results-dist__title">Results Distribution</div>\
            <div class="results-dist__bar">' + barsHtml + '</div>\
            <div class="results-dist__legend">\
                <div class="results-dist__legend-item"><span class="results-dist__dot" style="background:#22c55e"></span>Home Win ' + home.toFixed(1) + '%</div>\
                <div class="results-dist__legend-item"><span class="results-dist__dot" style="background:#eab308"></span>Draw ' + draw.toFixed(1) + '%</div>\
                <div class="results-dist__legend-item"><span class="results-dist__dot" style="background:#ef4444"></span>Away Win ' + away.toFixed(1) + '%</div>\
            </div>\
        </div>';
    }

    // ══════════════════════════════════════════════════════════════════
    // STATS BAR CHARTS
    // ══════════════════════════════════════════════════════════════════

    function renderStatsBarChartsHtml(data) {
        var html = '<div class="ref-bars"><div class="ref-bars__title">Key Statistics</div>';

        BAR_METRICS.forEach(function (m, idx) {
            var value = data[m.key] || 0;
            var pct = Math.min(100, (value / m.max) * 100);
            var displayVal = m.max <= 1 ? value.toFixed(3) : value.toFixed(1);

            html += '<div class="ref-bars__row" style="animation-delay:' + (idx * 60) + 'ms">' +
                '<div class="ref-bars__label"><span class="ref-bars__icon">' + m.icon + '</span>' + m.label + '</div>' +
                '<div class="ref-bars__track"><div class="ref-bars__fill" style="width:' + pct + '%;background:' + m.color + ';"></div></div>' +
                '<div class="ref-bars__value">' + displayVal + '</div></div>';
        });

        html += '</div>';
        return html;
    }

    // ══════════════════════════════════════════════════════════════════
    // REFEREE STATS CARD (GRID ITEM)
    // ══════════════════════════════════════════════════════════════════

    function renderRefereeStatsCard(data, index) {
        if (!data) return '<div class="referee-stats-card">No data</div>';

        var yellowBarWidth = getBarWidth(data.avgYellowCards, 6);
        var redBarWidth = getBarWidth(data.redCardPercentage, 30);
        var delay = (index || 0) * 50;

        return '\
        <div class="referee-stats-card referee-stats-card--animated" data-referee="' + escapeHtml(data.refereeName) + '" style="animation-delay:' + delay + 'ms">\
            <div class="referee-stats-card__header">\
                <div class="referee-stats-card__avatar">🏁</div>\
                <div>\
                    <h3 class="referee-stats-card__name">' + escapeHtml(data.refereeName) + '</h3>\
                    <p class="referee-stats-card__matches">' + data.matchesOfficiated + ' matches officiated</p>\
                </div>\
                <span class="referee-stats-card__badge ' + getBadgeClass(data.refType) + '">\
                    ' + escapeHtml(data.refType || 'Unknown') + '\
                </span>\
            </div>\
            \
            <div class="referee-stats-card__stats">\
                <div class="referee-stats-card__stat">\
                    <span class="referee-stats-card__stat-label">🟨 Yellow Cards/Game</span>\
                    <span class="referee-stats-card__stat-value referee-stats-card__stat-value--yellow">' + formatNumber(data.avgYellowCards) + '</span>\
                    <div class="referee-stats-card__bar-container">\
                        <div class="referee-stats-card__bar referee-stats-card__bar--yellow" style="width: ' + yellowBarWidth + '%;"></div>\
                    </div>\
                </div>\
                <div class="referee-stats-card__stat">\
                    <span class="referee-stats-card__stat-label">🟥 Red Card Rate</span>\
                    <span class="referee-stats-card__stat-value referee-stats-card__stat-value--red">' + formatPct(data.redCardPercentage) + '</span>\
                    <div class="referee-stats-card__bar-container">\
                        <div class="referee-stats-card__bar referee-stats-card__bar--red" style="width: ' + redBarWidth + '%;"></div>\
                    </div>\
                </div>\
                <div class="referee-stats-card__stat">\
                    <span class="referee-stats-card__stat-label">🏠 Home Win %</span>\
                    <span class="referee-stats-card__stat-value">' + formatPct(data.homeWinPercentage) + '</span>\
                </div>\
                <div class="referee-stats-card__stat">\
                    <span class="referee-stats-card__stat-label">⚽ Goals/Game</span>\
                    <span class="referee-stats-card__stat-value">' + formatNumber(data.avgGoalsPerGame) + '</span>\
                </div>\
            </div>\
            \
            <div class="referee-stats-card__footer">\
                <span>📊 Over 2.5: ' + formatPct(data.over25GoalsRate) + '</span>\
                <span>📋 Cards/Foul: ' + formatNumber(data.cardsPerFoul, 3) + '</span>\
                <span>👊 Fouls/Game: ' + formatNumber(data.avgFoulsPerGame) + '</span>\
            </div>\
        </div>';
    }

    // ══════════════════════════════════════════════════════════════════
    // REFEREE DETAIL VIEW (EXPANDED)
    // ══════════════════════════════════════════════════════════════════

    function renderRefereeDetail(data) {
        if (!data) return '';

        return '\
        <div class="referee-detail">\
            <button class="referees-page__back-btn" id="refereeBackBtn">← Back to all referees</button>\
            <div class="referee-detail__header">\
                <div class="referee-detail__avatar">🏁</div>\
                <div>\
                    <h2 class="referee-detail__name">' + escapeHtml(data.refereeName) + '</h2>\
                    <p class="referee-detail__meta">' + data.matchesOfficiated + ' matches · ' + escapeHtml(data.refType || 'Unknown') + ' referee · ' + (data.seasonsActive || '-') + ' seasons</p>\
                </div>\
                <span class="referee-stats-card__badge ' + getBadgeClass(data.refType) + '" style="margin-left:auto;font-size:0.875rem;">\
                    ' + escapeHtml(data.refType || 'Unknown') + '\
                </span>\
            </div>\
            \
            <div class="referee-detail__visual-row">\
                <div class="referee-detail__visual-item">' + renderStrictnessGaugeHtml(data.strictnessIndex, data.refType) + '</div>\
                <div class="referee-detail__visual-item">' + renderResultsDistributionHtml(data) + '</div>\
            </div>\
            \
            <div class="referee-detail__bar-charts">' + renderStatsBarChartsHtml(data) + '</div>\
            \
            <div class="referee-detail__sections">\
                <div class="referee-detail__section">\
                    <h4 class="referee-detail__section-title">🟨 Card Statistics</h4>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Avg Yellow Cards/Game</span><span class="referee-detail__stat-val" style="color:#fbbf24;">' + formatNumber(data.avgYellowCards, 2) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Home Yellow Cards/Game</span><span class="referee-detail__stat-val">' + formatNumber(data.avgHomeYellowCards, 2) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Away Yellow Cards/Game</span><span class="referee-detail__stat-val">' + formatNumber(data.avgAwayYellowCards, 2) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Avg Red Cards/Game</span><span class="referee-detail__stat-val" style="color:#ef4444;">' + formatNumber(data.avgRedCards, 3) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Red Card Rate</span><span class="referee-detail__stat-val" style="color:#ef4444;">' + formatPct(data.redCardPercentage) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Cards / Foul Ratio</span><span class="referee-detail__stat-val" style="color:#a855f7;">' + formatNumber(data.cardsPerFoul, 3) + '</span></div>\
                </div>\
                \
                <div class="referee-detail__section">\
                    <h4 class="referee-detail__section-title">👊 Fouls & Discipline</h4>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Avg Fouls/Game</span><span class="referee-detail__stat-val">' + formatNumber(data.avgFoulsPerGame, 1) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Home Fouls/Game</span><span class="referee-detail__stat-val">' + formatNumber(data.avgHomeFouls, 1) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Away Fouls/Game</span><span class="referee-detail__stat-val">' + formatNumber(data.avgAwayFouls, 1) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Strictness Index</span><span class="referee-detail__stat-val">' + formatNumber(data.strictnessIndex, 3) + '</span></div>\
                </div>\
                \
                <div class="referee-detail__section">\
                    <h4 class="referee-detail__section-title">⚽ Match Outcomes</h4>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Avg Goals/Game</span><span class="referee-detail__stat-val">' + formatNumber(data.avgGoalsPerGame, 2) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Over 2.5 Goals %</span><span class="referee-detail__stat-val" style="color:#06b6d4;">' + formatPct(data.over25GoalsRate) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Seasons Active</span><span class="referee-detail__stat-val">' + (data.seasonsActive || '-') + '</span></div>\
                </div>\
                \
                <div class="referee-detail__section">\
                    <h4 class="referee-detail__section-title">📊 Data Quality</h4>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Data Completeness</span><span class="referee-detail__stat-val">' + formatPct(data.dataCompleteness) + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Card Style</span><span class="referee-detail__stat-val">' + escapeHtml(data.cardStyle || 'N/A') + '</span></div>\
                    <div class="referee-detail__stat-row"><span class="referee-detail__stat-name">Confidence</span><span class="referee-detail__stat-val">' + (data.matchesOfficiated >= 20 ? 'High' : data.matchesOfficiated >= 10 ? 'Medium' : 'Low') + '</span></div>\
                </div>\
            </div>\
        </div>';
    }

    // ══════════════════════════════════════════════════════════════════
    // COMPARISON PANEL
    // ══════════════════════════════════════════════════════════════════

    function renderComparisonPanel() {
        var opts = '';
        var names = state.allReferees.map(function (r) { return r.refereeName; }).sort();
        names.forEach(function (n) {
            opts += '<option value="' + escapeHtml(n) + '">' + escapeHtml(n) + '</option>';
        });

        return '\
        <div class="ref-compare">\
            <div class="ref-compare__header"><h3 class="ref-compare__title">🔄 Compare Referees</h3></div>\
            <div class="ref-compare__selectors">\
                <select class="ref-compare__select" id="refCompareSel1"><option value="">Select Referee 1</option>' + opts + '</select>\
                <span class="ref-compare__vs">vs</span>\
                <select class="ref-compare__select" id="refCompareSel2"><option value="">Select Referee 2</option>' + opts + '</select>\
                <button class="ref-compare__btn" id="refCompareBtn">Compare</button>\
            </div>\
            <div class="ref-compare__results" id="refCompareResults"></div>\
        </div>';
    }

    function attachComparisonListeners() {
        var btn = document.getElementById('refCompareBtn');
        if (btn) {
            btn.addEventListener('click', function () {
                var sel1 = document.getElementById('refCompareSel1');
                var sel2 = document.getElementById('refCompareSel2');
                var results = document.getElementById('refCompareResults');
                if (!sel1 || !sel2 || !results) return;

                var n1 = sel1.value, n2 = sel2.value;
                if (!n1 || !n2) { results.innerHTML = '<div class="ref-compare__hint">Please select two referees.</div>'; return; }
                if (n1 === n2) { results.innerHTML = '<div class="ref-compare__hint">Please select two different referees.</div>'; return; }

                results.innerHTML = '<div class="ref-compare__loading"><div class="referees-page__spinner"></div> Comparing…</div>';

                fetch(API_BASE + '/compare?ref1=' + encodeURIComponent(n1) + '&ref2=' + encodeURIComponent(n2))
                    .then(function (res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
                    .then(function (data) { renderComparisonResults(results, data); })
                    .catch(function (err) { results.innerHTML = '<div class="ref-compare__error">⚠️ ' + escapeHtml(err.message) + '</div>'; });
            });
        }
    }

    function renderComparisonResults(el, data) {
        var r1 = data.referee1, r2 = data.referee2;
        if (!r1 || !r2) { el.innerHTML = '<span>No data</span>'; return; }

        var html = '<div class="ref-compare__grid">' + buildCompareCard(r1) + buildCompareCard(r2) + '</div>';

        if (data.verdicts && data.verdicts.length) {
            html += '<div class="ref-compare__verdicts"><div class="ref-compare__verdicts-title">⚖️ Comparison Verdicts</div>';
            data.verdicts.forEach(function (v) {
                var tieClass = v.winner === 'tie' ? ' ref-compare__verdict-winner--tie' : '';
                html += '<div class="ref-compare__verdict-row">' +
                    '<span class="ref-compare__verdict-metric">' + escapeHtml(v.metric) + '</span>' +
                    '<span class="ref-compare__verdict-winner' + tieClass + '">' + escapeHtml(v.winner) + '</span>' +
                    '<span class="ref-compare__verdict-note">' + escapeHtml(v.note) + '</span></div>';
            });
            html += '</div>';
        }

        el.innerHTML = html;
    }

    function buildCompareCard(stats) {
        var typeClass = (stats.refType || '').toLowerCase();
        var statRows = [
            { label: 'Matches',    val: stats.matchesOfficiated },
            { label: 'Yellows/G',  val: formatNumber(stats.avgYellowCards, 2) },
            { label: 'Reds/G',     val: formatNumber(stats.avgRedCards, 3) },
            { label: 'Fouls/G',    val: formatNumber(stats.avgFoulsPerGame, 1) },
            { label: 'Goals/G',    val: formatNumber(stats.avgGoalsPerGame, 2) },
            { label: 'Home Win %', val: formatPct(stats.homeWinPercentage) },
            { label: 'Over 2.5 %', val: formatPct(stats.over25GoalsRate) },
            { label: 'Cards/Foul', val: formatNumber(stats.cardsPerFoul, 3) }
        ];

        var statsHtml = '';
        statRows.forEach(function (s) {
            statsHtml += '<div class="ref-compare__stat"><span class="ref-compare__stat-label">' + s.label + '</span><span class="ref-compare__stat-value">' + s.val + '</span></div>';
        });

        return '<div class="ref-compare__card">' +
            '<div class="ref-compare__card-name">' + escapeHtml(stats.refereeName) + '</div>' +
            '<span class="ref-compare__badge ref-compare__badge--' + typeClass + '">' + escapeHtml(stats.refType || '—') + '</span>' +
            '<div class="ref-compare__gauge">' + renderStrictnessGaugeHtml(stats.strictnessIndex, stats.refType) + '</div>' +
            '<div class="ref-compare__stats-grid">' + statsHtml + '</div></div>';
    }

    // ══════════════════════════════════════════════════════════════════
    // REFEREE IMPACT WIDGET
    // ══════════════════════════════════════════════════════════════════

    function renderImpactWidget(data) {
        if (!data) return '<div class="referee-impact-widget">No impact data available</div>';

        var homeAdvValue = data.homeAdvantageAdjustment || 0;
        var homeAdvSign = homeAdvValue > 0 ? '+' : '';
        var homeAdvClass = getHomeAdvClass(homeAdvValue);
        var riskClass = getRiskClass(data.riskLevel);

        var warningHtml = '';
        if (data.warning) {
            warningHtml = '<div class="referee-impact-widget__warning"><span class="referee-impact-widget__warning-icon">⚠️</span><span class="referee-impact-widget__warning-text">' + escapeHtml(data.warning) + '</span></div>';
        }

        return '\
        <div class="referee-impact-widget">\
            <div class="referee-impact-widget__header">\
                <span class="referee-impact-widget__icon">🏁</span>\
                <div><h4 class="referee-impact-widget__title">Referee Impact Analysis</h4><p class="referee-impact-widget__ref-name">' + escapeHtml(data.refereeName) + '</p></div>\
                <span class="referee-impact-widget__risk ' + riskClass + '" style="margin-left:auto;">' + escapeHtml(data.riskLevel || 'Low') + ' Risk</span>\
            </div>\
            <div class="referee-impact-widget__stats">\
                <div class="referee-impact-widget__stat"><span class="referee-impact-widget__stat-label">🟨 Expected Yellows</span><span class="referee-impact-widget__stat-value" style="color:#fbbf24;">' + formatNumber(data.expectedYellowCards) + '</span></div>\
                <div class="referee-impact-widget__stat"><span class="referee-impact-widget__stat-label">🟥 Red Card Prob</span><span class="referee-impact-widget__stat-value" style="color:#ef4444;">' + formatPct(data.redCardProbability * 100) + '</span></div>\
                <div class="referee-impact-widget__stat"><span class="referee-impact-widget__stat-label">🏠 ' + escapeHtml(data.homeTeam || 'Home') + '</span><span class="referee-impact-widget__stat-value" style="color:#fbbf24;">🟨 ' + formatNumber(data.expectedHomeYellowCards) + '</span></div>\
                <div class="referee-impact-widget__stat"><span class="referee-impact-widget__stat-label">✈️ ' + escapeHtml(data.awayTeam || 'Away') + '</span><span class="referee-impact-widget__stat-value" style="color:#fbbf24;">🟨 ' + formatNumber(data.expectedAwayYellowCards) + '</span></div>\
            </div>\
            <div class="referee-impact-widget__home-adv"><span class="referee-impact-widget__home-adv-icon">🏠</span><span class="referee-impact-widget__home-adv-text">Home Advantage Adjustment</span><span class="referee-impact-widget__home-adv-value ' + homeAdvClass + '">' + homeAdvSign + formatNumber(homeAdvValue) + '%</span></div>\
            ' + warningHtml + '\
            <div style="margin-top:0.75rem;text-align:right;font-size:0.75rem;color:var(--text-muted,#64748b);">Confidence: ' + escapeHtml(data.confidence || 'Low') + '</div>\
        </div>';
    }

    function renderImpactLoading(container) {
        if (!container) return;
        container.innerHTML = '<div class="referee-impact-widget" style="text-align:center;padding:2rem;"><div class="referees-page__spinner"></div><span style="color:var(--text-muted,#64748b);">Loading referee impact...</span></div>';
    }

    function renderImpactError(container, message) {
        if (!container) return;
        container.innerHTML = '<div class="referee-impact-widget" style="text-align:center;padding:1.5rem;"><span style="font-size:1.5rem;">⚠️</span><p style="color:var(--text-muted,#64748b);margin:0.5rem 0 0;">' + escapeHtml(message || 'Failed to load referee impact') + '</p></div>';
    }

    function fetchImpactAndRender(container, referee, homeTeam, awayTeam) {
        if (!container || !referee || !homeTeam || !awayTeam) {
            console.warn('[RefereeImpactWidget] Missing required params');
            return Promise.resolve(null);
        }
        renderImpactLoading(container);
        var url = '/api/referees/impact?ref=' + encodeURIComponent(referee) + '&home=' + encodeURIComponent(homeTeam) + '&away=' + encodeURIComponent(awayTeam);
        return fetch(url)
            .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
            .then(function (d) { container.innerHTML = renderImpactWidget(d); return d; })
            .catch(function (e) { console.error('[RefereeImpactWidget] Error:', e); renderImpactError(container, e.message); return null; });
    }

    // ══════════════════════════════════════════════════════════════════
    // MAIN PAGE
    // ══════════════════════════════════════════════════════════════════

    function initPage(container) {
        if (!container) { console.error('[RefereesPage] No container provided'); return; }
        state.container = container;
        state.selectedReferee = null;
        state.activeFilter = 'All';
        state.compareMode = false;
        state.summaryData = null;
        loadAllReferees();
    }

    function loadAllReferees() {
        state.isLoading = true;
        renderLoading();

        // Fetch summary + comprehensive in parallel
        var summaryPromise = fetch(API_BASE + '/summary')
            .then(function (r) { return r.ok ? r.json() : null; })
            .catch(function () { return null; });

        var dataPromise = fetch(API_BASE + '/comprehensive')
            .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); });

        Promise.all([summaryPromise, dataPromise])
            .then(function (results) {
                state.summaryData = results[0];
                state.allReferees = results[1] || [];
                state.filteredReferees = state.allReferees.slice();
                state.isLoading = false;
                applyFilters();
                renderPage();
            })
            .catch(function (error) {
                console.error('[RefereesPage] Load error:', error);
                state.isLoading = false;
                renderError(error.message);
            });
    }

    function applyFilters() {
        var term = state.searchTerm.toLowerCase().trim();
        state.filteredReferees = state.allReferees.filter(function (ref) {
            // Type filter
            if (state.activeFilter !== 'All' && ref.refType !== state.activeFilter) return false;
            // Search
            if (!term) return true;
            return (ref.refereeName || '').toLowerCase().indexOf(term) !== -1 ||
                   (ref.refType || '').toLowerCase().indexOf(term) !== -1;
        });

        var sortConfig = SORT_OPTIONS[state.currentSort] || SORT_OPTIONS.matches;
        state.filteredReferees.sort(function (a, b) {
            var aVal = a[sortConfig.field], bVal = b[sortConfig.field];
            if (typeof aVal === 'string') return sortConfig.desc ? bVal.localeCompare(aVal) : aVal.localeCompare(bVal);
            return sortConfig.desc ? (bVal || 0) - (aVal || 0) : (aVal || 0) - (bVal || 0);
        });
    }

    function renderPage() {
        if (!state.container) return;

        if (state.selectedReferee) { renderDetailView(); return; }

        // Summary
        var summaryHtml = state.summaryData ? renderLeagueSummary(state.summaryData) : '';

        // Comparison panel
        var compareHtml = state.compareMode ? renderComparisonPanel() : '';

        // Sort options
        var sortOptionsHtml = '';
        Object.keys(SORT_OPTIONS).forEach(function (key) {
            var sel = key === state.currentSort ? ' selected' : '';
            sortOptionsHtml += '<option value="' + key + '"' + sel + '>' + escapeHtml(SORT_OPTIONS[key].label) + '</option>';
        });

        // Filter buttons
        var filterTypes = ['All', 'Strict', 'Balanced', 'Lenient'];
        var filterBtnsHtml = '';
        filterTypes.forEach(function (type) {
            var active = type === state.activeFilter ? ' referees-page__filter-btn--active' : '';
            var count = '';
            if (type !== 'All') {
                var c = state.allReferees.filter(function (r) { return r.refType === type; }).length;
                count = ' <span class="referees-page__filter-count">' + c + '</span>';
            }
            filterBtnsHtml += '<button class="referees-page__filter-btn' + active + '" data-filter="' + type + '">' + type + count + '</button>';
        });

        // Cards
        var cardsHtml = '';
        state.filteredReferees.forEach(function (ref, idx) {
            cardsHtml += renderRefereeStatsCard(ref, idx);
        });

        if (state.filteredReferees.length === 0) {
            cardsHtml = '<div class="referees-page__empty" style="grid-column:1/-1;"><div class="referees-page__empty-icon">🔍</div><p>No referees found' + (state.searchTerm ? ' matching "' + escapeHtml(state.searchTerm) + '"' : ' for this filter') + '</p></div>';
        }

        state.container.innerHTML = '\
            <div class="referees-page">\
                ' + summaryHtml + '\
                <div class="referees-page__toolbar">\
                    <div class="referees-page__filters" id="refFilterBtns">' + filterBtnsHtml + '</div>\
                    <button class="referees-page__compare-toggle' + (state.compareMode ? ' referees-page__compare-toggle--active' : '') + '" id="refCompareToggle">🔄 Compare</button>\
                </div>\
                <div id="refCompareSection">' + compareHtml + '</div>\
                <div class="referees-page__controls">\
                    <div class="referees-page__search-wrapper">\
                        <span class="referees-page__search-icon">🔍</span>\
                        <input type="text" class="referees-page__search" id="refereeSearch" placeholder="Search referees..." value="' + escapeHtml(state.searchTerm) + '">\
                    </div>\
                    <select class="referees-page__sort-select" id="refereeSort">' + sortOptionsHtml + '</select>\
                </div>\
                <div class="referees-page__count">Showing ' + state.filteredReferees.length + ' of ' + state.allReferees.length + ' referees (min. 5 matches)</div>\
                <div class="referees-page__grid">' + cardsHtml + '</div>\
            </div>';

        attachPageListeners();
    }

    function renderDetailView() {
        if (!state.container || !state.selectedReferee) return;

        state.container.innerHTML = '\
            <div class="referees-page">\
                <div class="referees-page__header"><h2 class="referees-page__title">🏁 Referee Profile</h2></div>\
                ' + renderRefereeDetail(state.selectedReferee) + '\
            </div>';

        var backBtn = document.getElementById('refereeBackBtn');
        if (backBtn) {
            backBtn.addEventListener('click', function () {
                state.selectedReferee = null;
                renderPage();
            });
        }
    }

    function renderLoading() {
        if (!state.container) return;
        state.container.innerHTML = '<div class="referees-page referees-page--loading"><div class="referees-page__loader"><div class="referees-page__spinner"></div><div>Loading referee statistics...</div></div></div>';
    }

    function renderError(message) {
        if (!state.container) return;
        state.container.innerHTML = '<div class="referees-page referees-page--error"><div class="referees-page__error-icon">⚠️</div><p class="referees-page__error-text">' + escapeHtml(message || 'Failed to load referees') + '</p><button class="referees-page__retry-btn" id="refereeRetryBtn">Retry</button></div>';
        var btn = document.getElementById('refereeRetryBtn');
        if (btn) btn.addEventListener('click', function () { loadAllReferees(); });
    }

    function attachPageListeners() {
        // Search
        var searchInput = document.getElementById('refereeSearch');
        if (searchInput) {
            var debounce = null;
            searchInput.addEventListener('input', function (e) {
                clearTimeout(debounce);
                debounce = setTimeout(function () {
                    state.searchTerm = e.target.value;
                    applyFilters();
                    renderPage();
                    var ni = document.getElementById('refereeSearch');
                    if (ni) { ni.focus(); ni.setSelectionRange(ni.value.length, ni.value.length); }
                }, 300);
            });
        }

        // Sort
        var sortSel = document.getElementById('refereeSort');
        if (sortSel) {
            sortSel.addEventListener('change', function (e) {
                state.currentSort = e.target.value;
                applyFilters();
                renderPage();
            });
        }

        // Filter buttons
        var filterBtns = document.querySelectorAll('.referees-page__filter-btn');
        filterBtns.forEach(function (btn) {
            btn.addEventListener('click', function () {
                state.activeFilter = btn.getAttribute('data-filter') || 'All';
                applyFilters();
                renderPage();
            });
        });

        // Compare toggle
        var compToggle = document.getElementById('refCompareToggle');
        if (compToggle) {
            compToggle.addEventListener('click', function () {
                state.compareMode = !state.compareMode;
                renderPage();
            });
        }

        // Comparison panel listeners
        if (state.compareMode) attachComparisonListeners();

        // Card clicks -> detail
        var cards = state.container.querySelectorAll('.referee-stats-card');
        cards.forEach(function (card) {
            card.addEventListener('click', function () {
                var name = card.getAttribute('data-referee');
                if (name) {
                    var found = state.allReferees.find(function (r) { return r.refereeName === name; });
                    if (found) { state.selectedReferee = found; renderDetailView(); }
                }
            });
        });
    }

    // ══════════════════════════════════════════════════════════════════
    // EXPORT TO GLOBAL SCOPE
    // ══════════════════════════════════════════════════════════════════

    window.RefereesPage = {
        init: initPage
    };

    window.RefereeStatsCard = {
        render: function (container, data) {
            if (container && data) container.innerHTML = renderRefereeStatsCard(data, 0);
        }
    };

    window.RefereeImpactWidget = {
        render: function (container, data) {
            if (container && data) container.innerHTML = renderImpactWidget(data);
        },
        fetchAndRender: fetchImpactAndRender,
        renderLoading: renderImpactLoading,
        renderError: renderImpactError
    };

    console.log('[RefereesPage] Module v2 initialized');

})();

